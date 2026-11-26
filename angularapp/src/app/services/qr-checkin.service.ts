import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CheckInVerificationResponse } from './webauthn.service';

export interface QrSessionResponse {
  sessionCode: string;
  token: string;
  qrUrl: string;
  qrCodeDataUrl: string;
  gymId: string;
  branchId: string;
  status: string;
  createdAt: string;
  expiresAt: string;
  serverTime: string;
  expiresInSeconds: number;
}

export interface QrSessionValidateResponse {
  valid: boolean;
  sessionCode: string;
  gymId: string;
  branchId: string;
  status: string;
  expiresAt: string;
  serverTime: string;
  remainingSeconds: number;
  memberId: number;
  memberName: string;
  planName: string;
  membershipStatus: string;
  hasRegisteredDevices: boolean;
  deviceCount: number;
  locationPolicy: string;
  riskResult: string;
  message: string;
}

export interface CheckInLiveEvent {
  sessionCode: string;
  status: string;
  eventType: string;
  memberId?: number;
  memberName?: string;
  memberInitials?: string;
  memberPhotoUrl?: string;
  planName?: string;
  membershipStatus?: string;
  expiryDate?: string;
  checkInTime?: string;
  checkInTimestamp?: string;
  checkInMethod?: string;
  verificationResult?: string;
  riskResult?: string;
  failureReason?: string;
  branchId?: string;
  timestamp: string;
}

@Injectable({
  providedIn: 'root'
})
export class QrCheckInService {
  private readonly baseUrl = `${environment.apiUrl}/qr-checkin`;

  constructor(private http: HttpClient) {}

  createSession(gymId?: string, branchId?: string): Observable<QrSessionResponse> {
    return this.http.post<QrSessionResponse>(`${this.baseUrl}/sessions`, {
      gymId: gymId || null,
      branchId: branchId || null
    });
  }

  getSessionStatus(sessionCode: string): Observable<CheckInLiveEvent> {
    return this.http.get<CheckInLiveEvent>(`${this.baseUrl}/sessions/${sessionCode}`);
  }

  cancelSession(sessionCode: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/sessions/${sessionCode}`);
  }

  adminManualCheckIn(sessionCode: string | null, memberId: number, reason: string): Observable<CheckInVerificationResponse> {
    return this.http.post<CheckInVerificationResponse>(`${this.baseUrl}/manual-checkin`, {
      sessionCode,
      memberId,
      reason
    });
  }

  validateSession(payload: {
    token: string;
    clientTimestamp?: string;
    latitude?: number;
    longitude?: number;
    wifiSsid?: string;
  }): Observable<QrSessionValidateResponse> {
    return this.http.post<QrSessionValidateResponse>(`${this.baseUrl}/sessions/validate`, payload);
  }

  confirmByIdempotencyKey(idempotencyKey: string): Observable<CheckInVerificationResponse> {
    const params = new HttpParams().set('idempotencyKey', idempotencyKey);
    return this.http.get<CheckInVerificationResponse>(`${this.baseUrl}/sessions/member-status`, { params });
  }

  async tryGetCurrentPosition(timeoutMs = 3000): Promise<{ latitude: number; longitude: number } | null> {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      return null;
    }
    return new Promise((resolve) => {
      const timer = setTimeout(() => resolve(null), timeoutMs + 200);
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          clearTimeout(timer);
          resolve({
            latitude: pos.coords.latitude,
            longitude: pos.coords.longitude
          });
        },
        () => {
          clearTimeout(timer);
          resolve(null);
        },
        { enableHighAccuracy: false, timeout: timeoutMs, maximumAge: 60000 }
      );
    });
  }
}
