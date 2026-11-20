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
  errorMessage: string = '';

  constructor(
    private memberService: MemberService,
    private planService: PlanService
  ) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
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
        this.allPlans = plans;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }
}
