import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Plan } from '../models/plan.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class PlanService {
  apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getAllPlans(): Observable<Plan[]> {
    return this.http.get<Plan[]>(`${this.apiUrl}/plans`);
  }

  getPlanById(id: number): Observable<Plan> {
    return this.http.get<Plan>(`${this.apiUrl}/plans/${id}`);
  }

  addPlan(plan: Plan): Observable<Plan> {
    return this.http.post<Plan>(`${this.apiUrl}/plans`, plan);
  }

  updatePlan(id: number, plan: Plan): Observable<Plan> {
    return this.http.put<Plan>(`${this.apiUrl}/plans/${id}`, plan);
  }

  deletePlan(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/plans/${id}`);
  }
}
