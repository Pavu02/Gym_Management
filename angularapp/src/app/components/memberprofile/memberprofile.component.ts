import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MemberService } from '../../services/member.service';
import { MemberResponse } from '../../models/member.model';
import { PasskeyService, WebAuthnDevice } from '../../services/passkey.service';
import { BiometricService, BiometricStatusResponse } from '../../services/biometric.service';
import { FingerprintService, FingerprintStatusDTO } from '../../services/fingerprint.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-memberprofile',
  templateUrl: './memberprofile.component.html',
  styleUrls: ['./memberprofile.component.css']
})
export class MemberprofileComponent implements OnInit {
  profile: MemberResponse | null = null;
  daysLeft: number = 0;
  loading: boolean = true;
  isEditing: boolean = false;
  savingProfile: boolean = false;
  errorMessage: string = '';
  successMessage: string = '';

  // Passkey & Biometrics
  webAuthnSupported: boolean = false;
  passkeyDevices: WebAuthnDevice[] = [];
  biometricStatus: BiometricStatusResponse | null = null;
  fingerprintStatus: FingerprintStatusDTO | null = null;
  registeringPasskey: boolean = false;
  passkeyMessage: string = '';
  passkeyError: string = '';
  showFaceEnrollModal: boolean = false;
  showFingerprintEnrollModal: boolean = false;

  editForm!: FormGroup;

  fitnessGoals = [
    { id: 'WEIGHT_LOSS', label: 'Weight Loss' },
    { id: 'MUSCLE_GAIN', label: 'Muscle Gain' },
    { id: 'GENERAL_FITNESS', label: 'General Fitness' },
    { id: 'ENDURANCE', label: 'Endurance' }
  ];

  constructor(
    private memberService: MemberService,
    private passkeyService: PasskeyService,
    private biometricService: BiometricService,
    private fingerprintService: FingerprintService,
    private authService: AuthService,
    private fb: FormBuilder,
    private router: Router
  ) { }

  get currentUserId(): number {
    const idStr = this.authService.getUserId();
    return idStr ? Number(idStr) : (this.profile?.id || 0);
  }

  ngOnInit(): void {
    this.webAuthnSupported = this.passkeyService.isWebAuthnSupported();
    this.initForm();
    this.loadProfile();
    this.loadBiometricsAndPasskeys();
  }

  initForm(): void {
    this.editForm = this.fb.group({
      name: ['', Validators.required],
      phone: ['', [Validators.required, Validators.pattern(/^[0-9]{10}$/)]],
      gender: ['Male', Validators.required],
      dateOfBirth: [''],
      heightCm: ['', [Validators.required, Validators.min(100), Validators.max(250)]],
      weightKg: ['', [Validators.required, Validators.min(30), Validators.max(250)]],
      fitnessGoal: ['MUSCLE_GAIN', Validators.required],
      medicalConditions: [''],
      emergencyContactName: ['', Validators.required],
      emergencyContactPhone: ['', [Validators.required, Validators.pattern(/^[0-9]{10}$/)]]
    });
  }

  loadProfile(): void {
    this.loading = true;
    this.errorMessage = '';
    this.memberService.getMyProfile().subscribe({
      next: (data) => {
        this.profile = data;
        this.daysLeft = data.daysLeft ?? 0;
        this.populateForm(data);
        this.loading = false;
      },
      error: (err: any) => {
        this.errorMessage = err.error?.message || 'Failed to load member profile';
        this.loading = false;
      }
    });
  }

  loadBiometricsAndPasskeys(): void {
    this.biometricService.getBiometricStatus().subscribe({
      next: (status) => {
        this.biometricStatus = status;
      },
      error: () => { }
    });

    this.fingerprintService.getMyStatus().subscribe({
      next: (status: any) => {
        this.fingerprintStatus = status;
      },
      error: () => { }
    });

    if (this.webAuthnSupported) {
      this.passkeyService.getDevices().subscribe({
        next: (devices) => {
          this.passkeyDevices = devices || [];
          this.loadFingerprintDevices();
        },
        error: () => {
          this.loadFingerprintDevices();
        }
      });
    } else {
      this.loadFingerprintDevices();
    }
  }

  private loadFingerprintDevices(): void {
    this.fingerprintService.getMyDevices().subscribe({
      next: (fpDevices) => {
        if (fpDevices && fpDevices.length > 0) {
          const existingNames = new Set(this.passkeyDevices.map(d => d.deviceName));
          for (const d of fpDevices) {
            if (!existingNames.has(d.deviceName)) {
              this.passkeyDevices.push({
                id: d.id,
                credentialId: String(d.id),
                deviceName: d.deviceName,
                signCount: 0,
                createdAt: d.createdAt
              });
            }
          }
        }
      },
      error: () => { }
    });
  }

