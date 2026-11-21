package com.gym.springapp.config;

import com.gym.springapp.service.fingerprint.GymCredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

@Configuration
public class WebAuthnConfig {

    @Value("${webauthn.rp.id:localhost}")
    private String rpId;

    @Value("${webauthn.rp.name:BeastX Gym Management}")
    private String rpName;

    @Value("${webauthn.origin:http://localhost:8081}")
    private String origin;

    @Bean
    public RelyingParty relyingParty(GymCredentialRepository credentialRepository) {
        RelyingPartyIdentity rpIdentity = RelyingPartyIdentity.builder()
                .id(rpId)
                .name(rpName)
                .build();

        Set<String> origins = new HashSet<>();
        origins.add(origin);
        if (origin.startsWith("http://localhost")) {
            origins.add("http://127.0.0.1:8081");
            origins.add("http://localhost:8080");
        }

        return RelyingParty.builder()
                .identity(rpIdentity)
                .credentialRepository(credentialRepository)
                .origins(origins)
                .allowUntrustedAttestation(true)
                .build();
    }
}
