import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { CheckInWebSocketService } from './checkin-websocket.service';
import { AuthService } from './auth.service';
import { QrCheckInService, CheckInLiveEvent } from './qr-checkin.service';

describe('CheckInWebSocketService', () => {
  let service: CheckInWebSocketService;
  let authSpy: jasmine.SpyObj<AuthService>;
  let qrSpy: jasmine.SpyObj<QrCheckInService>;

  beforeEach(() => {
    authSpy = jasmine.createSpyObj<AuthService>('AuthService', ['getToken']);
    qrSpy = jasmine.createSpyObj<QrCheckInService>('QrCheckInService', ['getSessionStatus']);
    authSpy.getToken.and.returnValue(null); // forces immediate HTTP 2s polling fallback path

    TestBed.configureTestingModule({
      providers: [
        CheckInWebSocketService,
        { provide: AuthService, useValue: authSpy },
        { provide: QrCheckInService, useValue: qrSpy }
      ]
    });
    service = TestBed.inject(CheckInWebSocketService);
  });

  it('falls back to 2-second polling and stops automatically after COMPLETED event', fakeAsync(() => {
    const liveEvents: CheckInLiveEvent[] = [];
    const sub = service.events$.subscribe(e => liveEvents.push(e));

    qrSpy.getSessionStatus.and.returnValues(
      of({
        sessionCode: 'sess-1',
        status: 'PHONE_CONNECTED',
        eventType: 'PHONE_CONNECTED',
        timestamp: new Date().toISOString()
      }),
      of({
        sessionCode: 'sess-1',
        status: 'COMPLETED',
        eventType: 'CHECKIN_COMPLETED',
        memberName: 'Athlete One',
        timestamp: new Date().toISOString()
      })
    );

    service.watchSession('sess-1');
    tick(2000);
    expect(qrSpy.getSessionStatus).toHaveBeenCalledTimes(1);
    expect(liveEvents[0].status).toBe('PHONE_CONNECTED');

    tick(2000);
    expect(qrSpy.getSessionStatus).toHaveBeenCalledTimes(2);
    expect(liveEvents[1].status).toBe('COMPLETED');

    // Further ticks must not poll after terminal completion
    tick(6000);
    expect(qrSpy.getSessionStatus).toHaveBeenCalledTimes(2);
    sub.unsubscribe();
  }));

  it('prevents repeated poll subscriptions when watchSession is called again', fakeAsync(() => {
    qrSpy.getSessionStatus.and.returnValue(
      of({
        sessionCode: 'sess-2',
        status: 'PENDING',
        eventType: 'PENDING',
        timestamp: new Date().toISOString()
      })
    );

    service.watchSession('sess-1');
    service.watchSession('sess-2');
    tick(2000);

    expect(qrSpy.getSessionStatus).toHaveBeenCalledTimes(1);
    expect(qrSpy.getSessionStatus).toHaveBeenCalledWith('sess-2');
    service.disconnect();
  }));
});
