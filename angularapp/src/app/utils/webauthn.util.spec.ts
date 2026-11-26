import {
  arrayBufferToBase64Url,
  base64UrlToArrayBuffer,
  mapWebAuthnClientError,
  prepareCreationOptions,
  prepareRequestOptions,
  serializeAssertionCredential,
  serializeRegistrationCredential
} from './webauthn.util';

describe('WebAuthn Base64URL & Serialization Utilities', () => {
  it('converts ArrayBuffer to Base64URL and back losslessly including URL-safe characters', () => {
    const raw = new Uint8Array([0xfb, 0xff, 0xbf, 0x00, 0x10, 0x7f, 0xfe, 0x3f]);
    const b64url = arrayBufferToBase64Url(raw.buffer);
    expect(b64url).not.toContain('+');
    expect(b64url).not.toContain('/');
    expect(b64url).not.toContain('=');

    const decoded = new Uint8Array(base64UrlToArrayBuffer(b64url));
    expect(Array.from(decoded)).toEqual(Array.from(raw));
  });

  it('handles Uint8Array slice views accurately during ArrayBuffer -> Base64URL conversion', () => {
    const backing = new Uint8Array([1, 2, 3, 4, 5, 6]);
    const slice = backing.subarray(2, 5); // [3, 4, 5]
    const b64url = arrayBufferToBase64Url(slice);
    const roundTrip = new Uint8Array(base64UrlToArrayBuffer(b64url));
    expect(Array.from(roundTrip)).toEqual([3, 4, 5]);
  });

  it('converts WebAuthn creation and request option fields to ArrayBuffer', () => {
    const creationJson = JSON.stringify({
      challenge: 'AQIDBA',
      user: { id: 'BQYHCA', name: 'member', displayName: 'Member' },
      excludeCredentials: [{ id: 'CQoLDA', type: 'public-key' }]
    });
    const created = prepareCreationOptions(creationJson);
    expect(created.publicKey?.challenge instanceof ArrayBuffer).toBeTrue();
    expect(created.publicKey?.user.id instanceof ArrayBuffer).toBeTrue();
    expect(created.publicKey?.excludeCredentials?.[0].id instanceof ArrayBuffer).toBeTrue();

    const requestJson = JSON.stringify({
      publicKey: {
        challenge: 'DQ4PEA',
        allowCredentials: [{ id: 'ESESFA', type: 'public-key' }]
      }
    });
    const req = prepareRequestOptions(requestJson);
    expect(req.publicKey?.challenge instanceof ArrayBuffer).toBeTrue();
    expect(req.publicKey?.allowCredentials?.[0].id instanceof ArrayBuffer).toBeTrue();
  });

  it('serializes attestation and assertion PublicKeyCredential fields into Base64URL strings', () => {
    const regCred: any = {
      id: 'cred-1',
      rawId: new Uint8Array([1, 2, 3]).buffer,
      type: 'public-key',
      authenticatorAttachment: 'platform',
      response: {
        clientDataJSON: new Uint8Array([4, 5]).buffer,
        attestationObject: new Uint8Array([6, 7]).buffer,
        getTransports: () => ['internal']
      },
      getClientExtensionResults: () => ({})
    };
    const serializedReg = serializeRegistrationCredential(regCred);
    expect(serializedReg.rawId).toBe(arrayBufferToBase64Url(new Uint8Array([1, 2, 3])));
    expect(serializedReg.response.attestationObject).toBe(arrayBufferToBase64Url(new Uint8Array([6, 7])));
    expect(serializedReg.response.transports).toEqual(['internal']);

    const authCred: any = {
      id: 'cred-1',
      rawId: new Uint8Array([1, 2, 3]).buffer,
      type: 'public-key',
      authenticatorAttachment: 'platform',
      response: {
        clientDataJSON: new Uint8Array([8, 9]).buffer,
        authenticatorData: new Uint8Array([10, 11]).buffer,
        signature: new Uint8Array([12, 13]).buffer,
        userHandle: new Uint8Array([14, 15]).buffer
      },
      getClientExtensionResults: () => ({})
    };
    const serializedAuth = serializeAssertionCredential(authCred);
    expect(serializedAuth.response.authenticatorData).toBe(arrayBufferToBase64Url(new Uint8Array([10, 11])));
    expect(serializedAuth.response.signature).toBe(arrayBufferToBase64Url(new Uint8Array([12, 13])));
    expect(serializedAuth.response.userHandle).toBe(arrayBufferToBase64Url(new Uint8Array([14, 15])));
  });

  it('maps cancellation errors accurately for registration and authentication', () => {
    const err = { name: 'NotAllowedError', message: 'User cancelled' };
    expect(mapWebAuthnClientError(err, 'authentication')).toContain('cancelled');
    expect(mapWebAuthnClientError(err, 'registration')).toContain('cancelled');
  });
});
