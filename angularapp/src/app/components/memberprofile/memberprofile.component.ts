import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MemberService } from '../../services/member.service';
import { MemberResponse } from '../../models/member.model';

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

  editForm!: FormGroup;

  fitnessGoals = [
    { id: 'WEIGHT_LOSS', label: 'Weight Loss' },
    { id: 'MUSCLE_GAIN', label: 'Muscle Gain' },
    { id: 'GENERAL_FITNESS', label: 'General Fitness' },
    { id: 'ENDURANCE', label: 'Endurance' }
  ];

  constructor(
    private memberService: MemberService,
    private fb: FormBuilder,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.loadProfile();
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
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load member profile';
        this.loading = false;
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
      error: (err) => {
        this.savingProfile = false;
        this.errorMessage = err.error?.message || 'Failed to update profile';
      }
    });
  }
}
