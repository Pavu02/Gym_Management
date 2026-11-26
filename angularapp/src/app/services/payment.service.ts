import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Payment, PaymentRequest } from '../models/payment.model';
import { environment } from '../../environments/environment';

export interface ModeBreakdown {
  mode: string;
  count: number;
  amount: number;
}

export interface MonthlyCollection {
  label: string;
  paidAmount: number;
  pendingAmount: number;
  count: number;
}

export interface PaymentTransactionRow {
  id: number;
  invoiceNo: string;
  memberName: string;
  memberEmail: string;
  planName: string;
  amount: number;
  mode: string;
  status: string;
  paymentDate: string;
}

export interface PaymentReportDTO {
  totalCollected: number;
  totalPending: number;
  paidCount: number;
  pendingCount: number;
  totalTransactions: number;
  averageTicket: number;
  collectionRate: number;
  byMode: ModeBreakdown[];
  byMonth: MonthlyCollection[];
  transactions: PaymentTransactionRow[];
}

@Injectable({
  providedIn: 'root'
})
export class PaymentService {
  apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  addPayment(payment: PaymentRequest): Observable<Payment> {
    return this.http.post<Payment>(`${this.apiUrl}/payments`, payment);
  }

  updatePaymentStatus(id: number, status: string): Observable<Payment> {
    return this.http.patch<Payment>(`${this.apiUrl}/payments/${id}/status`, { status });
  }

  getAllPayments(): Observable<Payment[]> {
    return this.http.get<Payment[]>(`${this.apiUrl}/payments`);
  }

  getPaymentReport(from?: string, to?: string, mode?: string, status?: string): Observable<PaymentReportDTO> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    if (mode && mode !== 'ALL') params = params.set('mode', mode);
    if (status && status !== 'ALL') params = params.set('status', status);
    return this.http.get<PaymentReportDTO>(`${this.apiUrl}/payments/report`, { params });
  }

  exportPaymentReportCsv(from?: string, to?: string, mode?: string, status?: string): Observable<Blob> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    if (mode && mode !== 'ALL') params = params.set('mode', mode);
    if (status && status !== 'ALL') params = params.set('status', status);
    return this.http.get(`${this.apiUrl}/payments/report/export`, {
      params,
      responseType: 'blob'
    });
  }

  getPaymentsByMember(memberId: number): Observable<Payment[]> {
    return this.http.get<Payment[]>(`${this.apiUrl}/payments/member/${memberId}`);
  }

  getMyPayments(): Observable<Payment[]> {
    return this.http.get<Payment[]>(`${this.apiUrl}/payments/me`);
  }

  deletePayment(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/payments/${id}`);
  }
}
