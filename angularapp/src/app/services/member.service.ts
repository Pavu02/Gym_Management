import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MemberRequest, MemberResponse } from '../models/member.model';

@Injectable({
  providedIn: 'root'
})
export class MemberService {
  apiUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  getAllMembers(): Observable<MemberResponse[]> {
    return this.http.get<MemberResponse[]>(`${this.apiUrl}/members`);
  }

  getMemberById(id: number): Observable<MemberResponse> {
    return this.http.get<MemberResponse>(`${this.apiUrl}/members/${id}`);
  }

  addMember(member: MemberRequest): Observable<MemberResponse> {
    return this.http.post<MemberResponse>(`${this.apiUrl}/members`, member);
  }

  updateMember(id: number, member: MemberRequest): Observable<MemberResponse> {
    return this.http.put<MemberResponse>(`${this.apiUrl}/members/${id}`, member);
  }

  deleteMember(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/members/${id}`);
  }

  assignTrainer(id: number, trainerId: number): Observable<MemberResponse> {
    return this.http.put<MemberResponse>(`${this.apiUrl}/members/${id}/assign-trainer/${trainerId}`, {});
  }

  getMyProfile(): Observable<MemberResponse> {
    return this.http.get<MemberResponse>(`${this.apiUrl}/members/me`);
  }
}
