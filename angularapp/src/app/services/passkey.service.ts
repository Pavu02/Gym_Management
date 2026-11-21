import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface WebAuthnDevice {
  id: number;
  credentialId: string;
  deviceName: string;
  signCount: number;
  createdAt: string;
}

export interface PasskeyRegistrationOptionsResponse {
  rpId: string;
  rpName: string;
  challenge: string;
  userId: string;
  userName: string;
  optionsJson: string;
}

export interface PasskeyAssertionOptionsResponse {
  challenge: string;
  rpId: string;
  optionsJson: string;
}

@Injectable({
  providedIn: 'root'
})
export class PasskeyService {
  private memberApiUrl = 'http://localhost:8080/api/member/passkey';
  private checkinApiUrl = 'http://localhost:8080/api/checkin/passkey';

  constructor(private http: HttpClient) {}

  isWebAuthnSupported(): boolean {
    return typeof window !== 'undefined' &&
      window.PublicKeyCredential !== undefined &&
      typeof window.PublicKeyCredential === 'function';
  }

  // Base64URL to ArrayBuffer
  private base64UrlToBuffer(base64Url: string): ArrayBuffer {
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const padLen = (4 - (base64.length % 4)) % 4;
    const padded = base64 + '='.repeat(padLen);
    const binary = atob(padded);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes.buffer;
  }

  // ArrayBuffer to Base64URL
  private bufferToBase64Url(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary)
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=/g, '');
  }

  async registerPasskey(deviceName: string = 'My Passkey Device'): Promise<WebAuthnDevice> {
    if (!this.isWebAuthnSupported()) {
      throw new Error('WebAuthn fingerprint passkeys are not supported in this browser.');
    }

    // 1. Request options from server
    const optionsRes = await this.http
      .post<PasskeyRegistrationOptionsResponse>(`${this.memberApiUrl}/register/options`, {})
      .toPromise();

    if (!optionsRes) {
      throw new Error('Failed to retrieve passkey registration challenge from server.');
    }

    const creationOptions = JSON.parse(optionsRes.optionsJson);

    // Convert challenge and user.id to ArrayBuffer
    creationOptions.challenge = this.base64UrlToBuffer(creationOptions.challenge);
    if (creationOptions.user && creationOptions.user.id) {
      creationOptions.user.id = this.base64UrlToBuffer(creationOptions.user.id);
    }
    if (creationOptions.excludeCredentials) {
      for (const cred of creationOptions.excludeCredentials) {
        cred.id = this.base64UrlToBuffer(cred.id);
      }
    }

    // 2. Invoke browser WebAuthn API
    const credential = (await navigator.credentials.create({
      publicKey: creationOptions
    })) as PublicKeyCredential;

    if (!credential) {
      throw new Error('Passkey creation cancelled or failed.');
    }

    const response = credential.response as AuthenticatorAttestationResponse;

    // 3. Serialize response for server verification
    const registrationResponse = {
      id: credential.id,
      rawId: this.bufferToBase64Url(credential.rawId),
      type: credential.type,
      response: {
        clientDataJSON: this.bufferToBase64Url(response.clientDataJSON),
        attestationObject: this.bufferToBase64Url(response.attestationObject)
      },
      clientExtensionResults: credential.getClientExtensionResults()
    };

    // 4. Send to server for verification and storage
    const verifyRes = await this.http
      .post<WebAuthnDevice>(`${this.memberApiUrl}/register/verify`, {
        deviceName,
        registrationResponseJson: JSON.stringify(registrationResponse)
      })
      .toPromise();

    return verifyRes!;
  }

  async authenticatePasskey(email?: string): Promise<any> {
    if (!this.isWebAuthnSupported()) {
      throw new Error('WebAuthn fingerprint passkeys are not supported in this browser.');
    }

    // 1. Request assertion options from server
    const query = email ? `?email=${encodeURIComponent(email)}` : '';
    const optionsRes = await this.http
      .post<PasskeyAssertionOptionsResponse>(`${this.checkinApiUrl}/options${query}`, {})
      .toPromise();

    if (!optionsRes) {
      throw new Error('Failed to retrieve passkey authentication challenge.');
    }

    const requestOptions = JSON.parse(optionsRes.optionsJson);
    requestOptions.challenge = this.base64UrlToBuffer(requestOptions.challenge);

    if (requestOptions.allowCredentials) {
      for (const cred of requestOptions.allowCredentials) {
        cred.id = this.base64UrlToBuffer(cred.id);
      }
    }

    // 2. Invoke browser WebAuthn assertion
    const assertion = (await navigator.credentials.get({
      publicKey: requestOptions
    })) as PublicKeyCredential;

    if (!assertion) {
      throw new Error('Fingerprint authentication was cancelled.');
    }

    const response = assertion.response as AuthenticatorAssertionResponse;

    const assertionResponse = {
      id: assertion.id,
      rawId: this.bufferToBase64Url(assertion.rawId),
      type: assertion.type,
      response: {
        clientDataJSON: this.bufferToBase64Url(response.clientDataJSON),
        authenticatorData: this.bufferToBase64Url(response.authenticatorData),
        signature: this.bufferToBase64Url(response.signature),
        userHandle: response.userHandle ? this.bufferToBase64Url(response.userHandle) : null
      },
      clientExtensionResults: assertion.getClientExtensionResults()
    };

    // 3. Verify assertion on server
    const verifyRes = await this.http
      .post<any>(`${this.checkinApiUrl}/verify`, {
        assertionResponseJson: JSON.stringify(assertionResponse)
      })
      .toPromise();

    return verifyRes;
  }

  getDevices(): Observable<WebAuthnDevice[]> {
    return this.http.get<WebAuthnDevice[]>(`${this.memberApiUrl}/devices`);
  }

  deleteDevice(credentialId: string): Observable<void> {
    return this.http.delete<void>(`${this.memberApiUrl}/${encodeURIComponent(credentialId)}`);
  }
}
