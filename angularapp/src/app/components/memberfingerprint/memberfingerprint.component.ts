import { Component, OnInit } from '@angular/core';
import { FingerprintService, FingerprintDeviceDTO } from '../../services/fingerprint.service';
import { isFingerprintSupported, prepareCreationOptions, publicKeyCredentialToJson } from '../../utils/webauthn.util';

@Component({
  selector: 'app-memberfingerprint',
  templateUrl: './memberfingerprint.component.html',
  styleUrls: ['./memberfingerprint.component.css']
})
export class MemberfingerprintComponent implements OnInit {
  isSupported: boolean = false;
  checkingSupport: boolean = true;

  agreeConsent: boolean = false;
  deviceName: string = 'My Phone';
  registering: boolean = false;
  errorMessage: string = '';
  successMessage: string = '';

  devices: FingerprintDeviceDTO[] = [];
  loadingDevices: boolean = true;

  currentUrl: string = '';

  constructor(private fingerprintService: FingerprintService) {}

  async ngOnInit(): Promise<void> {
    if (typeof window !== 'undefined') {
      this.currentUrl = window.location.href;
    }
    this.detectDeviceName();
    await this.checkSupport();
    this.loadDevices();
  }

  detectDeviceName(): void {
    if (typeof navigator !== 'undefined') {
      const ua = navigator.userAgent;
      if (/iPhone/i.test(ua)) {
        this.deviceName = 'My iPhone';
      } else if (/iPad/i.test(ua)) {
        this.deviceName = 'My iPad';
      } else if (/Android/i.test(ua)) {
        this.deviceName = 'My Android Phone';
      } else if (/Macintosh/i.test(ua)) {
        this.deviceName = 'My Mac (Touch ID)';
      } else if (/Windows/i.test(ua)) {
        this.deviceName = 'My Windows PC';
      }
    }
  }

  async checkSupport(): Promise<void> {
    this.checkingSupport = true;
    try {
      this.isSupported = await isFingerprintSupported();
    } catch {
      this.isSupported = false;
    } finally {
      this.checkingSupport = false;
    }
  }

  loadDevices(): void {
    this.loadingDevices = true;
    this.fingerprintService.getMyDevices().subscribe({
      next: (devices) => {
        this.devices = devices || [];
        this.loadingDevices = false;
      },
      error: () => {
        this.loadingDevices = false;
      }
    });
  }

  async registerFingerprint(): Promise<void> {
    this.errorMessage = '';
    this.successMessage = '';

    if (!this.agreeConsent) {
      this.errorMessage = 'Please check the consent box to proceed with fingerprint setup.';
      return;
    }

    if (!this.deviceName || !this.deviceName.trim()) {
      this.errorMessage = 'Please enter a name for this device (e.g. My Phone).';
      return;
    }

    this.registering = true;

    this.fingerprintService.startRegistration().subscribe({
      next: async (res) => {
        try {
          const creationOptions = prepareCreationOptions(res.publicKeyCredentialCreationOptions);
          const credential = (await navigator.credentials.create(creationOptions)) as PublicKeyCredential;

          if (!credential) {
            throw new Error('Biometric registration was cancelled.');
          }

          const credJson = publicKeyCredentialToJson(credential);

          this.fingerprintService.finishRegistration(
            res.requestId,
            JSON.stringify(credJson),
            this.deviceName.trim()
          ).subscribe({
            next: (dev) => {
              this.registering = false;
              this.successMessage = `Fingerprint registered successfully on "${dev.deviceName}"! You can now use your phone fingerprint at the gym kiosk.`;
              this.loadDevices();
            },
            error: (err) => {
              this.registering = false;
              this.errorMessage = err.error?.message || 'Server failed to verify fingerprint passkey.';
            }
          });
        } catch (err: any) {
          this.registering = false;
          const msg = err.error?.message || err.message || '';
          if (err.name === 'NotAllowedError' || msg.includes('timed out or was not allowed') || msg.includes('privacy-considerations')) {
            this.errorMessage = 'Biometric verification was cancelled or is not available on this device. If you are on a computer without a biometric sensor, please open this page on your smartphone.';
          } else {
            this.errorMessage = msg || 'Failed to complete biometric setup on this device.';
          }
        }
      },
      error: (err) => {
        this.registering = false;
        this.errorMessage = err.error?.message || 'Could not initiate fingerprint registration.';
      }
    });
  }

  deleteDevice(id: number, name: string): void {
    if (!confirm(`Are you sure you want to remove "${name}" from your fingerprint check-in devices?`)) {
      return;
    }

    this.fingerprintService.deleteMyDevice(id).subscribe({
      next: () => {
        this.successMessage = `Device "${name}" removed.`;
        this.loadDevices();
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to remove device.';
      }
    });
  }
}
