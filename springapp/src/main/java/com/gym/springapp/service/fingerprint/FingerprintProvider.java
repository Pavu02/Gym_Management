package com.gym.springapp.service.fingerprint;

public interface FingerprintProvider {
    String getProviderType();
    boolean isAvailable();
}
