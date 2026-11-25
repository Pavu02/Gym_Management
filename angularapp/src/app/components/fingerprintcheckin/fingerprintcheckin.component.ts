import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { FingerprintService, CheckinResultDTO, FingerprintDeviceDTO } from '../../services/fingerprint.service';
import { prepareRequestOptions, publicKeyCredentialToJson } from '../../utils/webauthn.util';

@Component({
  selector: 'app-fingerprintcheckin',
  templateUrl: './fingerprintcheckin.component.html',
  styleUrls: ['./fingerprintcheckin.component.css']
})
export class FingerprintcheckinComponent implements OnInit {
  kioskToken: string = '';
  hasNoDevices: boolean = false;
  loadingDevices: boolean = true;
  devices: FingerprintDeviceDTO[] = [];

  status: 'IDLE' | 'SCANNING' | 'SUCCESS' | 'ERROR' = 'IDLE';
  errorMessage: string = '';
  isExpiredPlan: boolean = false;
  isTokenExpired: boolean = false;

  checkinResult: CheckinResultDTO | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService,
    private fingerprintService: FingerprintService
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe((params) => {
      this.kioskToken = params['k'] || '';

      if (!this.authService.isLoggedIn()) {
        const returnUrl = `/checkin/fingerprint${this.kioskToken ? '?k=' + this.kioskToken : ''}`;
        this.router.navigate(['/login'], { queryParams: { returnUrl } });
        return;
      }

      this.checkDevices();
    });
  }

  checkDevices(): void {
    this.loadingDevices = true;
    this.fingerprintService.getMyDevices().subscribe({
      next: (devices) => {
        this.devices = devices || [];
        this.hasNoDevices = this.devices.length === 0;
        this.loadingDevices = false;
      },
      error: () => {
        this.loadingDevices = false;
      }
    });
  }

  async touchCheckIn(): Promise<void> {
    if (!this.kioskToken) {
      this.status = 'ERROR';
      this.errorMessage = 'Missing kiosk check-in token. Please scan the QR code displayed on the gym screen.';
      return;
    }

    this.status = 'SCANNING';
    this.errorMessage = '';
    this.isExpiredPlan = false;
    this.isTokenExpired = false;

    this.fingerprintService.startCheckin(this.kioskToken).subscribe({
      next: async (res) => {
        try {
          const requestOptions = prepareRequestOptions(res.publicKeyCredentialRequestOptions);
          const assertion = (await navigator.credentials.get(requestOptions)) as PublicKeyCredential;

          if (!assertion) {
            throw new Error('Biometric check was cancelled.');
          }

          const assertionJson = publicKeyCredentialToJson(assertion);

          this.fingerprintService.finishCheckin(
            res.requestId,
            this.kioskToken,
            JSON.stringify(assertionJson)
          ).subscribe({
            next: (result) => {
              this.checkinResult = result;
              this.status = 'SUCCESS';
            },
            error: (err) => {
              this.status = 'ERROR';
              const backendMsg = err.error?.message || err.message || '';
              if (backendMsg.toLowerCase().includes('token') || backendMsg.toLowerCase().includes('expired')) {
                this.isTokenExpired = true;
                this.errorMessage = 'This kiosk QR code has expired. Please rescan the live QR code on the gym screen.';
              } else if (backendMsg.toLowerCase().includes('membership') || backendMsg.toLowerCase().includes('plan')) {
                this.isExpiredPlan = true;
                this.errorMessage = backendMsg;
              } else {
                this.errorMessage = backendMsg || 'Failed to complete check-in verification.';
              }
            }
          });
        } catch (err: any) {
          this.status = 'ERROR';
          const msg = err.error?.message || err.message || '';
          if (err.name === 'NotAllowedError' || msg.includes('timed out or was not allowed') || msg.includes('privacy-considerations')) {
            this.errorMessage = 'Fingerprint prompt was cancelled. Touch the button below to try again.';
          } else {
            this.errorMessage = msg || 'Biometric verification failed on this device.';
          }
        }
      },
      error: (err) => {
        this.status = 'ERROR';
        const msg = err.error?.message || err.message || '';
        if (msg.toLowerCase().includes('token') || msg.toLowerCase().includes('expired')) {
          this.isTokenExpired = true;
          this.errorMessage = 'The kiosk QR code has expired. Please scan the updated QR code on the gym display.';
        } else {
          this.errorMessage = msg || 'Unable to start fingerprint check-in.';
        }
      }
    });
  }

  goToSetup(): void {
    this.router.navigate(['/member/fingerprint']);
  }

  goToRenew(): void {
    this.router.navigate(['/member/plan']);
  }

  retry(): void {
    this.status = 'IDLE';
    this.errorMessage = '';
  }
}
