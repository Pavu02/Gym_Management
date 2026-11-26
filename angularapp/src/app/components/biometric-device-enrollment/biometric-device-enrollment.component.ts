import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { WebAuthnService, RegisteredDeviceDTO } from '../../services/webauthn.service';

@Component({
  selector: 'app-biometric-device-enrollment',
  templateUrl: './biometric-device-enrollment.component.html',
  styleUrls: ['./biometric-device-enrollment.component.css']
})
export class BiometricDeviceEnrollmentComponent implements OnInit {
  @Input() activeDeviceCount = 0;
  @Input() maxDevices = 5;
  @Output() enrolled = new EventEmitter<RegisteredDeviceDTO>();

  webAuthnSupported = false;
  platformAvailable = false;
  checkingCapability = true;

  deviceName = '';
  consent = false;
  currentPassword = '';
  requireReauth = false;

  enrolling = false;
  errorMessage = '';
  successMessage = '';

  constructor(private webAuthnService: WebAuthnService) {}

  async ngOnInit(): Promise<void> {
    const detected = this.webAuthnService.detectDevice();
    this.deviceName = detected.deviceName;
    this.webAuthnSupported = this.webAuthnService.isSupported();
    this.platformAvailable = await this.webAuthnService.isPlatformAuthenticatorAvailable();
    this.checkingCapability = false;
  }

  get limitReached(): boolean {
    return this.activeDeviceCount >= this.maxDevices;
  }

  async enableFingerprintCheckIn(): Promise<void> {
    this.errorMessage = '';
    this.successMessage = '';

    if (!this.webAuthnSupported) {
      this.errorMessage = 'WebAuthn passkeys are not supported in this browser. Use Android Chrome or iPhone Safari over HTTPS.';
      return;
    }
    if (!this.consent) {
      this.errorMessage = 'Please confirm biometric check-in consent to continue.';
      return;
    }
    if (!this.deviceName.trim()) {
      this.errorMessage = 'Please enter a device name (e.g. Primary Phone or Backup Phone).';
      return;
    }

    this.enrolling = true;
    try {
      const saved = await this.webAuthnService.enrollCurrentDevice(
        this.deviceName.trim(),
        this.consent,
        this.currentPassword.trim() || undefined
      );
      this.successMessage = 'Biometric device registered successfully';
      this.currentPassword = '';
      this.enrolled.emit(saved);
    } catch (err: any) {
      const backendMsg = err?.error?.message || err?.message || '';
      if (backendMsg.toLowerCase().includes('already registered')) {
        this.errorMessage = 'This device credential is already registered to your account.';
      } else {
        this.errorMessage = backendMsg || 'Failed to register this check-in device.';
      }
    } finally {
      this.enrolling = false;
    }
  }
}
