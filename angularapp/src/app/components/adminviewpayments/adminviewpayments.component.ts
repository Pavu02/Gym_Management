import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { PaymentService } from '../../services/payment.service';
import { MemberService } from '../../services/member.service';
import { Payment } from '../../models/payment.model';
import { MemberResponse } from '../../models/member.model';

@Component({
  selector: 'app-adminviewpayments',
  templateUrl: './adminviewpayments.component.html',
  styleUrls: ['./adminviewpayments.component.css']
})
export class AdminviewpaymentsComponent implements OnInit {
  payments: Payment[] = [];
  members: MemberResponse[] = [];
  paymentForm!: FormGroup;
  loading: boolean = true;
  submitting: boolean = false;
  errorMessage: string = '';
  successMessage: string = '';

  constructor(
    private fb: FormBuilder,
    private paymentService: PaymentService,
    private memberService: MemberService
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.loadData();
  }

  initForm(): void {
    this.paymentForm = this.fb.group({
      memberId: [null, [Validators.required]],
      amount: [null, [Validators.required, Validators.min(1)]],
      mode: ['UPI', [Validators.required]],
      status: ['PAID', [Validators.required]]
    });
  }

  loadData(): void {
    this.loading = true;
    this.paymentService.getAllPayments().subscribe({
      next: (payments) => {
        this.payments = payments;
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load payments';
        this.loading = false;
      }
    });

    this.memberService.getAllMembers().subscribe({
      next: (members) => (this.members = members),
      error: () => {}
    });
  }

  onSubmit(): void {
    if (this.paymentForm.invalid) {
      this.paymentForm.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.errorMessage = '';
    this.successMessage = '';

    const formVal = this.paymentForm.value;
    const payload = {
      ...formVal,
      memberId: +formVal.memberId,
      amount: +formVal.amount
    };

    this.paymentService.addPayment(payload).subscribe({
      next: () => {
        this.submitting = false;
        this.successMessage = 'Payment recorded successfully! Membership updated.';
        this.paymentForm.reset({ mode: 'UPI', status: 'PAID' });
        this.loadData();
      },
      error: (err) => {
        this.submitting = false;
        this.errorMessage = err.error?.message || 'Failed to record payment';
      }
    });
  }

  deletePayment(id: number): void {
    if (!confirm('Are you sure you want to delete this payment record?')) {
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';

    this.paymentService.deletePayment(id).subscribe({
      next: () => {
        this.successMessage = 'Payment record deleted successfully!';
        this.loadData();
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to delete payment';
      }
    });
  }
}
