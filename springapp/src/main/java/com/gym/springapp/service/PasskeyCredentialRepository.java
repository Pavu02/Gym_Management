package com.gym.springapp.service;

import com.gym.springapp.model.Member;
import com.gym.springapp.model.MemberPasskey;
import com.gym.springapp.model.User;
import com.gym.springapp.repository.MemberPasskeyRepo;
import com.gym.springapp.repository.MemberRepo;
import com.gym.springapp.repository.UserRepo;
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
public class PasskeyCredentialRepository implements CredentialRepository {

    private final MemberPasskeyRepo memberPasskeyRepo;
    private final MemberRepo memberRepo;
    private final UserRepo userRepo;

    public PasskeyCredentialRepository(
            MemberPasskeyRepo memberPasskeyRepo,
            MemberRepo memberRepo,
            UserRepo userRepo) {
        this.memberPasskeyRepo = memberPasskeyRepo;
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

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return memberRepo.findByUserEmail(username)
                .map(member -> memberPasskeyRepo.findByMemberId(member.getId()).stream()
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
        Optional<MemberPasskey> credOpt = memberPasskeyRepo.findByCredentialId(credIdBase64);
        if (credOpt.isEmpty()) {
            return Optional.empty();
        }

        MemberPasskey passkey = credOpt.get();
        ByteArray storedUserHandle = new ByteArray(passkey.getMember().getUser().getEmail().getBytes(StandardCharsets.UTF_8));

        RegisteredCredential rc = RegisteredCredential.builder()
                .credentialId(credentialId)
                .userHandle(storedUserHandle)
                .publicKeyCose(new ByteArray(passkey.getPublicKeyCose()))
                .signatureCount(passkey.getSignCount())
                .build();

        return Optional.of(rc);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        String credIdBase64 = credentialId.getBase64Url();
        return memberPasskeyRepo.findByCredentialId(credIdBase64)
                .map(passkey -> {
                    ByteArray userHandle = new ByteArray(passkey.getMember().getUser().getEmail().getBytes(StandardCharsets.UTF_8));
                    RegisteredCredential rc = RegisteredCredential.builder()
                            .credentialId(credentialId)
                            .userHandle(userHandle)
                            .publicKeyCose(new ByteArray(passkey.getPublicKeyCose()))
                            .signatureCount(passkey.getSignCount())
                            .build();
                    return Collections.singleton(rc);
                })
                .orElse(Collections.emptySet());
    }
}
