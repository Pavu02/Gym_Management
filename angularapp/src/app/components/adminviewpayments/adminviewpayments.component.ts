import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import {
  PaymentService,
  PaymentReportDTO,
  PaymentTransactionRow
} from '../../services/payment.service';
import { MemberService } from '../../services/member.service';
import { MemberResponse } from '../../models/member.model';
import { MonthlyRevenue } from '../../services/dashboard.service';
import { DonutSegment } from '../donutchart/donutchart.component';

@Component({
  selector: 'app-adminviewpayments',
  templateUrl: './adminviewpayments.component.html',
  styleUrls: ['./adminviewpayments.component.css']
})
export class AdminviewpaymentsComponent implements OnInit, OnDestroy {
  report: PaymentReportDTO | null = null;
  visibleTransactions: PaymentTransactionRow[] = [];
  members: MemberResponse[] = [];
  selectedMemberInfo: MemberResponse | null = null;

  paymentForm!: FormGroup;

  // Report Filters
  activePreset: 'ALL' | 'TODAY' | 'MONTH' | '30D' = 'ALL';
  fromDate = '';
  toDate = '';
  filterMode = 'ALL';
  filterStatus = 'ALL';
  searchQuery = '';
  viewMode: 'table' | 'cards' = 'table';

  // Charts
  revenueTrend: MonthlyRevenue[] = [];
  modeDonutSegs: DonutSegment[] = [];

  // UI state
  loading = true;
  submitting = false;
  exporting = false;
  updatingId: number | null = null;
  errorMessage = '';
  successMessage = '';
  receiptTx: PaymentTransactionRow | null = null;

  quickAmounts = [999, 1500, 2499, 4999, 8999];

  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private paymentService: PaymentService,
    private memberService: MemberService
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.loadMembers();
    this.loadReport();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  initForm(): void {
    this.paymentForm = this.fb.group({
      memberId: [null, [Validators.required]],
      amount: [null, [Validators.required, Validators.min(1)]],
      mode: ['UPI', [Validators.required]],
      status: ['PAID', [Validators.required]]
    });

    this.paymentForm.get('memberId')?.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(val => {
        const id = Number(val);
        this.selectedMemberInfo = this.members.find(m => m.id === id) || null;
      });
  }

