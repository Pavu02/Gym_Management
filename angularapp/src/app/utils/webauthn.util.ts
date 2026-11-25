export function base64UrlToBuffer(base64Url: string): ArrayBuffer {
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

export function bufferToBase64Url(buffer: ArrayBuffer): string {
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

export async function isFingerprintSupported(): Promise<boolean> {
  if (typeof window === 'undefined' || !window.PublicKeyCredential) {
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

export function prepareCreationOptions(optionsJson: string): CredentialCreationOptions {
  const options = JSON.parse(optionsJson);

  if (options.challenge) {
    options.challenge = base64UrlToBuffer(options.challenge);
  }
  if (options.user && options.user.id) {
    options.user.id = base64UrlToBuffer(options.user.id);
  }
  if (options.excludeCredentials && Array.isArray(options.excludeCredentials)) {
    for (const cred of options.excludeCredentials) {
      if (cred.id) {
        cred.id = base64UrlToBuffer(cred.id);
      }
    }
  }

  return { publicKey: options };
}

export function prepareRequestOptions(optionsJson: string): CredentialRequestOptions {
  const options = JSON.parse(optionsJson);

  if (options.challenge) {
    options.challenge = base64UrlToBuffer(options.challenge);
  }
  if (options.allowCredentials && Array.isArray(options.allowCredentials)) {
    for (const cred of options.allowCredentials) {
      if (cred.id) {
        cred.id = base64UrlToBuffer(cred.id);
      }
    }
  }

  return { publicKey: options };
}

export function publicKeyCredentialToJson(credential: PublicKeyCredential): any {
  const rawId = bufferToBase64Url(credential.rawId);

  if ('attestationObject' in credential.response) {
    // Registration
    const response = credential.response as AuthenticatorAttestationResponse;
    return {
      id: credential.id,
      rawId,
      type: credential.type,
      response: {
        clientDataJSON: bufferToBase64Url(response.clientDataJSON),
        attestationObject: bufferToBase64Url(response.attestationObject)
      },
      clientExtensionResults: credential.getClientExtensionResults ? credential.getClientExtensionResults() : {}
    };
  } else {
    // Assertion (checkin)
    const response = credential.response as AuthenticatorAssertionResponse;
    return {
      id: credential.id,
      rawId,
      type: credential.type,
      response: {
        clientDataJSON: bufferToBase64Url(response.clientDataJSON),
        authenticatorData: bufferToBase64Url(response.authenticatorData),
        signature: bufferToBase64Url(response.signature),
        userHandle: response.userHandle ? bufferToBase64Url(response.userHandle) : null
      },
      clientExtensionResults: credential.getClientExtensionResults ? credential.getClientExtensionResults() : {}
    };
  }
}
