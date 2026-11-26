package com.gym.springapp.service;

import com.gym.springapp.config.CheckInPolicyProperties;
import com.gym.springapp.exceptions.BadRequestException;
import com.gym.springapp.exceptions.ForbiddenOperationException;
import com.gym.springapp.exceptions.QrSessionConflictException;
import com.gym.springapp.exceptions.QrSessionExpiredException;
import com.gym.springapp.model.Member;
import com.gym.springapp.model.QrCheckInSession;
import com.gym.springapp.model.WebAuthnChallenge;
import com.gym.springapp.repository.WebAuthnChallengeRepository;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class WebAuthnChallengeService {

    public static final String OP_REGISTRATION = "REGISTRATION";
    public static final String OP_AUTHENTICATION = "AUTHENTICATION";

    private final WebAuthnChallengeRepository challengeRepository;
    private final CheckInPolicyProperties policy;

    public WebAuthnChallengeService(
            WebAuthnChallengeRepository challengeRepository,
            CheckInPolicyProperties policy) {
        this.challengeRepository = challengeRepository;
        this.policy = policy;
    }

    @Transactional
    public WebAuthnChallenge createRegistrationChallenge(Member member, PublicKeyCredentialCreationOptions options) {
        Instant now = Instant.now();
        WebAuthnChallenge challenge = new WebAuthnChallenge();
        challenge.setChallengeId(UUID.randomUUID().toString());
        challenge.setChallengeValue(options.getChallenge().getBase64Url());
        challenge.setOperation(OP_REGISTRATION);
        challenge.setMemberId(member.getId());
        challenge.setQrSessionId(null);
        try {
            challenge.setOptionsJson(options.toJson());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize registration challenge", e);
        }
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plusSeconds(policy.getRegistrationChallengeSeconds()));
        challenge.setConsumed(false);
        return challengeRepository.save(challenge);
    }

    @Transactional
    public WebAuthnChallenge createAuthenticationChallenge(
            Member member,
            QrCheckInSession session,
            AssertionRequest request) {
        Instant now = Instant.now();
        WebAuthnChallenge challenge = new WebAuthnChallenge();
        challenge.setChallengeId(UUID.randomUUID().toString());
        challenge.setChallengeValue(request.getPublicKeyCredentialRequestOptions().getChallenge().getBase64Url());
        challenge.setOperation(OP_AUTHENTICATION);
        challenge.setMemberId(member.getId());
        challenge.setQrSessionId(session.getId());
        try {
            challenge.setOptionsJson(request.toJson());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize authentication challenge", e);
        }
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plusSeconds(policy.getChallengeExpirationSeconds()));
        challenge.setConsumed(false);
        return challengeRepository.save(challenge);
    }

    @Transactional
    public WebAuthnChallenge consumeRegistrationChallenge(String challengeId, Member member) {
        return consumeChallengeInternal(challengeId, OP_REGISTRATION, member.getId(), null);
    }

    @Transactional
    public WebAuthnChallenge consumeAuthenticationChallenge(String challengeId, Member member, QrCheckInSession session) {
        return consumeChallengeInternal(challengeId, OP_AUTHENTICATION, member.getId(), session.getId());
    }

    private WebAuthnChallenge consumeChallengeInternal(
            String challengeId,
            String expectedOp,
            Long expectedMemberId,
            Long expectedQrSessionId) {
        if (challengeId == null || challengeId.isBlank()) {
            throw new BadRequestException("Challenge ID is required.");
        }

        WebAuthnChallenge stored = challengeRepository.findByChallengeId(challengeId.trim())
                .orElseThrow(() -> new BadRequestException("Verification challenge not found. Please try again."));

        if (!expectedOp.equals(stored.getOperation())) {
            throw new BadRequestException("Invalid challenge operation type.");
        }
        if (!stored.getMemberId().equals(expectedMemberId)) {
            throw new ForbiddenOperationException("Challenge was issued for another member.");
        }
        if (expectedQrSessionId != null && !expectedQrSessionId.equals(stored.getQrSessionId())) {
            throw new QrSessionConflictException("Authentication response was submitted against the wrong QR session.");
        }
        if (stored.isConsumed()) {
            throw new QrSessionConflictException("This verification challenge has already been used.");
        }

        Instant now = Instant.now();
        if (stored.getExpiresAt().isBefore(now)) {
            throw new QrSessionExpiredException("Verification challenge expired. Please try again.");
        }

        int updated = challengeRepository.consumeValidChallenge(
                stored.getChallengeId(),
                expectedOp,
                expectedMemberId,
                expectedQrSessionId,
                now);
        if (updated != 1) {
            throw new QrSessionConflictException("Verification challenge could not be consumed or was already replayed.");
        }
        return stored;
    }

    @Scheduled(fixedRate = 120000)
    @Transactional
    public void cleanupExpiredChallenges() {
        challengeRepository.deleteStaleChallenges(Instant.now().minusSeconds(600));
    }
}
