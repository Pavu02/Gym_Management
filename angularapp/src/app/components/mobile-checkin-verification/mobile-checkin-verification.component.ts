import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { QrCheckInService, QrSessionValidateResponse } from '../../services/qr-checkin.service';
import { CheckInVerificationResponse, WebAuthnService } from '../../services/webauthn.service';
import { mapWebAuthnClientError } from '../../utils/webauthn.util';

type VerifyStage =
  | 'VALIDATING_QR'
  | 'STAFF_OR_WRONG_DEVICE'
  | 'UNREGISTERED_PHONE'
  | 'READY_TO_VERIFY'
  | 'VERIFYING'
  | 'CONFIRMING_OFFLINE_RECOVERY'
  | 'SUCCESS'
  | 'ERROR';

@Component({
  selector: 'app-mobile-checkin-verification',
  templateUrl: './mobile-checkin-verification.component.html',
  styleUrls: ['./mobile-checkin-verification.component.css']
})
export class MobileCheckinVerificationComponent implements OnInit, OnDestroy {
  qrToken = '';
  stage: VerifyStage = 'VALIDATING_QR';

  webAuthnSupported = false;
  platformAvailable = false;

  validationInfo: QrSessionValidateResponse | null = null;
  preparedChallengeId = '';
  preparedRequestOptions: CredentialRequestOptions | null = null;
  idempotencyKey = '';
  remainingSeconds = 0;

  result: CheckInVerificationResponse | null = null;
  statusMessage = 'Validating gym check-in QR session...';
  errorMessage = '';
  canRetryVerification = false;
  isExpiredPlan = false;
  isQrExpiredOrUsed = false;