  async addPasskey(): Promise<void> {
    this.registeringPasskey = true;
    this.passkeyMessage = '';
    this.passkeyError = '';

    try {
      const deviceName = prompt('Enter a name for this biometric passkey (e.g. My iPhone, Pixel 8):') || 'My Biometric Device';
      const created = await this.passkeyService.registerPasskey(deviceName);
      this.passkeyMessage = 'Fingerprint passkey registered successfully on ' + created.deviceName + '!';
      this.registeringPasskey = false;
      this.loadBiometricsAndPasskeys();
    } catch (err: any) {
      this.registeringPasskey = false;
      const msg = err.error?.message || err.message || '';
      if (err.name === 'NotAllowedError' || msg.includes('timed out or was not allowed') || msg.includes('privacy-considerations')) {
        this.passkeyError = 'Biometric prompt was cancelled or is not configured on this PC. To use fingerprint check-in at the gym, open /member/fingerprint on your smartphone (iPhone Touch ID / Android Fingerprint).';
      } else {
        this.passkeyError = msg || 'Passkey enrollment was cancelled or failed.';
      }
    }
  }

  openFaceEnroll(): void {
    this.showFaceEnrollModal = true;
  }

  closeFaceEnroll(): void {
    this.showFaceEnrollModal = false;
  }

  onFaceEnrolled(): void {
    this.showFaceEnrollModal = false;
    this.successMessage = 'Your face has been successfully registered for auto check-in!';
    this.loadBiometricsAndPasskeys();
  }

  openFingerprintEnroll(): void {
    this.showFingerprintEnrollModal = true;
  }

  closeFingerprintEnroll(): void {
    this.showFingerprintEnrollModal = false;
  }

  onFingerprintEnrolled(): void {
    this.showFingerprintEnrollModal = false;
    this.successMessage = 'Your unique fingerprint has been enrolled successfully for gym check-in!';
    this.loadBiometricsAndPasskeys();
  }

  deleteFingerprint(): void {
    if (!confirm('Are you sure you want to remove your enrolled fingerprint biometric?')) {
      return;
    }

    const userId = this.currentUserId;
    this.fingerprintService.deleteFingerprint(userId).subscribe({
      next: () => {
        this.successMessage = 'Fingerprint removed successfully.';
        this.loadBiometricsAndPasskeys();
      },
      error: (err: any) => {
        this.errorMessage = err.error?.message || 'Failed to remove fingerprint biometric.';
      }
    });
  }

  deletePasskey(credentialId: string): void {
    if (!confirm('Are you sure you want to remove this passkey device?')) {
      return;
    }

    const numericId = Number(credentialId);
    if (!isNaN(numericId) && numericId > 0) {
      this.fingerprintService.deleteMyDevice(numericId).subscribe({
        next: () => {
          this.passkeyMessage = 'Device removed successfully.';
          this.loadBiometricsAndPasskeys();
        },
        error: (err: any) => {
          this.passkeyError = err.error?.message || 'Failed to remove device.';
        }
      });
      return;
    }

    this.passkeyService.deleteDevice(credentialId).subscribe({
      next: () => {
        this.passkeyMessage = 'Passkey removed successfully.';
        this.loadBiometricsAndPasskeys();
      },
      error: (err: any) => {
        this.passkeyError = err.error?.message || 'Failed to delete passkey device.';
      }
    });
  }

  populateForm(data: MemberResponse): void {
    this.editForm.patchValue({
      name: data.name,
      phone: data.phone,
      gender: data.gender || 'Male',
      dateOfBirth: data.dateOfBirth || '',
      heightCm: data.heightCm || '',
      weightKg: data.weightKg || '',
      fitnessGoal: data.fitnessGoal || 'MUSCLE_GAIN',
      medicalConditions: data.medicalConditions || '',
      emergencyContactName: data.emergencyContactName || '',
      emergencyContactPhone: data.emergencyContactPhone || ''
    });
  }

  toggleEdit(): void {
    this.isEditing = !this.isEditing;
    this.errorMessage = '';
    this.successMessage = '';
    if (this.isEditing && this.profile) {
      this.populateForm(this.profile);
    }
  }

  getBmiCategory(bmi?: number): { label: string; color: string } {
    if (!bmi) return { label: 'Not calculated', color: '#888888' };
    if (bmi < 18.5) return { label: 'Underweight', color: '#42a5f5' };
    if (bmi < 25) return { label: 'Normal Weight', color: '#66bb6a' };
    if (bmi < 30) return { label: 'Overweight', color: '#ffa726' };
    return { label: 'Obese', color: '#ef5350' };
  }

  getGoalLabel(goal?: string): string {
    const found = this.fitnessGoals.find(g => g.id === goal);
    return found ? found.label : (goal || 'Not specified');
  }

  goToRenew(): void {
    this.router.navigate(['/member/plan']);
  }

  saveProfile(): void {
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }

    this.savingProfile = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.memberService.updateMyProfile(this.editForm.value).subscribe({
      next: (updated) => {
        this.profile = updated;
        this.daysLeft = updated.daysLeft ?? 0;
        this.savingProfile = false;
        this.isEditing = false;
        this.successMessage = 'Profile updated successfully!';
      },
      error: (err: any) => {
        this.savingProfile = false;
        this.errorMessage = err.error?.message || 'Failed to update profile';
      }
    });
  }
}
