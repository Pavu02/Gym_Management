package com.gym.springapp.config;

import com.gym.springapp.service.PasskeyCredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class WebAuthnConfig {

    @Value("${webauthn.rp-id:${webauthn.rp.id:localhost}}")
    private String rpId;

    @Value("${webauthn.rp-name:${webauthn.rp.name:GymPro}}")
    private String rpName;

    @Value("${webauthn.origins:${webauthn.origin:http://localhost:8081}}")
    private String originsProperty;

    @Bean
    public RelyingParty relyingParty(PasskeyCredentialRepository credentialRepository) {
        RelyingPartyIdentity rpIdentity = RelyingPartyIdentity.builder()
                .id(rpId)
                .name(rpName)
                .build();

        Set<String> origins = Arrays.stream(originsProperty.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        if (rpId.equalsIgnoreCase("localhost")) {
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
