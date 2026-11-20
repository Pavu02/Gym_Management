import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { OnboardingStatus } from '../models/onboarding-status.model';
import { OnboardingSummary } from '../models/onboarding-summary.model';
import { Plan } from '../models/plan.model';
import { Trainer } from '../models/trainer.model';
import { MemberResponse } from '../models/member.model';

@Injectable({
  providedIn: 'root'
})
export class OnboardingService {
  private apiUrl = 'http://localhost:8080/api/onboarding';

  constructor(private http: HttpClient) {}

  getStatus(): Observable<OnboardingStatus> {
    return this.http.get<OnboardingStatus>(`${this.apiUrl}/status`);
  }

  getPlans(): Observable<Plan[]> {
    return this.http.get<Plan[]>(`${this.apiUrl}/plans`);
  }

  getTrainers(): Observable<Trainer[]> {
    return this.http.get<Trainer[]>(`${this.apiUrl}/trainers`);
  }

  enroll(data: { planId: number; trainerId?: number | null }): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.apiUrl}/enroll`, data);
  }

  setupProfile(data: any): Observable<{ message: string }> {
    return this.http.put<{ message: string }>(`${this.apiUrl}/profile`, data);
  }

  getSummary(): Observable<OnboardingSummary> {
    return this.http.get<OnboardingSummary>(`${this.apiUrl}/summary`);
  }

  completePayment(data: { mode: string }): Observable<MemberResponse> {
    return this.http.post<MemberResponse>(`${this.apiUrl}/payment`, data);
  }
}
