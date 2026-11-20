import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { OnboardingService } from '../../services/onboarding.service';
import { Trainer } from '../../models/trainer.model';
import { Plan } from '../../models/plan.model';

@Component({
  selector: 'app-onboardingtrainer',
  templateUrl: './onboardingtrainer.component.html',
  styleUrls: ['./onboardingtrainer.component.css']
})
export class OnboardingtrainerComponent implements OnInit {
  trainers: Trainer[] = [];
  selectedTrainerId: number | null = null;
  selectedPlanId: number | null = null;
  currentPlan: Plan | null = null;
  loading: boolean = false;
  submitting: boolean = false;
  errorMessage: string = '';

  constructor(
    private onboardingService: OnboardingService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.fetchData();
  }

  fetchData(): void {
    this.loading = true;
    this.errorMessage = '';

    this.onboardingService.getStatus().subscribe({
      next: (status) => {
        if (!status.planId) {
          this.router.navigate(['/onboarding/plans']);
          return;
        }
        this.selectedPlanId = status.planId;
        this.selectedTrainerId = status.trainerId;

        // Fetch plan details to show duration
        this.onboardingService.getPlans().subscribe({
          next: (plans) => {
            this.currentPlan = plans.find(p => p.id === this.selectedPlanId) || null;
          }
        });

        // Fetch trainers
        this.onboardingService.getTrainers().subscribe({
          next: (trainers) => {
            this.trainers = trainers;
            this.loading = false;
          },
          error: (err) => {
            this.loading = false;
            this.errorMessage = err.error?.message || 'Failed to load trainers.';
          }
        });
      },
      error: () => {
        this.router.navigate(['/onboarding/plans']);
      }
    });
  }

  selectTrainer(trainerId: number | null): void {
    this.selectedTrainerId = trainerId;
  }

  getInitials(name?: string): string {
    if (!name) return 'TR';
    const parts = name.trim().split(' ');
    if (parts.length > 1) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.substring(0, 2).toUpperCase();
  }

  getTrainerCost(monthlyFee?: number): number {
    if (!monthlyFee || !this.currentPlan) return 0;
    return monthlyFee * this.currentPlan.durationMonths;
  }

  onBack(): void {
    this.router.navigate(['/onboarding/plans']);
  }

  onContinue(): void {
    if (!this.selectedPlanId) return;

    this.submitting = true;
    this.errorMessage = '';

    this.onboardingService.enroll({
      planId: this.selectedPlanId,
      trainerId: this.selectedTrainerId
    }).subscribe({
      next: () => {
        this.submitting = false;
        this.router.navigate(['/onboarding/profile']);
      },
      error: (err) => {
        this.submitting = false;
        this.errorMessage = err.error?.message || 'Failed to save trainer selection.';
      }
    });
  }
}
