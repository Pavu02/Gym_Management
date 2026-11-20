import { Component, OnInit } from '@angular/core';
import { MemberService } from '../../services/member.service';
import { PlanService } from '../../services/plan.service';
import { MemberResponse } from '../../models/member.model';
import { Plan } from '../../models/plan.model';

@Component({
  selector: 'app-memberviewplan',
  templateUrl: './memberviewplan.component.html',
  styleUrls: ['./memberviewplan.component.css']
})
export class MemberviewplanComponent implements OnInit {
  member: MemberResponse | null = null;
  allPlans: Plan[] = [];
  loading: boolean = true;
  renewingPlanId: number | null = null;
  selectedMode: string = 'UPI';
  submitting: boolean = false;
  errorMessage: string = '';
  successMessage: string = '';

  paymentModes = ['UPI', 'CARD', 'CASH'];

  constructor(
    private memberService: MemberService,
    private planService: PlanService
  ) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.errorMessage = '';
    this.memberService.getMyProfile().subscribe({
      next: (profile) => {
        this.member = profile;
        this.loadAllPlans();
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load member profile';
        this.loadAllPlans();
      }
    });
  }

  loadAllPlans(): void {
    this.planService.getAllPlans().subscribe({
      next: (plans) => {
        this.allPlans = plans.sort((a, b) => a.price - b.price);
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  openRenewDialog(planId?: number): void {
    if (planId) {
      this.renewingPlanId = planId;
      this.errorMessage = '';
      this.successMessage = '';
    }
  }

  cancelRenew(): void {
    this.renewingPlanId = null;
  }

  confirmRenew(planId?: number): void {
    if (!planId) return;

    this.submitting = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.memberService.renewMembership({
      planId: planId,
      mode: this.selectedMode
    }).subscribe({
      next: (updated) => {
        this.member = updated;
        this.submitting = false;
        this.renewingPlanId = null;
        this.successMessage = `Successfully renewed with the ${updated.planName} plan! Membership valid until ${updated.expiryDate}.`;
      },
      error: (err) => {
        this.submitting = false;
        this.errorMessage = err.error?.message || 'Renewal failed. Please try again.';
      }
    });
  }

  getFeatures(featuresStr?: string): string[] {
    if (!featuresStr) return [];
    return featuresStr.split(',').map(f => f.trim()).filter(f => f.length > 0);
  }
}
