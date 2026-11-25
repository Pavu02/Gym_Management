package com.gym.springapp.service;

import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebAuthnChallengeStore {

    private static final long EXPIRATION_SECONDS = 120; // 2 minutes

    private record Entry<T>(T value, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final Map<String, Entry<PublicKeyCredentialCreationOptions>> registrationRequests = new ConcurrentHashMap<>();
    private final Map<String, Entry<AssertionRequest>> assertionRequests = new ConcurrentHashMap<>();

    public void putRegistration(String requestId, PublicKeyCredentialCreationOptions options) {
        registrationRequests.put(requestId, new Entry<>(options, Instant.now().plusSeconds(EXPIRATION_SECONDS)));
    }

    public PublicKeyCredentialCreationOptions getRegistration(String requestId) {
        Entry<PublicKeyCredentialCreationOptions> entry = registrationRequests.get(requestId);
        if (entry == null || entry.isExpired()) {
            registrationRequests.remove(requestId);
            return null;
        }
        return entry.value();
    }

    public PublicKeyCredentialCreationOptions removeRegistration(String requestId) {
        Entry<PublicKeyCredentialCreationOptions> entry = registrationRequests.remove(requestId);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.value();
    }

    public void putAssertion(String requestId, AssertionRequest request) {
        assertionRequests.put(requestId, new Entry<>(request, Instant.now().plusSeconds(EXPIRATION_SECONDS)));
    }

    public AssertionRequest getAssertion(String requestId) {
        Entry<AssertionRequest> entry = assertionRequests.get(requestId);
        if (entry == null || entry.isExpired()) {
            assertionRequests.remove(requestId);
            return null;
        }
        return entry.value();
    }

    public AssertionRequest removeAssertion(String requestId) {
        Entry<AssertionRequest> entry = assertionRequests.remove(requestId);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.value();
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupExpired() {
        registrationRequests.entrySet().removeIf(e -> e.getValue().isExpired());
        assertionRequests.entrySet().removeIf(e -> e.getValue().isExpired());
    }
}
