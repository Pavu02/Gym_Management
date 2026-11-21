package com.gym.springapp.service.fingerprint;

import org.springframework.stereotype.Component;

@Component
public class WebAuthnFingerprintProvider implements FingerprintProvider {

    @Override
    public String getProviderType() {
        return "WEBAUTHN";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
