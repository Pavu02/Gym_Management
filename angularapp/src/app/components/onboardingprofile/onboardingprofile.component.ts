import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl } from '@angular/forms';
import { Router } from '@angular/router';
import { OnboardingService } from '../../services/onboarding.service';

export function ageRangeValidator(minAge: number, maxAge: number) {
  return (control: AbstractControl): { [key: string]: any } | null => {
    if (!control.value) return null;
    const dob = new Date(control.value);
    const today = new Date();
    let age = today.getFullYear() - dob.getFullYear();
    const monthDiff = today.getMonth() - dob.getMonth();
    if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < dob.getDate())) {
      age--;
    }
    if (age < minAge || age > maxAge) {
      return { invalidAge: { value: control.value, age } };
    }
    return null;
  };
}

@Component({
  selector: 'app-onboardingprofile',
  templateUrl: './onboardingprofile.component.html',
  styleUrls: ['./onboardingprofile.component.css']
})
export class OnboardingprofileComponent implements OnInit {
  profileForm!: FormGroup;
  bmi: number | null = null;
  bmiCategory: string = '';
  bmiColor: string = '#888888';
  submitting: boolean = false;
  errorMessage: string = '';

  fitnessGoals = [
    { id: 'WEIGHT_LOSS', label: 'Weight Loss', icon: '🔥', desc: 'Burn fat and get leaner' },
    { id: 'MUSCLE_GAIN', label: 'Muscle Gain', icon: '💪', desc: 'Build lean mass and strength' },
    { id: 'GENERAL_FITNESS', label: 'General Fitness', icon: '⚡', desc: 'Stay healthy, energetic & fit' },
    { id: 'ENDURANCE', label: 'Endurance', icon: '🏃', desc: 'Boost stamina and cardiovascular health' }
  ];

  constructor(
    private fb: FormBuilder,
    private onboardingService: OnboardingService,
    private router: Router
  ) {}

  ngOnInit(): void {
    // Check onboarding status: must have selected a plan first
    this.onboardingService.getStatus().subscribe({
      next: (status) => {
        if (!status.planId) {
          this.router.navigate(['/onboarding/plans']);
        }
      },
      error: () => {
        this.router.navigate(['/onboarding/plans']);
      }
    });

    this.profileForm = this.fb.group({
      gender: ['Male', [Validators.required]],
      dateOfBirth: ['', [Validators.required, ageRangeValidator(14, 80)]],
      heightCm: ['', [Validators.required, Validators.min(100), Validators.max(250)]],
      weightKg: ['', [Validators.required, Validators.min(30), Validators.max(250)]],
      fitnessGoal: ['MUSCLE_GAIN', [Validators.required]],
      medicalConditions: [''],
      emergencyContactName: ['', [Validators.required, Validators.minLength(2)]],
      emergencyContactPhone: ['', [Validators.required, Validators.pattern(/^[0-9]{10}$/)]]
    });

    this.profileForm.get('heightCm')?.valueChanges.subscribe(() => this.calculateBmi());
    this.profileForm.get('weightKg')?.valueChanges.subscribe(() => this.calculateBmi());
  }

  selectGoal(goalId: string): void {
    this.profileForm.patchValue({ fitnessGoal: goalId });
  }

  calculateBmi(): void {
    const height = Number(this.profileForm.get('heightCm')?.value);
    const weight = Number(this.profileForm.get('weightKg')?.value);

    if (height >= 100 && weight >= 30) {
      const hM = height / 100;
      const raw = weight / (hM * hM);
      this.bmi = Math.round(raw * 10) / 10;

      if (this.bmi < 18.5) {
        this.bmiCategory = 'Underweight';
        this.bmiColor = '#42a5f5';
      } else if (this.bmi < 25) {
        this.bmiCategory = 'Normal Weight';
        this.bmiColor = '#66bb6a';
      } else if (this.bmi < 30) {
        this.bmiCategory = 'Overweight';
        this.bmiColor = '#ffa726';
      } else {
        this.bmiCategory = 'Obese';
        this.bmiColor = '#ef5350';
      }
    } else {
      this.bmi = null;
      this.bmiCategory = '';
      this.bmiColor = '#888888';
    }
  }

  onBack(): void {
    this.router.navigate(['/onboarding/trainer']);
  }

  onSubmit(): void {
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.errorMessage = '';

    this.onboardingService.setupProfile(this.profileForm.value).subscribe({
      next: () => {
        this.submitting = false;
        this.router.navigate(['/onboarding/payment']);
      },
      error: (err) => {
        this.submitting = false;
        this.errorMessage = err.error?.message || 'Failed to update profile. Please verify your details.';
      }
    });
  }
}
