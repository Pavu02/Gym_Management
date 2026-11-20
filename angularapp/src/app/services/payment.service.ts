import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Payment, PaymentRequest } from '../models/payment.model';

@Injectable({
  providedIn: 'root'
})
export class PaymentService {
  apiUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  addPayment(payment: PaymentRequest): Observable<Payment> {
    return this.http.post<Payment>(`${this.apiUrl}/payments`, payment);
  }

  getAllPayments(): Observable<Payment[]> {
    return this.http.get<Payment[]>(`${this.apiUrl}/payments`);
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
