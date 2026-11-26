import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { CheckInWebSocketService, RealtimeChannelState } from '../../services/checkin-websocket.service';
import { CheckInLiveEvent, QrCheckInService, QrSessionResponse } from '../../services/qr-checkin.service';

@Component({
  selector: 'app-admin-qr-checkin',
  templateUrl: './admin-qr-checkin.component.html',
  styleUrls: ['./admin-qr-checkin.component.css']
})
export class AdminQrCheckinComponent implements OnInit, OnDestroy {
  @Input() availableMembers: { id: number; name: string; role: string; email: string }[] = [];
  @Output() close = new EventEmitter<void>();
  @Output() checkInCompleted = new EventEmitter<void>();

  session: QrSessionResponse | null = null;
  liveEvent: CheckInLiveEvent | null = null;
  channelState: RealtimeChannelState = 'DISCONNECTED';

  loading = true;
  errorMessage = '';
  remainingSeconds = 0;

  // Manual fallback state
  showManualFallback = false;
  manualMemberId: number | null = null;
  manualReason = '';
  manualSubmitting = false;
  manualSuccess = '';

  private countdownTimer: any = null;
  private serverOffsetMs = 0;
  private destroy$ = new Subject<void>();

  constructor(
    private qrCheckInService: QrCheckInService,
    private wsService: CheckInWebSocketService
  ) {}

  ngOnInit(): void {
    this.wsService.events$
      .pipe(takeUntil(this.destroy$))
      .subscribe((evt) => {
        if (this.session && evt.sessionCode === this.session.sessionCode) {
          this.liveEvent = evt;
          if (evt.status === 'COMPLETED' || evt.status === 'CONSUMED') {
            this.stopTimer();
            this.checkInCompleted.emit();
          } else if (evt.status === 'EXPIRED' || evt.status === 'CANCELLED') {
            this.stopTimer();
          }
        }
      });

    this.wsService.connectionState$
      .pipe(takeUntil(this.destroy$))
      .subscribe((state) => {
        this.channelState = state;
      });

    this.startNewSession();
  }

  ngOnDestroy(): void {
    this.stopTimer();
    this.wsService.disconnect();
    this.destroy$.next();
    this.destroy$.complete();
  }

  get memberOptions(): { id: number; name: string; role: string; email: string }[] {
    return (this.availableMembers || []).filter(u => u.role === 'MEMBER');
  }

  get currentStatus(): string {
    if (this.liveEvent?.status) {
      return this.liveEvent.status;
    }
    if (this.remainingSeconds <= 0 && this.session) {
      return 'EXPIRED';
    }
    return this.session?.status || 'PENDING';
  }

  startNewSession(): void {
    this.loading = true;
    this.errorMessage = '';
    this.liveEvent = null;
    this.manualSuccess = '';
    this.stopTimer();
    this.wsService.disconnect();

    this.qrCheckInService.createSession()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (res) => {
          this.session = res;
          this.loading = false;
          const serverMs = new Date(res.serverTime).getTime();
          this.serverOffsetMs = isNaN(serverMs) ? 0 : serverMs - Date.now();
          this.startCountdown(res.expiresAt);
          this.wsService.watchSession(res.sessionCode);
        },
        error: (err) => {
          this.loading = false;
          this.errorMessage = err?.error?.message || 'Unable to start QR biometric check-in session.';
        }
      });
  }

  cancelCurrentSession(): void {
    if (!this.session) {
      this.close.emit();
      return;
    }
    const code = this.session.sessionCode;
    this.stopTimer();
    this.wsService.disconnect();
    this.qrCheckInService.cancelSession(code)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => this.close.emit(),
        error: () => this.close.emit()
      });
  }

  private startCountdown(expiresAtIso: string): void {
    const expiresMs = new Date(expiresAtIso).getTime();
    const tick = () => {
      const alignedNow = Date.now() + this.serverOffsetMs;
      const diffSec = Math.max(0, Math.ceil((expiresMs - alignedNow) / 1000));
      this.remainingSeconds = diffSec;
      if (diffSec <= 0) {
        this.stopTimer();
        if (this.currentStatus !== 'COMPLETED' && this.currentStatus !== 'CANCELLED') {
          this.liveEvent = {
            ...(this.liveEvent || {
              sessionCode: this.session?.sessionCode || '',
              eventType: 'SESSION_EXPIRED',
              timestamp: new Date().toISOString()
            }),
            status: 'EXPIRED'
          };
        }
        this.wsService.disconnect();
      }
    };
    tick();
    this.countdownTimer = setInterval(tick, 1000);
  }

  private stopTimer(): void {
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    }
  }

  submitManualFallback(): void {
    if (!this.manualMemberId || this.manualReason.trim().length < 4) {
      this.errorMessage = 'Select a member and enter a reason (min 4 characters) for admin-assisted check-in.';
      return;
    }
    this.manualSubmitting = true;
    this.errorMessage = '';

    this.qrCheckInService.adminManualCheckIn(
      this.session?.sessionCode || null,
      this.manualMemberId,
      this.manualReason.trim()
    )
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (res) => {
          this.manualSubmitting = false;
          this.manualSuccess = `Admin-assisted check-in recorded for ${res.memberName} (${res.checkInTime}).`;
          this.liveEvent = {
            sessionCode: this.session?.sessionCode || '',
            status: 'COMPLETED',
            eventType: 'CHECKIN_COMPLETED',
            memberId: res.memberId,
            memberName: res.memberName,
            memberInitials: res.memberName.slice(0, 2).toUpperCase(),
            planName: res.planName,
            membershipStatus: res.membershipStatus,
            expiryDate: res.expiryDate,
            checkInTime: res.checkInTime,
            checkInTimestamp: res.checkInTimestamp,
            checkInMethod: 'ADMIN_MANUAL',
            verificationResult: 'VERIFIED_ADMIN_MANUAL',
            riskResult: res.riskResult,
            timestamp: new Date().toISOString()
          };
          this.stopTimer();
          this.wsService.disconnect();
          this.checkInCompleted.emit();
        },
        error: (err) => {
          this.manualSubmitting = false;
          this.errorMessage = err?.error?.message || 'Failed to record manual fallback check-in.';
        }
      });
  }

  onCloseBackdrop(): void {
    this.close.emit();
  }
}
