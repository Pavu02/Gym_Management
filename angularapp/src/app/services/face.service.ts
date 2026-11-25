import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface FaceProfileDescriptorDTO {
  userId: number;
  name: string;
  role: string;
  descriptor: number[];
}

export interface FaceCheckinResponseDTO {
  userId: number;
  name: string;
  role: string;
  photo?: string | null;
  phone?: string;
  email?: string;
  planName?: string;
  planStartDate?: string;
  planEndDate?: string;
  remainingDays?: number;
  assignedTrainerName?: string;
  totalAttendanceThisMonth: number;
  checkInTime: string;
  status: 'CHECKED_IN' | 'ALREADY_CHECKED_IN' | 'MEMBERSHIP_EXPIRED' | 'NO_PLAN' | string;
  expiringSoon?: boolean;
  alreadyCheckedIn?: boolean;
  method?: string;
}

@Injectable({
  providedIn: 'root'
})
export class FaceService {
  private apiUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  enrollFace(userId: number, descriptor: number[]): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/face/enroll/${userId}`, descriptor);
  }

  getAllDescriptors(): Observable<FaceProfileDescriptorDTO[]> {
    return this.http.get<FaceProfileDescriptorDTO[]>(`${this.apiUrl}/face/descriptors`);
  }

  deleteProfile(userId: number): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/face/${userId}`);
  }

  faceCheckin(userId: number): Observable<FaceCheckinResponseDTO> {
    return this.http.post<FaceCheckinResponseDTO>(`${this.apiUrl}/attendance/face-checkin`, { userId });
  }

  manualCheckin(userId: number): Observable<FaceCheckinResponseDTO> {
    return this.http.post<FaceCheckinResponseDTO>(`${this.apiUrl}/attendance/manual-checkin/${userId}`, {});
  }

  getTodayAttendance(): Observable<FaceCheckinResponseDTO[]> {
    return this.http.get<FaceCheckinResponseDTO[]>(`${this.apiUrl}/attendance/today`);
  }

  getAllAttendance(from?: string, to?: string, role?: string, name?: string): Observable<FaceCheckinResponseDTO[]> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    if (role && role !== 'ALL') params = params.set('role', role);
    if (name) params = params.set('name', name);
    return this.http.get<FaceCheckinResponseDTO[]>(`${this.apiUrl}/attendance/all`, { params });
  }

  getMyAttendance(): Observable<FaceCheckinResponseDTO[]> {
    return this.http.get<FaceCheckinResponseDTO[]>(`${this.apiUrl}/attendance/my`);
  }
}
