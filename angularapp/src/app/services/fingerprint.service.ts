    import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface FingerprintDeviceDTO {
  id: number;
  deviceName: string;
  createdAt: string;
  lastUsedAt?: string;
}

export interface RegisterStartResponseDTO {
  requestId: string;
  publicKeyCredentialCreationOptions: string;
}

export interface CheckinStartResponseDTO {
  requestId: string;
  publicKeyCredentialRequestOptions: string;
}

export interface CheckinResultDTO {
  action: 'CHECK_IN' | 'CHECK_OUT';
  time: string;
  memberName: string;
  planName: string;
  expiryDate: string;
  daysLeft: number;
  status: string;
}

export interface FingerprintStatusDTO {
  enrolled: boolean;
  userId?: number;
  fingerprintHash?: string;
  qualityScore?: number;
  deviceName?: string;
  enrolledAt?: string;
}

@Injectable({
  providedIn: 'root'
})
export class FingerprintService {
  private apiUrl = `${environment.apiUrl}/fingerprint`;

  constructor(private http: HttpClient) { }

  // WebAuthn Passkey Registration & Assertion (Phases 1-5)
  startRegistration(): Observable<RegisterStartResponseDTO> {
    return this.http.post<RegisterStartResponseDTO>(`${this.apiUrl}/register/start`, {});
  }

  finishRegistration(requestId: string, credential: string, deviceName: string = 'My Phone'): Observable<FingerprintDeviceDTO> {
    return this.http.post<FingerprintDeviceDTO>(`${this.apiUrl}/register/finish`, {
      requestId,
      credential,
      deviceName
    });
  }

  getMyDevices(): Observable<FingerprintDeviceDTO[]> {
    return this.http.get<FingerprintDeviceDTO[]>(`${this.apiUrl}/me/devices`);
  }

  deleteMyDevice(id: number): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.apiUrl}/me/devices/${id}`);
  }

  getMemberDevices(memberId: number): Observable<FingerprintDeviceDTO[]> {
    return this.http.get<FingerprintDeviceDTO[]>(`${this.apiUrl}/member/${memberId}/devices`);
  }

  adminDeleteDevice(id: number): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.apiUrl}/devices/${id}`);
  }

  startCheckin(kioskToken: string): Observable<CheckinStartResponseDTO> {
    return this.http.post<CheckinStartResponseDTO>(`${this.apiUrl}/checkin/start`, { kioskToken });
  }

  finishCheckin(requestId: string, kioskToken: string, credential: string): Observable<CheckinResultDTO> {
    return this.http.post<CheckinResultDTO>(`${this.apiUrl}/checkin/finish`, {
      requestId,
      kioskToken,
      credential
    });
  }

  // Optical / Direct Status & Sensor Endpoints (Backward compatibility)
  getMyStatus(): Observable<FingerprintStatusDTO> {
    return this.http.get<FingerprintStatusDTO>(`${this.apiUrl}/status`);
  }

  enrollFingerprint(userId: number, payload: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/enroll/${userId}`, payload);
  }

  deleteFingerprint(userId: number): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/enroll/${userId}`);
  }

  checkin(payload: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/checkin/manual`, payload);
  }
}
