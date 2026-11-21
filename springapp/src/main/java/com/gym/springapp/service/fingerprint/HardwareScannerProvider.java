package com.gym.springapp.service.fingerprint;

import org.springframework.stereotype.Component;

/**
 * Stub implementation for future USB/Hardware Fingerprint Scanners (e.g. DigitalPersona, SecuGen).
 */
@Component
public class HardwareScannerProvider implements FingerprintProvider {

    @Override
    public String getProviderType() {
        return "HARDWARE_USB";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
