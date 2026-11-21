package com.gym.springapp.service.fingerprint;

import com.gym.springapp.model.User;
import com.gym.springapp.model.WebAuthnCredential;
import com.gym.springapp.repository.MemberRepo;
import com.gym.springapp.repository.UserRepo;
import com.gym.springapp.repository.WebAuthnCredentialRepo;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.PublicKeyCredentialType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class GymCredentialRepository implements CredentialRepository {

    private final WebAuthnCredentialRepo webAuthnCredentialRepo;
    private final MemberRepo memberRepo;
    private final UserRepo userRepo;

    public GymCredentialRepository(
            WebAuthnCredentialRepo webAuthnCredentialRepo,
            MemberRepo memberRepo,
            UserRepo userRepo) {
        this.webAuthnCredentialRepo = webAuthnCredentialRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
    }

    private ByteArray safeFromBase64Url(String base64Url) {
        try {
            return ByteArray.fromBase64Url(base64Url);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Base64Url credentialId", e);
        }
    }

    private ByteArray safeFromBase64(String base64) {
        try {
            return ByteArray.fromBase64(base64);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Base64 publicKey", e);
        }
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return memberRepo.findByUserEmail(username)
                .map(member -> webAuthnCredentialRepo.findByMemberId(member.getId()).stream()
                        .map(cred -> PublicKeyCredentialDescriptor.builder()
                                .id(safeFromBase64Url(cred.getCredentialId()))
                                .type(PublicKeyCredentialType.PUBLIC_KEY)
                                .build())
                        .collect(Collectors.toSet()))
                .orElse(Collections.emptySet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return userRepo.findByEmail(username)
                .map(user -> new ByteArray(user.getEmail().getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        String email = new String(userHandle.getBytes(), StandardCharsets.UTF_8);
        return userRepo.findByEmail(email).map(User::getEmail);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        String credIdBase64 = credentialId.getBase64Url();
        Optional<WebAuthnCredential> credOpt = webAuthnCredentialRepo.findByCredentialId(credIdBase64);
        if (credOpt.isEmpty()) {
            return Optional.empty();
        }

        WebAuthnCredential cred = credOpt.get();
        ByteArray storedUserHandle = new ByteArray(cred.getMember().getUser().getEmail().getBytes(StandardCharsets.UTF_8));

        RegisteredCredential rc = RegisteredCredential.builder()
                .credentialId(credentialId)
                .userHandle(storedUserHandle)
                .publicKeyCose(safeFromBase64(cred.getPublicKey()))
                .signatureCount(cred.getSignCount())
                .build();

        return Optional.of(rc);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        String credIdBase64 = credentialId.getBase64Url();
        return webAuthnCredentialRepo.findByCredentialId(credIdBase64)
                .map(cred -> {
                    ByteArray userHandle = new ByteArray(cred.getMember().getUser().getEmail().getBytes(StandardCharsets.UTF_8));
                    RegisteredCredential rc = RegisteredCredential.builder()
                            .credentialId(credentialId)
                            .userHandle(userHandle)
                            .publicKeyCose(safeFromBase64(cred.getPublicKey()))
                            .signatureCount(cred.getSignCount())
                            .build();
                    return Collections.singleton(rc);
                })
                .orElse(Collections.emptySet());
    }
}