  loadMembers(): void {
    this.memberService.getAllMembers()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (members) => (this.members = members || []),
        error: () => {}
      });
  }

  loadReport(): void {
    this.loading = true;
    this.paymentService.getPaymentReport(
      this.fromDate || undefined,
      this.toDate || undefined,
      this.filterMode,
      this.filterStatus
    )
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (rep) => {
          this.report = rep;
          this.applyLocalSearch();
          this.buildCharts(rep);
          this.loading = false;
        },
        error: (err) => {
          this.errorMessage = err.error?.message || 'Failed to load payment reports';
          this.loading = false;
        }
      });
  }

  private buildCharts(rep: PaymentReportDTO): void {
    this.revenueTrend = (rep.byMonth || []).map(m => ({
      label: m.label,
      amount: m.paidAmount || 0
    }));

    const colorMap: Record<string, string> = {
      UPI: '#FF5A00',
      CARD: '#8B5CF6',
      CASH: '#22C55E',
      ONLINE: '#38BDF8'
    };

    this.modeDonutSegs = (rep.byMode || [])
      .filter(b => b.amount > 0)
      .map(b => ({
        label: `${b.mode} (${b.count})`,
        value: Math.round(b.amount),
        color: colorMap[b.mode.toUpperCase()] || '#FF8A1F'
      }));
  }

  applyLocalSearch(): void {
    const txs = this.report?.transactions || [];
    const q = this.searchQuery.trim().toLowerCase();
    if (!q) {
      this.visibleTransactions = [...txs];
      return;
    }
    this.visibleTransactions = txs.filter(t =>
      (t.memberName || '').toLowerCase().includes(q) ||
      (t.memberEmail || '').toLowerCase().includes(q) ||
      (t.invoiceNo || '').toLowerCase().includes(q) ||
      (t.planName || '').toLowerCase().includes(q)
    );
  }

  applyPreset(preset: 'ALL' | 'TODAY' | 'MONTH' | '30D'): void {
    this.activePreset = preset;
    const now = new Date();
    const fmt = (d: Date) => d.toISOString().slice(0, 10);

    if (preset === 'TODAY') {
      this.fromDate = fmt(now);
      this.toDate = fmt(now);
    } else if (preset === 'MONTH') {
      const start = new Date(now.getFullYear(), now.getMonth(), 1);
      this.fromDate = fmt(start);
      this.toDate = fmt(now);
    } else if (preset === '30D') {
      const start = new Date(now);
      start.setDate(now.getDate() - 29);
      this.fromDate = fmt(start);
      this.toDate = fmt(now);
    } else {
      this.fromDate = '';
      this.toDate = '';
    }
    this.loadReport();
  }

  onDateFilterChange(): void {
    if (this.fromDate || this.toDate) {
      this.activePreset = 'ALL';
    }
    this.loadReport();
  }

  setModeFilter(mode: string): void {
    this.filterMode = mode;
    this.loadReport();
  }

  setStatusFilter(status: string): void {
    this.filterStatus = status;
    this.loadReport();
  }

  resetReportFilters(): void {
    this.activePreset = 'ALL';
    this.fromDate = '';
    this.toDate = '';
    this.filterMode = 'ALL';
    this.filterStatus = 'ALL';
    this.searchQuery = '';
    this.loadReport();
  }

  setFormMode(mode: string): void {
    this.paymentForm.patchValue({ mode });
  }

  setFormStatus(status: string): void {
    this.paymentForm.patchValue({ status });
  }

  setQuickAmount(amt: number): void {
    this.paymentForm.patchValue({ amount: amt });
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

    this.paymentService.addPayment(payload)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.submitting = false;
          const isPaid = payload.status === 'PAID';
          this.successMessage = isPaid
            ? 'Payment recorded! Membership extended and confirmation emails sent to both Member & Admin.'
            : 'Pending payment entry recorded in ledger.';
          this.paymentForm.reset({ mode: 'UPI', status: 'PAID' });
          this.selectedMemberInfo = null;
          this.loadMembers();
          this.loadReport();
        },
        error: (err) => {
          this.submitting = false;
          this.errorMessage = err.error?.message || 'Failed to record payment';
        }
      });
  }

  markAsPaid(tx: PaymentTransactionRow): void {
    if (!confirm(`Confirm payment ${tx.invoiceNo} (₹${tx.amount}) from ${tx.memberName} as PAID? Confirmation emails will be sent to Member & Admin.`)) {
      return;
    }

    this.updatingId = tx.id;
    this.errorMessage = '';
    this.successMessage = '';

    this.paymentService.updatePaymentStatus(tx.id, 'PAID')
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.updatingId = null;
          this.successMessage = `${tx.invoiceNo} marked as PAID! Member & Admin confirmation emails dispatched.`;
          this.loadMembers();
          this.loadReport();
        },
        error: (err) => {
          this.updatingId = null;
          this.errorMessage = err.error?.message || 'Failed to update payment status';
        }
      });
  }

  deletePayment(id: number): void {
    if (!confirm('Are you sure you want to delete this payment record?')) {
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';

    this.paymentService.deletePayment(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.successMessage = 'Payment record deleted successfully!';
          this.loadReport();
        },
        error: (err) => {
          this.errorMessage = err.error?.message || 'Failed to delete payment';
        }
      });
  }

  downloadCsvReport(): void {
    this.exporting = true;
    this.paymentService.exportPaymentReportCsv(
      this.fromDate || undefined,
      this.toDate || undefined,
      this.filterMode,
      this.filterStatus
    )
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (blob) => {
          this.exporting = false;
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `beastx-payments-report-${new Date().toISOString().slice(0, 10)}.csv`;
          a.click();
          URL.revokeObjectURL(url);
        },
        error: () => {
          this.exporting = false;
          this.errorMessage = 'Failed to export payment report CSV';
        }
      });
  }

  printStatement(): void {
    if (typeof window !== 'undefined') {
      window.print();
    }
  }

  openReceipt(tx: PaymentTransactionRow): void {
    this.receiptTx = tx;
  }

  closeReceipt(): void {
    this.receiptTx = null;
  }

  getInitials(name?: string): string {
    if (!name || !name.trim()) return 'BX';
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  formatINR(val?: number): string {
    const safe = val && Number.isFinite(val) ? val : 0;
    return '₹' + safe.toLocaleString('en-IN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
  }

  dismissAlerts(): void {
    this.successMessage = '';
    this.errorMessage = '';
  }
}
