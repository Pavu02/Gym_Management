import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FingerprintService } from '../../services/fingerprint.service';
import { isFingerprintSupported, prepareCreationOptions, publicKeyCredentialToJson } from '../../utils/webauthn.util';

@Component({
  selector: 'app-fingerprintenroll',
  templateUrl: './fingerprintenroll.component.html',
  styleUrls: ['./fingerprintenroll.component.css']
})
export class FingerprintenrollComponent implements OnInit {
  @Input() userId: number = 0;
  @Input() userName: string = '';
  @Input() role: string = 'MEMBER';

  @Output() enrolled = new EventEmitter<void>();
  @Output() close = new EventEmitter<void>();

  // Scanner States: 'IDLE' | 'SCANNING' | 'SUCCESS' | 'ERROR'
  scanState: 'IDLE' | 'SCANNING' | 'SUCCESS' | 'ERROR' = 'IDLE';
  progress: number = 0;
  statusText: string = 'Place and touch finger on sensor to register';
  qualityScore: number = 0;
  uniqueFingerprintHash: string = '';
  errorMessage: string = '';
  isSaving: boolean = false;
  isSupported: boolean = true;
  deviceName: string = 'My Phone';

  constructor(private fingerprintService: FingerprintService) {}

  async ngOnInit(): Promise<void> {
    if (!this.userName) {
      this.userName = 'Member';
    }
    this.detectDeviceName();
    try {
      this.isSupported = await isFingerprintSupported();
    } catch {
      this.isSupported = true;
    }
  }

  detectDeviceName(): void {
    if (typeof navigator !== 'undefined') {
      const ua = navigator.userAgent;
      if (/iPhone/i.test(ua)) {
        this.deviceName = 'iPhone (Touch ID / Face ID)';
      } else if (/iPad/i.test(ua)) {
        this.deviceName = 'iPad (Biometric Sensor)';
      } else if (/Android/i.test(ua)) {
        this.deviceName = 'Android Phone (Fingerprint Sensor)';
      } else if (/Macintosh/i.test(ua)) {
        this.deviceName = 'MacBook (Touch ID)';
      } else if (/Windows/i.test(ua)) {
        this.deviceName = 'Windows PC (Windows Hello)';
      } else {
        this.deviceName = 'My Phone (Fingerprint Sensor)';
      }
    }
  }

  async startScanning(): Promise<void> {
    if (this.scanState === 'SCANNING' || this.isSaving) return;

    this.scanState = 'SCANNING';
    this.progress = 20;
    this.errorMessage = '';
    this.statusText = 'Contacting security module... Please touch your phone\'s fingerprint sensor when prompted.';

    try {
      // 1. Check WebAuthn platform authenticator support
      if (typeof window === 'undefined' || !window.PublicKeyCredential) {
        throw new Error('Biometric WebAuthn is not supported on this browser. Please open this page on your smartphone browser (Chrome on Android or Safari on iPhone) with HTTPS.');
      }

      // 2. Start registration with backend to get WebAuthn creation options
      this.statusText = 'Contacting server for biometric challenge...';
      const res = await this.fingerprintService.startRegistration().toPromise();
      if (!res || !res.publicKeyCredentialCreationOptions) {
        throw new Error('Could not obtain biometric registration challenge from server.');
      }

      this.progress = 50;
      this.statusText = '👆 Touch your phone\'s fingerprint sensor now...';

      // 3. Prepare creation options and invoke native biometric prompt on mobile/desktop
      const creationOptions = prepareCreationOptions(res.publicKeyCredentialCreationOptions);
      const credential = (await navigator.credentials.create(creationOptions)) as PublicKeyCredential;

      if (!credential) {
        throw new Error('Fingerprint registration was cancelled.');
      }

      this.progress = 85;
      this.isSaving = true;
      this.statusText = 'Fingerprint recognized! Finalizing cryptographic biometric enrollment...';

      // 4. Serialize credential and finish registration
      const credJson = publicKeyCredentialToJson(credential);
      const targetDeviceName = (this.deviceName && this.deviceName.trim()) ? this.deviceName.trim() : 'My Phone';

      const dev = await this.fingerprintService.finishRegistration(
        res.requestId,
        JSON.stringify(credJson),
        targetDeviceName
      ).toPromise();

      // 5. Success state
      this.isSaving = false;
      this.progress = 100;
      this.qualityScore = 99;
      this.uniqueFingerprintHash = dev?.deviceName || targetDeviceName;
      this.scanState = 'SUCCESS';
      this.statusText = `✓ Biometric Verified! Fingerprint passkey active on "${this.uniqueFingerprintHash}".`;

      // Emit enrolled event
      setTimeout(() => {
        this.enrolled.emit();
      }, 1500);

    } catch (err: any) {
      this.isSaving = false;
      this.scanState = 'ERROR';
      const msg = err.error?.message || err.message || '';
      if (err.name === 'NotAllowedError' || msg.includes('timed out or was not allowed') || msg.includes('privacy-considerations')) {
        this.errorMessage = 'Fingerprint verification was cancelled or timed out. Please tap "Try Again" and touch the sensor when prompted by your phone.';
      } else if (err.name === 'SecurityError' || msg.includes('relying party') || msg.includes('rpId')) {
        this.errorMessage = 'Security origin mismatch. Ensure you are accessing via the official domain with HTTPS.';
      } else {
        this.errorMessage = msg || 'Biometric fingerprint capture failed.';
      }
      this.statusText = 'Fingerprint registration failed.';
    }
  }

  cancelScanning(): void {
    if (this.scanState === 'SCANNING') {
      this.scanState = 'IDLE';
      this.progress = 0;
      this.statusText = 'Place and touch finger on sensor to register';
    }
  }

  retry(): void {
    this.scanState = 'IDLE';
    this.progress = 0;
    this.errorMessage = '';
    this.statusText = 'Place and touch finger on sensor to register';
  }

  closeModal(): void {
    this.close.emit();
  }
}
