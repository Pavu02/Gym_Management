import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DashboardStats } from '../models/dashboard.model';
import { environment } from '../../environments/environment';

export interface MonthlyRevenue { label: string; amount: number; }
export interface DayAttendance  { label: string; count: number; }
export interface CheckInsByMethod { face: number; fingerprint: number; manual: number; }
export interface MembersByStatus  { active: number; expired: number; pendingOnboarding: number; }
export interface PlanCount        { plan: string; count: number; }
export interface MonthlyCount     { label: string; count: number; }
export interface PeakHour         { hour: number; count: number; }
export interface TrainerLoad      { name: string; members: number; max: number; }

export interface DashboardAnalytics {
  revenueByMonth:      MonthlyRevenue[];
  attendanceLast7Days: DayAttendance[];
  checkInsByMethod:    CheckInsByMethod;
  membersByStatus:     MembersByStatus;
  membersByPlan:       PlanCount[];
  newMembersByMonth:   MonthlyCount[];
  peakHours:           PeakHour[];
  trainerLoad:         TrainerLoad[];
}

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.apiUrl}/dashboard/stats`);
  }

  getAnalytics(): Observable<DashboardAnalytics> {
    return this.http.get<DashboardAnalytics>(`${this.apiUrl}/admin/dashboard/analytics`);
  }
}

