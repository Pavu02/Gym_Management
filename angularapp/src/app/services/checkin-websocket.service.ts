import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, Subject, Subscription, interval } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';
import { CheckInLiveEvent, QrCheckInService } from './qr-checkin.service';

export type RealtimeChannelState = 'DISCONNECTED' | 'CONNECTING' | 'WEBSOCKET_LIVE' | 'POLLING_FALLBACK';

@Injectable({
  providedIn: 'root'
})
export class CheckInWebSocketService implements OnDestroy {
  private ws: WebSocket | null = null;
  private pollSub: Subscription | null = null;
  private reconnectTimer: any = null;
  private activeSessionCode: string | null = null;
  private subscriptionSeq = 0;
  private closedExplicitly = false;
  private wsReceivedMessage = false;

  private readonly eventsSubject = new Subject<CheckInLiveEvent>();
  private readonly stateSubject = new BehaviorSubject<RealtimeChannelState>('DISCONNECTED');

  readonly events$: Observable<CheckInLiveEvent> = this.eventsSubject.asObservable();
  readonly connectionState$: Observable<RealtimeChannelState> = this.stateSubject.asObservable();

  constructor(
    private authService: AuthService,
    private qrCheckInService: QrCheckInService
  ) {}

  watchSession(sessionCode: string): void {
    // Always tear down previous WebSocket + polling before watching a new session
    this.disconnect();
    if (!sessionCode) {
      return;
    }
    this.activeSessionCode = sessionCode;
    this.closedExplicitly = false;
    this.wsReceivedMessage = false;
    this.connectStomp(sessionCode);
  }

  private connectStomp(sessionCode: string): void {
    if (this.closedExplicitly || this.activeSessionCode !== sessionCode) {
      return;
    }

    const token = this.authService.getToken();
    const canUseWs = typeof WebSocket !== 'undefined' && !!token && !!environment.wsUrl;

    if (!canUseWs) {
      this.startPollingFallback(sessionCode);
      return;
    }

    this.stateSubject.next('CONNECTING');
    let socket: WebSocket;
    try {
      socket = new WebSocket(environment.wsUrl);
    } catch {
      this.startPollingFallback(sessionCode);
      return;
    }
    this.ws = socket;

    // Fallback automatically to 2s polling if STOMP CONNECTED isn't achieved quickly
    const fallbackTimeout = setTimeout(() => {
      if (this.stateSubject.value === 'CONNECTING' && this.activeSessionCode === sessionCode) {
        this.startPollingFallback(sessionCode);
      }
    }, 3500);

    socket.onopen = () => {
      if (this.closedExplicitly || this.activeSessionCode !== sessionCode) {
        socket.close();
        return;
      }
      const connectFrame = [
        'CONNECT',
        'accept-version:1.2,1.1',
        'heart-beat:10000,10000',
        `Authorization:Bearer ${token}`,
        '',
        '\0'
      ].join('\n');
      socket.send(connectFrame);
    };

    socket.onmessage = (msg) => {
      if (typeof msg.data !== 'string') {
        return;
      }
      const frames = msg.data.split('\0');
      for (const rawFrame of frames) {
        const trimmed = rawFrame.trim();
        if (!trimmed) continue;
        const firstNewline = trimmed.indexOf('\n');
        const command = (firstNewline >= 0 ? trimmed.slice(0, firstNewline) : trimmed).trim();

        if (command === 'CONNECTED') {
          clearTimeout(fallbackTimeout);
          this.stopPollingOnly();
          this.stateSubject.next('WEBSOCKET_LIVE');
          const subId = `sub-qr-${++this.subscriptionSeq}`;
          const subscribeFrame = [
            'SUBSCRIBE',
            `id:${subId}`,
            `destination:/topic/checkin-sessions/${sessionCode}`,
            'ack:auto',
            '',
            '\0'
          ].join('\n');
          socket.send(subscribeFrame);
        } else if (command === 'MESSAGE') {
          const splitIdx = rawFrame.indexOf('\n\n');
          const altIdx = rawFrame.indexOf('\r\n\r\n');
          let bodyStr = '';
          if (splitIdx >= 0) {
            bodyStr = rawFrame.slice(splitIdx + 2);
          } else if (altIdx >= 0) {
            bodyStr = rawFrame.slice(altIdx + 4);
          }
          if (bodyStr) {
            try {
              const event = JSON.parse(bodyStr) as CheckInLiveEvent;
              this.wsReceivedMessage = true;
              this.handleIncomingEvent(event);
            } catch {
              // Ignore malformed STOMP payload
            }
          }
        }
      }
    };

    socket.onerror = () => {
      clearTimeout(fallbackTimeout);
      if (!this.closedExplicitly && this.activeSessionCode === sessionCode) {
        this.startPollingFallback(sessionCode);
      }
    };

    socket.onclose = () => {
      clearTimeout(fallbackTimeout);
      if (!this.closedExplicitly && this.activeSessionCode === sessionCode) {
        this.startPollingFallback(sessionCode);
      }
    };
  }

  private startPollingFallback(sessionCode: string): void {
    if (this.closedExplicitly || this.pollSub || this.activeSessionCode !== sessionCode) {
      return;
    }
    this.stateSubject.next('POLLING_FALLBACK');
    this.pollSub = interval(2000)
      .pipe(
        switchMap(() => this.qrCheckInService.getSessionStatus(sessionCode).pipe(
          catchError(() => [])
        ))
      )
      .subscribe((event) => {
        if (event && event.sessionCode === sessionCode) {
          this.handleIncomingEvent(event);
        }
      });

    // Also attempt one reconnect after 6s if no terminal state arrived
    if (!this.reconnectTimer && !this.wsReceivedMessage) {
      this.reconnectTimer = setTimeout(() => {
        this.reconnectTimer = null;
        if (!this.closedExplicitly && this.activeSessionCode === sessionCode && (!this.ws || this.ws.readyState !== WebSocket.OPEN)) {
          this.connectStomp(sessionCode);
        }
      }, 6000);
    }
  }

  private handleIncomingEvent(event: CheckInLiveEvent): void {
    this.eventsSubject.next(event);
    const terminalStatuses = ['COMPLETED', 'CONSUMED', 'EXPIRED', 'CANCELLED'];
    if (event.status && terminalStatuses.includes(event.status.toUpperCase())) {
      this.disconnect();
    }
  }

  private stopPollingOnly(): void {
    if (this.pollSub) {
      this.pollSub.unsubscribe();
      this.pollSub = null;
    }
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  disconnect(): void {
    this.closedExplicitly = true;
    this.activeSessionCode = null;
    this.stopPollingOnly();

    if (this.ws) {
      try {
        if (this.ws.readyState === WebSocket.OPEN) {
          this.ws.send('DISCONNECT\n\n\0');
        }
        this.ws.close();
      } catch {
        // Ignore close error
      }
      this.ws = null;
    }
    this.stateSubject.next('DISCONNECTED');
  }

  ngOnDestroy(): void {
    this.disconnect();
    this.eventsSubject.complete();
    this.stateSubject.complete();
  }
}