  private coords: { latitude: number; longitude: number } | null = null;
  private countdownTimer: any = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService,
    private qrCheckInService: QrCheckInService,
    private webAuthnService: WebAuthnService
  ) {}

  async ngOnInit(): Promise<void> {
    this.route.queryParamMap.subscribe(async (params) => {
      const token = (params.get('token') || params.get('k') || '').trim();
      this.qrToken = token;

      if (!this.authService.isLoggedIn()) {
        const returnUrl = `/check-in/verify${token ? '?token=' + encodeURIComponent(token) : ''}`;
        this.router.navigate(['/login'], { queryParams: { returnUrl } });
        return;
      }

      const role = this.authService.getRole();
      if (role !== 'MEMBER') {
        this.stage = 'STAFF_OR_WRONG_DEVICE';
        this.errorMessage = 'This link must be opened on the member\'s registered smartphone logged in with a MEMBER account.';
        return;
      }

      if (!token) {
        this.stage = 'ERROR';
        this.errorMessage = 'Missing QR token. Scan the live QR code shown at the gym entrance.';
        return;
      }

      this.webAuthnSupported = this.webAuthnService.isSupported();
      this.platformAvailable = await this.webAuthnService.isPlatformAuthenticatorAvailable();

      // Check if browser already completed verification and saved idempotencyKey before losing connection
      const savedRecovery = this.readPendingRecovery(token);
      if (savedRecovery) {
        this.idempotencyKey = savedRecovery;
        await this.tryRecoverStatus(true);
        if (this.stage === 'SUCCESS') {
          return;
        }
      }

      await this.validateQrAndPrefetchChallenge();
    });
  }

  ngOnDestroy(): void {
    this.stopCountdown();
  }

  async validateQrAndPrefetchChallenge(): Promise<void> {
    this.stage = 'VALIDATING_QR';
    this.errorMessage = '';
    this.isExpiredPlan = false;
    this.isQrExpiredOrUsed = false;
    this.statusMessage = 'Validating QR session with gym server...';

    // Optional location lookup without blocking unless backend STRICT mode demands it
    this.coords = await this.qrCheckInService.tryGetCurrentPosition(2000);

    this.qrCheckInService.validateSession({
      token: this.qrToken,
      clientTimestamp: new Date().toISOString(),
      latitude: this.coords?.latitude,
      longitude: this.coords?.longitude
    }).subscribe({
      next: (val) => {
        this.validationInfo = val;
        this.remainingSeconds = val.remainingSeconds;
        this.startCountdown(val.expiresAt);

        if (!val.hasRegisteredDevices) {
          this.stage = 'UNREGISTERED_PHONE';
          this.errorMessage = 'This phone is not registered for biometric check-in.';
          return;
        }

        if (!this.webAuthnSupported) {
          this.stage = 'ERROR';
          this.errorMessage = 'This phone browser does not support WebAuthn verification. Open with Android Chrome or iPhone Safari.';
          return;
        }

        // Prefetch challenge immediately so tapping Verify triggers navigator.credentials.get()
        // SYNCHRONOUSLY inside user gesture (critical for iOS Safari / Android Chrome)
        this.idempotencyKey = this.generateIdempotencyKey();
        this.prefetchChallenge();
      },
      error: (err) => {
        this.handleBackendError(err);
      }
    });
  }

  private prefetchChallenge(): void {
    this.webAuthnService.getAuthenticationOptions(this.qrToken, this.idempotencyKey).subscribe({
      next: (opts) => {
        this.preparedChallengeId = opts.challengeId;
        this.preparedRequestOptions = this.webAuthnService.prepareAssertionOptions(opts.publicKeyCredentialRequestOptions);
        this.stage = 'READY_TO_VERIFY';
        this.statusMessage = this.platformAvailable
          ? 'Place your finger on your phone\'s fingerprint sensor.'
          : 'Verify your identity using your phone\'s screen lock.';
      },
      error: (err) => {
        this.handleBackendError(err);
      }
    });
  }

  /**
   * Executes navigator.credentials.get() synchronously inside the tap handler
   * using pre-fetched assertion options so iOS Touch ID/Face ID and Android
   * fingerprint prompts never fail due to async user-gesture timeout.
   */
  async verifyAndCheckIn(): Promise<void> {
    if (!this.preparedRequestOptions || !this.preparedChallengeId) {
      await this.validateQrAndPrefetchChallenge();
      return;
    }

    this.stage = 'VERIFYING';
    this.errorMessage = '';
    this.canRetryVerification = false;
    this.statusMessage = 'Place your finger on your phone\'s fingerprint sensor.';

    let assertion: PublicKeyCredential | null;
    try {
      assertion = (await navigator.credentials.get(this.preparedRequestOptions)) as PublicKeyCredential | null;
    } catch (err: any) {
      this.stage = 'ERROR';
      this.canRetryVerification = true;
      this.errorMessage = mapWebAuthnClientError(err, 'authentication');
      // Refresh single-use challenge for retry
      this.preparedRequestOptions = null;
      return;
    }

    if (!assertion) {
      this.stage = 'ERROR';
      this.canRetryVerification = true;
      this.errorMessage = 'Fingerprint verification was cancelled. Please try again.';
      this.preparedRequestOptions = null;
      return;
    }

    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      this.savePendingRecovery(this.qrToken, this.idempotencyKey);
      this.stage = 'CONFIRMING_OFFLINE_RECOVERY';
      this.statusMessage = 'Verification succeeded locally, waiting for internet connection to confirm attendance...';
      return;
    }

    const attachment = (assertion as any).authenticatorAttachment || null;
    const verificationHint = this.platformAvailable ? 'BIOMETRIC' : 'DEVICE_PIN';

    this.savePendingRecovery(this.qrToken, this.idempotencyKey);
    this.statusMessage = 'Confirming attendance with gym server...';

    this.webAuthnService.verifyAuthentication({
      challengeId: this.preparedChallengeId,
      qrToken: this.qrToken,
      credentialJson: this.webAuthnService.serializeAssertion(assertion),
      idempotencyKey: this.idempotencyKey,
      latitude: this.coords?.latitude,
      longitude: this.coords?.longitude,
      authenticatorAttachment: attachment,
      verificationHint
    }).subscribe({
      next: (res) => {
        this.clearPendingRecovery(this.qrToken);
        this.stopCountdown();
        this.result = res;
        this.stage = 'SUCCESS';
      },
      error: (err) => {
        if (err.status === 0) {
          // Network/timeout uncertain transaction: let user confirm with idempotencyKey without double-check-in
          this.stage = 'CONFIRMING_OFFLINE_RECOVERY';
          this.statusMessage = 'Verification succeeded, but attendance status is being confirmed.';
          return;
        }
        this.clearPendingRecovery(this.qrToken);
        this.preparedRequestOptions = null;
        this.handleBackendError(err);
      }
    });
  }

  async tryRecoverStatus(silent = false): Promise<void> {
    if (!this.idempotencyKey) {
      return;
    }
    this.qrCheckInService.confirmByIdempotencyKey(this.idempotencyKey).subscribe({
      next: (res) => {
        this.clearPendingRecovery(this.qrToken);
        this.stopCountdown();
        this.result = res;
        this.stage = 'SUCCESS';
      },
      error: () => {
        if (!silent) {
          this.stage = 'ERROR';
          this.canRetryVerification = true;
          this.errorMessage = 'Could not confirm previous submission yet. Scan the current QR or tap Try Again.';
        }
      }
    });
  }

  private handleBackendError(err: any): void {
    this.stage = 'ERROR';
    const msg = err?.error?.message || err?.message || 'Verification failed.';
    const lower = msg.toLowerCase();

    if (err.status === 410 || lower.includes('expired')) {
      if (lower.includes('membership')) {
        this.isExpiredPlan = true;
        this.errorMessage = 'Your membership has expired. Please contact the gym administrator.';
      } else {
        this.isQrExpiredOrUsed = true;
        this.errorMessage = 'This QR code has expired. Ask the administrator to generate a new one.';
      }
    } else if (err.status === 409 && lower.includes('already checked in')) {
      this.errorMessage = 'You are already checked in.';
    } else if (err.status === 409) {
      this.isQrExpiredOrUsed = true;
      this.errorMessage = 'This QR code has already been used.';
    } else if (err.status === 422 && lower.includes('membership')) {
      this.isExpiredPlan = true;
      this.errorMessage = msg;
    } else if (lower.includes('not registered')) {
      this.stage = 'UNREGISTERED_PHONE';
      this.errorMessage = 'This phone is not registered for biometric check-in.';
    } else {
      this.canRetryVerification = !this.isQrExpiredOrUsed;
      this.errorMessage = msg;
    }
  }

  private startCountdown(expiresAtIso: string): void {
    this.stopCountdown();
    const expMs = new Date(expiresAtIso).getTime();
    this.countdownTimer = setInterval(() => {
      const rem = Math.max(0, Math.ceil((expMs - Date.now()) / 1000));
      this.remainingSeconds = rem;
      if (rem <= 0) {
        this.stopCountdown();
        if (this.stage === 'READY_TO_VERIFY') {
          this.stage = 'ERROR';
          this.isQrExpiredOrUsed = true;
          this.errorMessage = 'This QR code has expired. Ask the administrator to generate a new one.';
        }
      }
    }, 1000);
  }

  private stopCountdown(): void {
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    }
  }

  private generateIdempotencyKey(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
    return `idem-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
  }

  private savePendingRecovery(token: string, key: string): void {
    try {
      sessionStorage.setItem(`bx_checkin_${token.slice(0, 12)}`, key);
    } catch {}
  }

  private readPendingRecovery(token: string): string | null {
    try {
      return sessionStorage.getItem(`bx_checkin_${token.slice(0, 12)}`);
    } catch {
      return null;
    }
  }

  private clearPendingRecovery(token: string): void {
    try {
      sessionStorage.removeItem(`bx_checkin_${token.slice(0, 12)}`);
    } catch {}
  }

  goToDevices(): void {
    this.router.navigate(['/member/devices']);
  }

  goToPlan(): void {
    this.router.navigate(['/member/plan']);
  }
}
