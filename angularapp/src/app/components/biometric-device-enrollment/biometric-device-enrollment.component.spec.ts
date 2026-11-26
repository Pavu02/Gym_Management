import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { BiometricDeviceEnrollmentComponent } from './biometric-device-enrollment.component';
import { WebAuthnService, RegisteredDeviceDTO } from '../../services/webauthn.service';

describe('BiometricDeviceEnrollmentComponent', () => {
  let fixture: ComponentFixture<BiometricDeviceEnrollmentComponent>;
  let component: BiometricDeviceEnrollmentComponent;
  let webAuthnSpy: jasmine.SpyObj<WebAuthnService>;

  beforeEach(async () => {
    webAuthnSpy = jasmine.createSpyObj<WebAuthnService>('WebAuthnService', [
      'isSupported',
      'isPlatformAuthenticatorAvailable',
      'detectDevice',
      'enrollCurrentDevice'
    ]);
    webAuthnSpy.detectDevice.and.returnValue({ deviceName: 'My Phone', deviceType: 'ANDROID_PHONE' });
    webAuthnSpy.isSupported.and.returnValue(true);
    webAuthnSpy.isPlatformAuthenticatorAvailable.and.resolveTo(true);

    await TestBed.configureTestingModule({
      imports: [FormsModule],
      declarations: [BiometricDeviceEnrollmentComponent],
      providers: [{ provide: WebAuthnService, useValue: webAuthnSpy }]
    }).compileComponents();

    fixture = TestBed.createComponent(BiometricDeviceEnrollmentComponent);
    component = fixture.componentInstance;
  });

  it('detects unsupported WebAuthn devices and disables enrollment button', async () => {
    webAuthnSpy.isSupported.and.returnValue(false);
    webAuthnSpy.isPlatformAuthenticatorAvailable.and.resolveTo(false);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component.webAuthnSupported).toBeFalse();
    expect(component.platformAvailable).toBeFalse();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('WebAuthn Unsupported');
    const btn = compiled.querySelector('.btn-enable-fp') as HTMLButtonElement;
    expect(btn.disabled).toBeTrue();
  });

  it('handles registration cancellation without showing success message', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    component.consent = true;
    webAuthnSpy.enrollCurrentDevice.and.rejectWith(new Error('Biometric setup was cancelled, timed out, or this device has no screen lock / fingerprint enabled.'));

    await component.enableFingerprintCheckIn();
    fixture.detectChanges();

    expect(component.successMessage).toBe('');
    expect(component.errorMessage).toContain('cancelled');
  });

  it('displays Biometric device registered successfully after backend verification succeeds', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    component.consent = true;
    const savedDevice: RegisteredDeviceDTO = {
      id: 11,
      deviceName: 'Pixel 9 Pro',
      deviceType: 'ANDROID_PHONE',
      registeredAt: new Date().toISOString(),
      active: true
    };
    webAuthnSpy.enrollCurrentDevice.and.resolveTo(savedDevice);

    await component.enableFingerprintCheckIn();
    fixture.detectChanges();

    expect(component.successMessage).toBe('Biometric device registered successfully');
  });
});
