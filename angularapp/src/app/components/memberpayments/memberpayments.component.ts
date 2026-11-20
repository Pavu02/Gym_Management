import { Component, OnInit } from '@angular/core';
import { PaymentService } from '../../services/payment.service';
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

  constructor(private paymentService: PaymentService) {}

  ngOnInit(): void {
    this.loadMyPayments();
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
