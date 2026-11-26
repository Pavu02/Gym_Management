import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { AdminQrCheckinComponent } from './admin-qr-checkin.component';
import { QrCheckInService, QrSessionResponse } from '../../services/qr-checkin.service';
import { CheckInWebSocketService, RealtimeChannelState } from '../../services/checkin-websocket.service';

describe('AdminQrCheckinComponent', () => {
  let fixture: ComponentFixture<AdminQrCheckinComponent>;
  let component: AdminQrCheckinComponent;
  let qrSpy: jasmine.SpyObj<QrCheckInService>;
  let eventsSub: Subject<any>;
  let stateSub: BehaviorSubject<RealtimeChannelState>;

  beforeEach(async () => {
    eventsSub = new Subject<any>();
    stateSub = new BehaviorSubject<RealtimeChannelState>('DISCONNECTED');

    qrSpy = jasmine.createSpyObj<QrCheckInService>('QrCheckInService', [
      'createSession',
      'cancelSession',
      'adminManualCheckIn'
    ]);

    const wsMock = {
      events$: eventsSub.asObservable(),
      connectionState$: stateSub.asObservable(),
      watchSession: jasmine.createSpy('watchSession'),
      disconnect: jasmine.createSpy('disconnect')
    };

    await TestBed.configureTestingModule({
      imports: [FormsModule],
      declarations: [AdminQrCheckinComponent],
      providers: [
        { provide: QrCheckInService, useValue: qrSpy },
        { provide: CheckInWebSocketService, useValue: wsMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AdminQrCheckinComponent);
    component = fixture.componentInstance;
  });

  it('counts down the 60s QR expiry timer and transitions to EXPIRED', fakeAsync(() => {
    const now = Date.now();
    const sessionRes: QrSessionResponse = {
      sessionCode: 's-timer-1',
      token: 'opaque-token-123456789012345678',
      qrUrl: 'https://example.com/check-in/verify?token=opaque',
      qrCodeDataUrl: 'data:image/png;base64,iVBORw0KGgo=',
      gymId: 'BEASTX-01',
      branchId: 'MAIN',
      status: 'PENDING',
      createdAt: new Date(now).toISOString(),
      expiresAt: new Date(now + 3000).toISOString(),
      serverTime: new Date(now).toISOString(),
      expiresInSeconds: 3
    };
    qrSpy.createSession.and.returnValue(of(sessionRes));

    fixture.detectChanges();
    expect(component.remainingSeconds).toBe(3);
    expect(component.currentStatus).toBe('PENDING');

    tick(3000);
    expect(component.remainingSeconds).toBe(0);
    expect(component.currentStatus).toBe('EXPIRED');
  }));
});
