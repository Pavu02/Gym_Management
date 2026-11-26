import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MemberRequest, MemberResponse, MemberStatusActionResponse } from '../models/member.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class MemberService {
  apiUrl = `${environment.apiUrl}/members`;
  adminMembersUrl = `${environment.apiUrl}/admin/members`;

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

  deactivateMember(id: number, reason?: string, expectedVersion?: number): Observable<MemberStatusActionResponse> {
    return this.http.patch<MemberStatusActionResponse>(`${this.adminMembersUrl}/${id}/deactivate`, {
      reason: reason || null,
      expectedVersion: expectedVersion ?? null
    });
  }

  reactivateMember(id: number, expectedVersion?: number): Observable<MemberStatusActionResponse> {
    return this.http.patch<MemberStatusActionResponse>(`${this.adminMembersUrl}/${id}/reactivate`, {
      expectedVersion: expectedVersion ?? null
    });
  }

  deleteMember(id: number): Observable<MemberStatusActionResponse> {
    return this.deactivateMember(id);
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
