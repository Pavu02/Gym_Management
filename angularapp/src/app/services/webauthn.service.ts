import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  detectDeviceMetadata,
  isFingerprintSupported,
  isWebAuthnSupported,
  mapWebAuthnClientError,
  prepareCreationOptions,
  prepareRequestOptions,
  serializeAssertionCredential,
  serializeRegistrationCredential
} from '../utils/webauthn.util';

export interface RegisteredDeviceDTO {
  id: number;
  deviceName: string;
  deviceType: string;
  transports?: string;
  registeredAt: string;
  lastUsedAt?: string;
  active: boolean;
  revokedAt?: string;
}

export interface RegisterOptionsResponse {
  challengeId: string;
  publicKeyCredentialCreationOptions: string;
  expiresInSeconds: number;
  reauthVerified: boolean;
}

export interface AuthOptionsResponse {
  challengeId: string;
  sessionCode: string;
  publicKeyCredentialRequestOptions: string;
  expiresInSeconds: number;
  allowedDeviceCount: number;
}

export interface CheckInVerificationResponse {
  status: string;
  message: string;
  userVerificationMethod: string;
  memberId: number;
  memberName: string;
  memberPhotoUrl?: string;
  planName: string;
  membershipStatus: string;
  expiryDate?: string;
  checkInTime: string;
  checkInTimestamp: string;
  checkInMethod: string;
  sessionCode?: string;
  riskResult?: string;
  idempotencyKey?: string;
}

@Injectable({
  providedIn: 'root'
})
export class WebAuthnService {
  private readonly baseUrl = `${environment.apiUrl}/webauthn`;

  constructor(private http: HttpClient) {}

  isSupported(): boolean {
    return isWebAuthnSupported();
  }

  async isPlatformAuthenticatorAvailable(): Promise<boolean> {
    return isFingerprintSupported();
  }

  detectDevice(): { deviceName: string; deviceType: string } {
    return detectDeviceMetadata();
  }

  getRegistrationOptions(deviceName: string, deviceType: string, currentPassword?: string): Observable<RegisterOptionsResponse> {
    return this.http.post<RegisterOptionsResponse>(`${this.baseUrl}/register/options`, {
      deviceName,
      deviceType,
      currentPassword: currentPassword || null
    });
  }

  verifyRegistration(payload: {
    challengeId: string;
    credentialJson: string;
    deviceName: string;
    deviceType: string;
    transports?: string[];
    consent: boolean;
  }): Observable<RegisteredDeviceDTO> {
    return this.http.post<RegisteredDeviceDTO>(`${this.baseUrl}/register/verify`, payload);
  }

  async enrollCurrentDevice(deviceName: string, consent: boolean, currentPassword?: string): Promise<RegisteredDeviceDTO> {
    if (!this.isSupported()) {
      throw new Error('This phone or browser does not support WebAuthn passkeys.');
    }

    const detected = this.detectDevice();
    const cleanName = (deviceName || '').trim() || detected.deviceName;
    const optionsRes = await firstValueFrom(this.getRegistrationOptions(cleanName, detected.deviceType, currentPassword));

    let credential: PublicKeyCredential | null;
    try {
      const creationOptions = prepareCreationOptions(optionsRes.publicKeyCredentialCreationOptions);
      credential = (await navigator.credentials.create(creationOptions)) as PublicKeyCredential | null;
    } catch (err) {
      throw new Error(mapWebAuthnClientError(err, 'registration'));
    }

    if (!credential) {
      throw new Error('Biometric registration was cancelled.');
    }

    const serialized = serializeRegistrationCredential(credential);
    const transports: string[] = Array.isArray(serialized?.response?.transports)
      ? serialized.response.transports
      : ['internal'];

    return firstValueFrom(
      this.verifyRegistration({
        challengeId: optionsRes.challengeId,
        credentialJson: JSON.stringify(serialized),
        deviceName: cleanName,
        deviceType: detected.deviceType,
        transports,
        consent
      })
    );
  }

  getMyDevices(): Observable<RegisteredDeviceDTO[]> {
    return this.http.get<RegisteredDeviceDTO[]>(`${this.baseUrl}/devices`);
  }

  renameDevice(deviceId: number, deviceName: string): Observable<RegisteredDeviceDTO> {
    return this.http.patch<RegisteredDeviceDTO>(`${this.baseUrl}/devices/${deviceId}/rename`, { deviceName });
  }

  revokeDevice(deviceId: number): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/devices/${deviceId}`);
  }

  revokeAllDevices(currentPassword: string): Observable<{ revokedCount: number; message: string }> {
    return this.http.post<{ revokedCount: number; message: string }>(`${this.baseUrl}/devices/revoke-all`, {
      currentPassword
    });
  }

  getAuthenticationOptions(qrToken: string, idempotencyKey?: string): Observable<AuthOptionsResponse> {
    return this.http.post<AuthOptionsResponse>(`${this.baseUrl}/authenticate/options`, {
      qrToken,
      idempotencyKey
    });
  }

  prepareAssertionOptions(optionsJson: string): CredentialRequestOptions {
    return prepareRequestOptions(optionsJson);
  }

  serializeAssertion(credential: PublicKeyCredential): string {
    return JSON.stringify(serializeAssertionCredential(credential));
  }

  verifyAuthentication(payload: {
    challengeId: string;
    qrToken: string;
    credentialJson: string;
    idempotencyKey: string;
    latitude?: number;
    longitude?: number;
    wifiSsid?: string;
    authenticatorAttachment?: string | null;
    verificationHint?: string;
    fallbackMethod?: string;
    otpCode?: string;
  }): Observable<CheckInVerificationResponse> {
    return this.http.post<CheckInVerificationResponse>(`${this.baseUrl}/authenticate/verify`, payload);
  }
}
