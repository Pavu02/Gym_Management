import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Attendance } from '../models/attendance.model';

@Injectable({
  providedIn: 'root'
})
export class AttendanceService {
  apiUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  checkIn(memberId: number): Observable<Attendance> {
    return this.http.post<Attendance>(`${this.apiUrl}/attendance/check-in/${memberId}`, {});
  }

  checkOut(memberId: number): Observable<Attendance> {
    return this.http.post<Attendance>(`${this.apiUrl}/attendance/check-out/${memberId}`, {});
  }

  checkInMe(): Observable<Attendance> {
    return this.http.post<Attendance>(`${this.apiUrl}/attendance/me/check-in`, {});
  }

  checkOutMe(): Observable<Attendance> {
    return this.http.post<Attendance>(`${this.apiUrl}/attendance/me/check-out`, {});
  }

  getAllAttendance(): Observable<Attendance[]> {
    return this.http.get<Attendance[]>(`${this.apiUrl}/attendance`);
  }

  getAttendanceByMember(memberId: number): Observable<Attendance[]> {
    return this.http.get<Attendance[]>(`${this.apiUrl}/attendance/member/${memberId}`);
  }

  getMyAttendance(): Observable<Attendance[]> {
    return this.http.get<Attendance[]>(`${this.apiUrl}/attendance/me`);
  }
}
