package com.gym.springapp.config;

import com.gym.springapp.service.BiometricCredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Configuration
public class WebAuthnConfig {

    @Value("${webauthn.rp-id:${webauthn.rp.id:localhost}}")
    private String rpId;

    @Value("${webauthn.rp-name:${webauthn.rp.name:BeastX Gym}}")
    private String rpName;

    @Value("${webauthn.origins:${webauthn.origin:http://localhost:4200,http://localhost:8081}}")
    private String originsProperty;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${app.backend-url:http://localhost:8081}")
    private String backendUrl;

    public String getEffectiveRpId() {
        String effectiveRpId = rpId;
        if ("localhost".equalsIgnoreCase(effectiveRpId) && frontendUrl != null && !frontendUrl.isBlank()) {
            try {
                String firstUrl = frontendUrl.split(",")[0].trim();
                URI uri = URI.create(firstUrl);
                if (uri.getHost() != null
                        && !uri.getHost().equalsIgnoreCase("localhost")
                        && !uri.getHost().equals("127.0.0.1")) {
                    effectiveRpId = uri.getHost();
                }
            } catch (Exception ignored) {
            }
        }
        return effectiveRpId;
    }

    public Set<String> getAllowedOrigins() {
        String effectiveRpId = getEffectiveRpId();
        Set<String> origins = new LinkedHashSet<>();

        if (originsProperty != null && !originsProperty.isBlank()) {
            Arrays.stream(originsProperty.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(origins::add);
        }

        if (frontendUrl != null && !frontendUrl.isBlank()) {
            Arrays.stream(frontendUrl.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(origins::add);
        }

        if ("localhost".equalsIgnoreCase(effectiveRpId)) {
            origins.add("http://localhost:4200");
            origins.add("http://localhost:8081");
            origins.add("http://localhost:8080");
            origins.add("http://127.0.0.1:4200");
            origins.add("http://127.0.0.1:8081");
        }
        return origins;
    }

    @Bean
    public RelyingParty relyingParty(BiometricCredentialRepository credentialRepository) {
        String effectiveRpId = getEffectiveRpId();
        RelyingPartyIdentity rpIdentity = RelyingPartyIdentity.builder()
                .id(effectiveRpId)
                .name(rpName)
                .build();

        return RelyingParty.builder()
                .identity(rpIdentity)
                .credentialRepository(credentialRepository)
                .origins(getAllowedOrigins())
                .allowUntrustedAttestation(true)
                .validateSignatureCounter(true)
                .build();
    }
}
