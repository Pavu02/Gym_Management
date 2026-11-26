export function base64UrlToArrayBuffer(base64Url: string): ArrayBuffer {
  if (!base64Url || typeof base64Url !== 'string') {
    throw new Error('Invalid Base64URL string');
  }
  const normalized = base64Url.trim().replace(/-/g, '+').replace(/_/g, '/');
  const padLen = (4 - (normalized.length % 4)) % 4;
  const padded = normalized + '='.repeat(padLen);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

export function arrayBufferToBase64Url(buffer: ArrayBuffer | ArrayBufferView): string {
  const bytes = buffer instanceof ArrayBuffer
    ? new Uint8Array(buffer)
    : new Uint8Array(buffer.buffer, buffer.byteOffset, buffer.byteLength);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

// Backward-compatible aliases
export const base64UrlToBuffer = base64UrlToArrayBuffer;
export const bufferToBase64Url = arrayBufferToBase64Url;

export function isWebAuthnSupported(): boolean {
  return typeof window !== 'undefined'
    && typeof window.PublicKeyCredential === 'function'
    && typeof navigator !== 'undefined'
    && !!navigator.credentials
    && typeof navigator.credentials.create === 'function'
    && typeof navigator.credentials.get === 'function';
}

export async function isFingerprintSupported(): Promise<boolean> {
  if (!isWebAuthnSupported()) {
    return false;
  }
  if (typeof PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable !== 'function') {
    return false;
  }
  try {
    return await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
  } catch {
    return false;
  }
}

export function detectDeviceMetadata(): { deviceName: string; deviceType: string } {
  if (typeof navigator === 'undefined') {
    return { deviceName: 'My Phone', deviceType: 'MOBILE_PHONE' };
  }
  const ua = navigator.userAgent || '';
  if (/iPhone/i.test(ua)) {
    return { deviceName: 'My iPhone', deviceType: 'IPHONE' };
  }
  if (/iPad/i.test(ua)) {
    return { deviceName: 'My iPad', deviceType: 'IPAD' };
  }
  if (/Android/i.test(ua)) {
    return { deviceName: 'My Android Phone', deviceType: 'ANDROID_PHONE' };
  }
  if (/Macintosh/i.test(ua)) {
    return { deviceName: 'My Mac (Touch ID)', deviceType: 'LAPTOP' };
  }
  if (/Windows/i.test(ua)) {
    return { deviceName: 'My Windows PC', deviceType: 'LAPTOP' };
  }
  return { deviceName: 'My Device', deviceType: 'MOBILE_PHONE' };
}

export function prepareCreationOptions(optionsJson: string): CredentialCreationOptions {
  const parsed = JSON.parse(optionsJson);
  const publicKey = parsed.publicKey ? { ...parsed.publicKey } : { ...parsed };

  if (publicKey.challenge && typeof publicKey.challenge === 'string') {
    publicKey.challenge = base64UrlToArrayBuffer(publicKey.challenge);
  }
  if (publicKey.user && publicKey.user.id && typeof publicKey.user.id === 'string') {
    publicKey.user = {
      ...publicKey.user,
      id: base64UrlToArrayBuffer(publicKey.user.id)
    };
  }
  if (Array.isArray(publicKey.excludeCredentials)) {
    publicKey.excludeCredentials = publicKey.excludeCredentials.map((cred: any) => ({
      ...cred,
      id: typeof cred.id === 'string' ? base64UrlToArrayBuffer(cred.id) : cred.id
    }));
  }

  return { publicKey };
}

export function prepareRequestOptions(optionsJson: string): CredentialRequestOptions {
  const parsed = JSON.parse(optionsJson);
  const publicKey = parsed.publicKey ? { ...parsed.publicKey } : { ...parsed };

  if (publicKey.challenge && typeof publicKey.challenge === 'string') {
    publicKey.challenge = base64UrlToArrayBuffer(publicKey.challenge);
  }
  if (Array.isArray(publicKey.allowCredentials)) {
    publicKey.allowCredentials = publicKey.allowCredentials.map((cred: any) => ({
      ...cred,
      id: typeof cred.id === 'string' ? base64UrlToArrayBuffer(cred.id) : cred.id
    }));
  }

  return { publicKey };
}

export function serializeRegistrationCredential(credential: PublicKeyCredential): any {
  const response = credential.response as AuthenticatorAttestationResponse;
  const transports = typeof (response as any).getTransports === 'function'
    ? (response as any).getTransports()
    : undefined;

  return {
    id: credential.id,
    rawId: arrayBufferToBase64Url(credential.rawId),
    type: credential.type,
    authenticatorAttachment: (credential as any).authenticatorAttachment || null,
    response: {
      clientDataJSON: arrayBufferToBase64Url(response.clientDataJSON),
      attestationObject: arrayBufferToBase64Url(response.attestationObject),
      ...(transports ? { transports } : {})
    },
    clientExtensionResults: credential.getClientExtensionResults ? credential.getClientExtensionResults() : {}
  };
}

export function serializeAssertionCredential(credential: PublicKeyCredential): any {
  const response = credential.response as AuthenticatorAssertionResponse;
  return {
    id: credential.id,
    rawId: arrayBufferToBase64Url(credential.rawId),
    type: credential.type,
    authenticatorAttachment: (credential as any).authenticatorAttachment || null,
    response: {
      clientDataJSON: arrayBufferToBase64Url(response.clientDataJSON),
      authenticatorData: arrayBufferToBase64Url(response.authenticatorData),
      signature: arrayBufferToBase64Url(response.signature),
      userHandle: response.userHandle ? arrayBufferToBase64Url(response.userHandle) : null
    },
    clientExtensionResults: credential.getClientExtensionResults ? credential.getClientExtensionResults() : {}
  };
}

export function publicKeyCredentialToJson(credential: PublicKeyCredential): any {
  if ('attestationObject' in credential.response) {
    return serializeRegistrationCredential(credential);
  }
  return serializeAssertionCredential(credential);
}

export function mapWebAuthnClientError(err: any, mode: 'registration' | 'authentication'): string {
  const name = err?.name || '';
  const msg = (err?.error?.message || err?.message || '').toString();
  const lower = msg.toLowerCase();

  if (name === 'InvalidStateError') {
    return mode === 'registration'
      ? 'This device is already registered for biometric check-in.'
      : 'Your registered passkey is unavailable on this phone. Register this device again or request admin assistance.';
  }
  if (name === 'SecurityError' || lower.includes('relying party') || lower.includes('origin')) {
    return 'Security verification failed: domain or HTTPS origin does not match the configured gym app.';
  }
  if (name === 'AbortError' || lower.includes('timeout') || lower.includes('timed out')) {
    return 'Verification timed out. Please tap Try Again and verify using your phone sensor or screen lock.';
  }
  if (name === 'NotAllowedError' || lower.includes('cancelled') || lower.includes('not allowed')) {
    if (mode === 'authentication') {
      return 'Fingerprint verification was cancelled. Please try again.';
    }
    return 'Biometric setup was cancelled, timed out, or this device has no screen lock / fingerprint enabled.';
  }
  if (lower.includes('no credentials') || lower.includes('not found')) {
    return 'Your registered passkey is unavailable. Register this device again or request admin assistance.';
  }
  return msg || (mode === 'registration'
    ? 'Unable to register biometric check-in on this device.'
    : 'Biometric verification failed on this device.');
}
