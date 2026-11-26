import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { MobileCheckinVerificationComponent } from './mobile-checkin-verification.component';
import { AuthService } from '../../services/auth.service';
import { QrCheckInService } from '../../services/qr-checkin.service';
import { WebAuthnService } from '../../services/webauthn.service';

describe('MobileCheckinVerificationComponent', () => {
  let fixture: ComponentFixture<MobileCheckinVerificationComponent>;
  let component: MobileCheckinVerificationComponent;
  let qrSpy: jasmine.SpyObj<QrCheckInService>;
  let webAuthnSpy: jasmine.SpyObj<WebAuthnService>;
  let authSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const flushMicrotasks = () => new Promise(resolve => setTimeout(resolve, 20));

  beforeEach(async () => {
    authSpy = jasmine.createSpyObj<AuthService>('AuthService', ['isLoggedIn', 'getRole']);
    authSpy.isLoggedIn.and.returnValue(true);
    authSpy.getRole.and.returnValue('MEMBER');

    qrSpy = jasmine.createSpyObj<QrCheckInService>('QrCheckInService', [
      'tryGetCurrentPosition',
      'validateSession',
      'confirmByIdempotencyKey'
    ]);
    qrSpy.tryGetCurrentPosition.and.resolveTo(null);
    qrSpy.validateSession.and.returnValue(of({
      valid: true,
      sessionCode: 's-1',
      gymId: 'BEASTX-01',
      branchId: 'MAIN',
      status: 'PHONE_CONNECTED',
      expiresAt: new Date(Date.now() + 60000).toISOString(),
      serverTime: new Date().toISOString(),
      remainingSeconds: 60,
      memberId: 5,
      memberName: 'Athlete One',
      planName: 'Gold',
      membershipStatus: 'ACTIVE',
      hasRegisteredDevices: true,
      deviceCount: 1,
      locationPolicy: 'OFF',
      riskResult: 'STANDARD',
      message: 'Ready'
    }));

    webAuthnSpy = jasmine.createSpyObj<WebAuthnService>('WebAuthnService', [
      'isSupported',
      'isPlatformAuthenticatorAvailable',
      'getAuthenticationOptions',
      'prepareAssertionOptions',
      'serializeAssertion',
      'verifyAuthentication'
    ]);
    webAuthnSpy.isSupported.and.returnValue(true);
    webAuthnSpy.isPlatformAuthenticatorAvailable.and.resolveTo(true);
    webAuthnSpy.getAuthenticationOptions.and.returnValue(of({
      challengeId: 'ch-1',
      sessionCode: 's-1',
      publicKeyCredentialRequestOptions: '{"publicKey":{"challenge":"AQID"}}',
      expiresInSeconds: 120,
      allowedDeviceCount: 1
    }));
    webAuthnSpy.prepareAssertionOptions.and.returnValue({ publicKey: { challenge: new Uint8Array([1, 2, 3]).buffer } });

    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);

    await TestBed.configureTestingModule({
      declarations: [MobileCheckinVerificationComponent],
      providers: [
        { provide: AuthService, useValue: authSpy },
        { provide: QrCheckInService, useValue: qrSpy },
        { provide: WebAuthnService, useValue: webAuthnSpy },
        { provide: Router, useValue: routerSpy },
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: of(convertToParamMap({ token: 'abcdefghijklmnopqrstuvwxyz123456' })) }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MobileCheckinVerificationComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    try { sessionStorage.clear(); } catch {}
    component.ngOnDestroy();
  });

  it('handles authentication cancellation and supports retry with fresh challenge', async () => {
    const getSpy = spyOn(navigator.credentials, 'get')
      .and.rejectWith(Object.assign(new Error('Cancelled'), { name: 'NotAllowedError' }));

    await component.ngOnInit();
    await flushMicrotasks();
    expect(component.stage).toBe('READY_TO_VERIFY');

    await component.verifyAndCheckIn();
    expect(component.stage).toBe('ERROR');
    expect(component.errorMessage).toContain('Fingerprint verification was cancelled');
    expect(component.canRetryVerification).toBeTrue();

    // Retry re-validates QR and pre-fetches a fresh challenge
    await component.validateQrAndPrefetchChallenge();
    await flushMicrotasks();
    expect(component.stage).toBe('READY_TO_VERIFY');
    expect(webAuthnSpy.getAuthenticationOptions).toHaveBeenCalledTimes(2);

    getSpy.and.resolveTo({ id: 'c1', rawId: new Uint8Array([1]).buffer, type: 'public-key', response: {} } as any);
    webAuthnSpy.serializeAssertion.and.returnValue('{}');
    webAuthnSpy.verifyAuthentication.and.returnValue(of({
      status: 'CHECKED_IN',
      message: 'Check-in completed successfully.',
      userVerificationMethod: 'DEVICE_BIOMETRIC',
      memberId: 5,
      memberName: 'Athlete One',
      planName: 'Gold',
      membershipStatus: 'ACTIVE',
      checkInTime: '01:30 PM',
      checkInTimestamp: new Date().toISOString(),
      checkInMethod: 'QR + DEVICE_BIOMETRIC'
    }));
    await component.verifyAndCheckIn();
    expect(component.stage).toBe('SUCCESS');
  });

  it('handles offline / status 0 error via idempotency confirmation recovery', async () => {
    const fakeCred: any = { id: 'c1', rawId: new Uint8Array([1]).buffer, type: 'public-key', response: {} };
    spyOn(navigator.credentials, 'get').and.resolveTo(fakeCred);
    webAuthnSpy.serializeAssertion.and.returnValue('{}');
    webAuthnSpy.verifyAuthentication.and.returnValue(throwError(() => ({ status: 0 })));
    qrSpy.confirmByIdempotencyKey.and.returnValue(of({
      status: 'CHECKED_IN',
      message: 'Check-in completed successfully.',
      userVerificationMethod: 'DEVICE_BIOMETRIC',
      memberId: 5,
      memberName: 'Athlete One',
      planName: 'Gold',
      membershipStatus: 'ACTIVE',
      checkInTime: '01:30 PM',
      checkInTimestamp: new Date().toISOString(),
      checkInMethod: 'QR + DEVICE_BIOMETRIC'
    }));

    await component.ngOnInit();
    await flushMicrotasks();
    expect(component.stage).toBe('READY_TO_VERIFY');

    await component.verifyAndCheckIn();
    expect(component.stage).toBe('CONFIRMING_OFFLINE_RECOVERY');

    await component.tryRecoverStatus(false);
    expect(component.stage).toBe('SUCCESS');
    expect(component.result?.message).toBe('Check-in completed successfully.');
  });
});
