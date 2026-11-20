import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { OnboardingService } from '../../services/onboarding.service';
import { OnboardingSummary } from '../../models/onboarding-summary.model';

@Component({
  selector: 'app-onboardingpayment',
  templateUrl: './onboardingpayment.component.html',
  styleUrls: ['./onboardingpayment.component.css']
})
export class OnboardingpaymentComponent implements OnInit {
  summary: OnboardingSummary | null = null;
  selectedMode: string = 'UPI';
  loading: boolean = true;
  submitting: boolean = false;
  errorMessage: string = '';

  paymentModes = [
    { id: 'UPI', label: 'UPI / QR', icon: '📱', desc: 'Google Pay, PhonePe, Paytm' },
    { id: 'CARD', label: 'Debit / Credit Card', icon: '💳', desc: 'Visa, MasterCard, RuPay' },
    { id: 'CASH', label: 'Cash at Desk', icon: '💵', desc: 'Pay directly at the gym counter' }
  ];

  constructor(
    private onboardingService: OnboardingService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.fetchSummary();
  }

  fetchSummary(): void {
    this.loading = true;
    this.errorMessage = '';

    this.onboardingService.getSummary().subscribe({
      next: (data) => {
        this.summary = data;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Failed to retrieve order summary.';
        // If not enrolled or profile not completed, redirect to earlier steps
        setTimeout(() => {
          this.onboardingService.getStatus().subscribe({
            next: (status) => {
              if (!status.planId) {
                this.router.navigate(['/onboarding/plans']);
              } else {
                this.router.navigate(['/onboarding/profile']);
              }
            }
          });
        }, 1500);
      }
    });
  }

  selectMode(modeId: string): void {
    this.selectedMode = modeId;
  }

  onBack(): void {
    this.router.navigate(['/onboarding/profile']);
  }

  onPay(): void {
    if (!this.summary || this.submitting) return;

    this.submitting = true;
    this.errorMessage = '';

    this.onboardingService.completePayment({ mode: this.selectedMode }).subscribe({
      next: (member) => {
        this.submitting = false;
        this.router.navigate(['/onboarding/success'], { state: { member } });
      },
      error: (err) => {
        this.submitting = false;
        this.errorMessage = err.error?.message || 'Payment processing failed. Please try again.';
      }
    });
  }
}
