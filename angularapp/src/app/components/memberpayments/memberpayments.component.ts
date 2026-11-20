import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { PaymentService } from '../../services/payment.service';
import { MemberService } from '../../services/member.service';
import { Payment } from '../../models/payment.model';

@Component({
  selector: 'app-memberpayments',
  templateUrl: './memberpayments.component.html',
  styleUrls: ['./memberpayments.component.css']
})
export class MemberpaymentsComponent implements OnInit {
  payments: Payment[] = [];
  totalPaid: number = 0;
  loading: boolean = true;
  errorMessage: string = '';
  isExpired: boolean = false;

  constructor(
    private paymentService: PaymentService,
    private memberService: MemberService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.checkMembershipStatus();
    this.loadMyPayments();
  }

  checkMembershipStatus(): void {
    this.memberService.getMyProfile().subscribe({
      next: (profile) => {
        if (profile.status === 'EXPIRED' || (profile.daysLeft !== undefined && profile.daysLeft <= 0)) {
          this.isExpired = true;
        }
      }
    });
  }

  goToRenew(): void {
    this.router.navigate(['/member/plan']);
  }

  loadMyPayments(): void {
    this.loading = true;
    this.paymentService.getMyPayments().subscribe({
      next: (data) => {
        this.payments = data;
        this.calculateTotal();
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load payment history';
        this.loading = false;
      }
    });
  }

  calculateTotal(): void {
    this.totalPaid = this.payments
      .filter(p => p.status === 'PAID')
      .reduce((sum, p) => sum + p.amount, 0);
  }
}
