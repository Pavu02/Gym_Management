package com.gym.springapp.config;

import com.gym.springapp.service.PasskeyCredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class WebAuthnConfig {

    @Value("${webauthn.rp-id:${webauthn.rp.id:localhost}}")
    private String rpId;

    @Value("${webauthn.rp-name:${webauthn.rp.name:BeastX Gym}}")
    private String rpName;

    @Value("${webauthn.origins:${webauthn.origin:http://localhost:8081}}")
    private String originsProperty;

    @Value("${app.frontend-url:}")
    private String frontendUrl;

    @Bean
    public RelyingParty relyingParty(PasskeyCredentialRepository credentialRepository) {
        String effectiveRpId = rpId;

        // If default localhost but frontend-url is configured with an external domain
        // (e.g. vercel.app), derive RP ID
        if ("localhost".equalsIgnoreCase(effectiveRpId) && frontendUrl != null && !frontendUrl.isBlank()) {
            try {
                String firstUrl = frontendUrl.split(",")[0].trim();
                URI uri = URI.create(firstUrl);
                if (uri.getHost() != null && !uri.getHost().equalsIgnoreCase("localhost")
                        && !uri.getHost().equals("127.0.0.1")) {
                    effectiveRpId = uri.getHost();
                }
            } catch (Exception ignored) {
            }
        }

        RelyingPartyIdentity rpIdentity = RelyingPartyIdentity.builder()
                .id(effectiveRpId)
                .name(rpName)
                .build();

        Set<String> origins = Arrays.stream(originsProperty.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        if (frontendUrl != null && !frontendUrl.isBlank()) {
            Arrays.stream(frontendUrl.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(origins::add);
        }

        if (effectiveRpId.equalsIgnoreCase("localhost")) {
            origins.add("http://localhost:8081");
            origins.add("http://localhost:8080");
            origins.add("http://127.0.0.1:8081");
        }

        return RelyingParty.builder()
                .identity(rpIdentity)
                .credentialRepository(credentialRepository)
                .origins(origins)
                .allowUntrustedAttestation(true)
                .build();
    }
}
