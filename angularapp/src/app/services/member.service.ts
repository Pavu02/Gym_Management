import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MemberRequest, MemberResponse } from '../models/member.model';

@Injectable({
  providedIn: 'root'
})
export class MemberService {
  apiUrl = 'http://localhost:8080/api/members';

  constructor(private http: HttpClient) {}

  getAllMembers(): Observable<MemberResponse[]> {
    return this.http.get<MemberResponse[]>(this.apiUrl);
  }

  getMemberById(id: number): Observable<MemberResponse> {
    return this.http.get<MemberResponse>(`${this.apiUrl}/${id}`);
  }

  addMember(member: MemberRequest): Observable<MemberResponse> {
    return this.http.post<MemberResponse>(this.apiUrl, member);
  }

  updateMember(id: number, member: MemberRequest): Observable<MemberResponse> {
    return this.http.put<MemberResponse>(`${this.apiUrl}/${id}`, member);
  }

  deleteMember(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  assignTrainer(id: number, trainerId: number): Observable<MemberResponse> {
    return this.http.put<MemberResponse>(`${this.apiUrl}/${id}/assign-trainer/${trainerId}`, {});
  }

  getMyProfile(): Observable<MemberResponse> {
    return this.http.get<MemberResponse>(`${this.apiUrl}/me`);
  }

  updateMyProfile(data: any): Observable<MemberResponse> {
    return this.http.put<MemberResponse>(`${this.apiUrl}/me/profile`, data);
  }

  changeTrainer(trainerId: number): Observable<MemberResponse> {
    return this.http.put<MemberResponse>(`${this.apiUrl}/me/trainer/${trainerId}`, {});
  }

  removeTrainer(): Observable<MemberResponse> {
    return this.http.put<MemberResponse>(`${this.apiUrl}/me/trainer/remove`, {});
  }

  renewMembership(data: { planId: number; mode: string }): Observable<MemberResponse> {
    return this.http.post<MemberResponse>(`${this.apiUrl}/me/renew`, data);
  }
}
