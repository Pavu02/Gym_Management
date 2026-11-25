import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CheckinResultDTO } from './fingerprint.service';
import { environment } from '../../environments/environment';

export interface KioskTokenResponseDTO {
  token: string;
  expiresAt: string;
  qrUrl: string;
}

export interface RecentCheckinDTO {
  memberName: string;
  memberInitials: string;
  planName: string;
  expiryDate?: string;
  status: string;
  daysLeft: number;
  checkInMethod: string;
  time: string;
  action: 'CHECK_IN' | 'CHECK_OUT';
}

@Injectable({
  providedIn: 'root'
})
export class KioskService {
  private apiUrl = `${environment.apiUrl}/kiosk`;

  constructor(private http: HttpClient) {}

  createToken(): Observable<KioskTokenResponseDTO> {
    return this.http.post<KioskTokenResponseDTO>(`${this.apiUrl}/token`, {});
  }

  getQrImageBlob(token: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/token/${token}/qr`, {
      responseType: 'blob'
    });
  }

  getRecentCheckins(since?: string): Observable<RecentCheckinDTO[]> {
    let params = new HttpParams();
    if (since) {
      params = params.set('since', since);
    }
    return this.http.get<RecentCheckinDTO[]>(`${this.apiUrl}/recent-checkins`, { params });
  }

  simulateCheckin(memberId: number): Observable<CheckinResultDTO> {
    return this.http.post<CheckinResultDTO>(`${this.apiUrl}/simulate/${memberId}`, {});
  }
}
