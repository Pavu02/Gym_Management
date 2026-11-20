import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { OnboardingService } from '../../services/onboarding.service';
import { AuthService } from '../../services/auth.service';
import { Plan } from '../../models/plan.model';

@Component({
  selector: 'app-onboardingplans',
  templateUrl: './onboardingplans.component.html',
  styleUrls: ['./onboardingplans.component.css']
})
export class OnboardingplansComponent implements OnInit {
  plans: Plan[] = [];
  selectedPlanId: number | null = null;
  memberName: string = '';
  loading: boolean = false;
  submitting: boolean = false;
  errorMessage: string = '';

  constructor(
    private onboardingService: OnboardingService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.memberName = this.authService.getName() || 'Member';
    this.fetchPlans();
  }

  fetchPlans(): void {
    this.loading = true;
    this.errorMessage = '';

    this.onboardingService.getPlans().subscribe({
      next: (plans) => {
        this.plans = plans.sort((a, b) => a.price - b.price);
        this.checkExistingStatus();
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Failed to load plans.';
      }
    });
  }

  checkExistingStatus(): void {
    this.onboardingService.getStatus().subscribe({
      next: (status) => {
        this.loading = false;
        if (status.planId) {
          this.selectedPlanId = status.planId;
        } else if (this.plans.length > 0) {
          const pop = this.plans.find(p => p.popular);
          this.selectedPlanId = pop ? pop.id! : this.plans[0].id!;
        }
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  selectPlan(planId?: number): void {
    if (planId) {
      this.selectedPlanId = planId;
    }
  }

  getFeatures(featuresStr?: string): string[] {
    if (!featuresStr) return [];
    return featuresStr.split(',').map(f => f.trim()).filter(f => f.length > 0);
  }

  onContinue(): void {
    if (!this.selectedPlanId) return;

    this.submitting = true;
    this.errorMessage = '';

    this.onboardingService.enroll({ planId: this.selectedPlanId }).subscribe({
      next: () => {
        this.submitting = false;
        this.router.navigate(['/onboarding/trainer']);
      },
      error: (err) => {
        this.submitting = false;
        this.errorMessage = err.error?.message || 'Failed to select plan.';
      }
    });
  }
}
