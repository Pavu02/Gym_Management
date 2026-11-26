package com.gym.springapp.service;

import com.gym.springapp.model.Member;
import com.gym.springapp.model.MemberWebAuthnCredential;
import com.gym.springapp.repository.MemberRepo;
import com.gym.springapp.repository.MemberWebAuthnCredentialRepo;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.AuthenticatorTransport;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.PublicKeyCredentialType;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Primary
public class BiometricCredentialRepository implements CredentialRepository {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MemberWebAuthnCredentialRepo credentialRepo;
    private final MemberRepo memberRepo;

    public BiometricCredentialRepository(
            MemberWebAuthnCredentialRepo credentialRepo,
            MemberRepo memberRepo) {
        this.credentialRepo = credentialRepo;
        this.memberRepo = memberRepo;
    }

    public String ensureMemberUserHandle(Member member) {
        if (member.getWebAuthnUserHandle() != null && !member.getWebAuthnUserHandle().isBlank()) {
            return member.getWebAuthnUserHandle();
        }
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String handle = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        member.setWebAuthnUserHandle(handle);
        memberRepo.save(member);
        return handle;
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return memberRepo.findByUserEmail(username)
                .map(member -> credentialRepo.findByMemberIdAndActiveTrue(member.getId()).stream()
                        .map(cred -> PublicKeyCredentialDescriptor.builder()
                                .id(decodeBase64Url(cred.getCredentialId()))
                                .type(PublicKeyCredentialType.PUBLIC_KEY)
                                .transports(parseTransports(cred.getTransports()))
                                .build())
                        .collect(Collectors.toSet()))
                .orElse(Collections.emptySet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return memberRepo.findByUserEmail(username)
                .map(this::ensureMemberUserHandle)
                .map(this::decodeBase64Url);
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        String handle = userHandle.getBase64Url();
        return memberRepo.findByWebAuthnUserHandle(handle)
                .filter(m -> m.getUser() != null)
                .map(m -> m.getUser().getEmail());
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        String credIdB64 = credentialId.getBase64Url();
        return credentialRepo.findByCredentialIdAndActiveTrue(credIdB64)
                .filter(cred -> cred.getUserHandle() != null && cred.getUserHandle().equals(userHandle.getBase64Url()))
                .map(cred -> RegisteredCredential.builder()
                        .credentialId(credentialId)
                        .userHandle(decodeBase64Url(cred.getUserHandle()))
                        .publicKeyCose(new ByteArray(cred.getPublicKeyCose()))
                        .signatureCount(cred.getSignatureCount())
                        .build());
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        String credIdB64 = credentialId.getBase64Url();
        return credentialRepo.findByCredentialIdAndActiveTrue(credIdB64)
                .map(cred -> Collections.singleton(
                        RegisteredCredential.builder()
                                .credentialId(credentialId)
                                .userHandle(decodeBase64Url(cred.getUserHandle()))
                                .publicKeyCose(new ByteArray(cred.getPublicKeyCose()))
                                .signatureCount(cred.getSignatureCount())
                                .build()))
                .orElse(Collections.emptySet());
    }

    public ByteArray decodeBase64Url(String value) {
        try {
            return ByteArray.fromBase64Url(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed Base64URL value", e);
        }
    }

    private Optional<Set<AuthenticatorTransport>> parseTransports(String transportsCsv) {
        if (transportsCsv == null || transportsCsv.isBlank()) {
            return Optional.empty();
        }
        Set<AuthenticatorTransport> set = Arrays.stream(transportsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(t -> {
                    try {
                        return AuthenticatorTransport.of(t);
                    } catch (Exception ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return set.isEmpty() ? Optional.empty() : Optional.of(set);
    }
}
