package com.gym.springapp.service;

import com.gym.springapp.dto.*;
import com.gym.springapp.exceptions.BadRequestException;
import com.gym.springapp.exceptions.ForbiddenOperationException;
import com.gym.springapp.exceptions.MemberDeactivatedException;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.exceptions.VerificationRuleException;
import com.gym.springapp.model.Member;
import com.gym.springapp.model.User;
import com.gym.springapp.model.WebAuthnCredential;
import com.gym.springapp.repository.MemberRepo;
import com.gym.springapp.repository.UserRepo;
import com.gym.springapp.repository.WebAuthnCredentialRepo;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PasskeyServiceImpl implements PasskeyService {

    private static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");
    private static final int MAX_DEVICES_PER_MEMBER = 2;

    private final RelyingParty relyingParty;
    private final MemberRepo memberRepo;
    private final UserRepo userRepo;
    private final WebAuthnCredentialRepo webAuthnCredentialRepo;
    private final AttendanceService attendanceService;

    @Value("${webauthn.rp.id:localhost}")
    private String rpId;

    @Value("${webauthn.rp.name:BeastX Gym Management}")
    private String rpName;

    // Temporary storage for active challenges (keyed by user email or challenge
    // Base64Url)
    private final Map<String, PublicKeyCredentialCreationOptions> registrationRequests = new ConcurrentHashMap<>();
    private final Map<String, AssertionRequest> assertionRequests = new ConcurrentHashMap<>();

    public PasskeyServiceImpl(
            RelyingParty relyingParty,
            MemberRepo memberRepo,
            UserRepo userRepo,
            WebAuthnCredentialRepo webAuthnCredentialRepo,
            AttendanceService attendanceService) {
        this.relyingParty = relyingParty;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.webAuthnCredentialRepo = webAuthnCredentialRepo;
        this.attendanceService = attendanceService;
    }

    @Override
    @Transactional
    public PasskeyRegistrationOptionsDTO startRegistration(String authEmail) {
        User user = userRepo.findByEmail(authEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + authEmail));

        Member member = memberRepo.findByUserEmail(authEmail).orElseGet(() -> {
            Member newMember = new Member();
            newMember.setUser(user);
            newMember.setStatus("PENDING_PAYMENT");
            return memberRepo.save(newMember);
        });
        attendanceService.validateAccountNotDeactivated(member, "BIOMETRIC");

        long deviceCount = webAuthnCredentialRepo.findByMemberId(member.getId()).stream()
                .filter(WebAuthnCredential::isActive)
                .count();
        if (deviceCount >= MAX_DEVICES_PER_MEMBER) {
            throw new BadRequestException("Device limit reached (maximum " + MAX_DEVICES_PER_MEMBER
                    + " devices). Please remove an existing device first.");
        }

        UserIdentity userIdentity = UserIdentity.builder()
                .name(user.getEmail())
                .displayName(user.getName())
                .id(new ByteArray(user.getEmail().getBytes(StandardCharsets.UTF_8)))
                .build();

        AuthenticatorSelectionCriteria authSelection = AuthenticatorSelectionCriteria.builder()
                .userVerification(UserVerificationRequirement.PREFERRED)
                .residentKey(ResidentKeyRequirement.PREFERRED)
                .build();

        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(userIdentity)
                        .authenticatorSelection(authSelection)
                        .build());

        String challengeBase64 = options.getChallenge().getBase64Url();
        registrationRequests.put(user.getEmail(), options);
        registrationRequests.put(challengeBase64, options);

        try {
            return new PasskeyRegistrationOptionsDTO(
                    rpId,
                    rpName,
                    challengeBase64,
                    user.getId().toString(),
                    user.getName(),
                    options.toJson());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize passkey creation options: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public WebAuthnDeviceDTO finishRegistration(String authEmail, PasskeyRegisterVerifyDTO verifyDTO) {
        if (verifyDTO == null || verifyDTO.registrationResponseJson() == null) {
            throw new BadRequestException("Registration response payload cannot be empty");
        }

        User user = userRepo.findByEmail(authEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + authEmail));

        Member member = memberRepo.findByUserEmail(authEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found for email: " + authEmail));
        attendanceService.validateAccountNotDeactivated(member, "BIOMETRIC");

        long deviceCount = webAuthnCredentialRepo.findByMemberId(member.getId()).stream()
                .filter(WebAuthnCredential::isActive)
                .count();
        if (deviceCount >= MAX_DEVICES_PER_MEMBER) {
            throw new BadRequestException("Device limit reached (maximum " + MAX_DEVICES_PER_MEMBER + " devices)");
        }

        PublicKeyCredentialCreationOptions options = registrationRequests.remove(authEmail);
        if (options == null) {
            throw new BadRequestException("No active passkey registration challenge found. Please retry.");
        }

        try {
            PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc = PublicKeyCredential
                    .parseRegistrationResponseJson(verifyDTO.registrationResponseJson());

            RegistrationResult result = relyingParty.finishRegistration(
                    FinishRegistrationOptions.builder()
                            .request(options)
                            .response(pkc)
                            .build());

            String credentialId = result.getKeyId().getId().getBase64Url();
            String publicKeyCose = result.getPublicKeyCose().getBase64();
            long signCount = result.getSignatureCount();

            String deviceName = (verifyDTO.deviceName() != null && !verifyDTO.deviceName().trim().isEmpty())
                    ? verifyDTO.deviceName().trim()
                    : "Passkey Device " + (deviceCount + 1);

            LocalDateTime now = LocalDateTime.now(KOLKATA_ZONE);

            WebAuthnCredential cred = new WebAuthnCredential();
            cred.setMember(member);
            cred.setCredentialId(credentialId);
            cred.setPublicKey(publicKeyCose);
            cred.setSignCount(signCount);
            cred.setDeviceName(deviceName);
            cred.setCreatedAt(now);

            WebAuthnCredential saved = webAuthnCredentialRepo.save(cred);

            return new WebAuthnDeviceDTO(
                    saved.getId(),
                    saved.getCredentialId(),
                    saved.getDeviceName(),
                    saved.getSignCount(),
                    saved.getCreatedAt());

        } catch (Exception e) {
            throw new BadRequestException("Passkey verification failed: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PasskeyAssertionOptionsDTO startAssertion(String authEmailOrNull) {
        StartAssertionOptions.StartAssertionOptionsBuilder builder = StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.REQUIRED);

        if (authEmailOrNull != null && !authEmailOrNull.trim().isEmpty()) {
            memberRepo.findByUserEmail(authEmailOrNull.trim())
                    .ifPresent(m -> attendanceService.validateMemberCheckInEligibility(m, "BIOMETRIC"));
            builder.username(Optional.of(authEmailOrNull.trim()));
        }

        AssertionRequest request = relyingParty.startAssertion(builder.build());
        String challengeBase64 = request.getPublicKeyCredentialRequestOptions().getChallenge().getBase64Url();

        if (authEmailOrNull != null) {
            assertionRequests.put(authEmailOrNull.trim(), request);
        }
        assertionRequests.put(challengeBase64, request);

        try {
            return new PasskeyAssertionOptionsDTO(
                    challengeBase64,
                    rpId,
                    request.toCredentialsGetJson());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize passkey assertion options: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public Member finishAssertion(String authEmailOrNull, PasskeyAssertionVerifyDTO verifyDTO) {
        if (verifyDTO == null || verifyDTO.assertionResponseJson() == null) {
            throw new BadRequestException("Assertion response payload cannot be empty");
        }

        try {
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc = PublicKeyCredential
                    .parseAssertionResponseJson(verifyDTO.assertionResponseJson());

            String challengeBase64 = pkc.getResponse().getClientData().getChallenge().getBase64Url();
            AssertionRequest request = assertionRequests.remove(challengeBase64);

            if (request == null && authEmailOrNull != null) {
                request = assertionRequests.remove(authEmailOrNull);
            }

            if (request == null) {
                throw new BadRequestException("No matching passkey assertion request found or session expired.");
            }

            AssertionResult result = relyingParty.finishAssertion(
                    FinishAssertionOptions.builder()
                            .request(request)
                            .response(pkc)
                            .build());

            if (!result.isSuccess()) {
                throw new BadRequestException("WebAuthn assertion failed.");
            }

            String credentialId = result.getCredential().getCredentialId().getBase64Url();
            WebAuthnCredential cred = webAuthnCredentialRepo.findByCredentialId(credentialId)
                    .orElseThrow(
                            () -> new ResourceNotFoundException("Registered credential not found in gym database"));

            if (!cred.isActive()) {
                throw new VerificationRuleException(
                        "This biometric credential has been revoked. Register this device for biometric check-in.");
            }
            attendanceService.validateMemberCheckInEligibility(cred.getMember(), "BIOMETRIC");

            long newSignCount = result.getSignatureCount();
            if (newSignCount <= cred.getSignCount() && cred.getSignCount() > 0) {
                throw new BadRequestException("Cloned passkey risk detected: signCount did not advance (stored: "
                        + cred.getSignCount() + ", presented: " + newSignCount + ")");
            }

            cred.setSignCount(newSignCount);
            webAuthnCredentialRepo.save(cred);

            return cred.getMember();

        } catch (MemberDeactivatedException | ForbiddenOperationException | VerificationRuleException
                | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Fingerprint passkey verification error: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebAuthnDeviceDTO> getMemberDevices(String authEmail, Long memberId, boolean isAdmin) {
        Member member;
        if (isAdmin && memberId != null) {
            member = memberRepo.findById(memberId)
                    .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));
        } else {
            member = memberRepo.findByUserEmail(authEmail)
                    .orElseThrow(
                            () -> new ResourceNotFoundException("Member profile not found for email: " + authEmail));
            attendanceService.validateAccountNotDeactivated(member, "BIOMETRIC");
        }

        return webAuthnCredentialRepo.findByMemberId(member.getId()).stream()
                .filter(WebAuthnCredential::isActive)
                .map(c -> new WebAuthnDeviceDTO(
                        c.getId(),
                        null,
                        c.getDeviceName(),
                        0L,
                        c.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteDevice(String authEmail, String credentialId, boolean isAdmin) {
        WebAuthnCredential cred = webAuthnCredentialRepo.findByCredentialId(credentialId)
                .orElseThrow(() -> new ResourceNotFoundException("Credential not found: " + credentialId));

        if (!isAdmin && !cred.getMember().getUser().getEmail().equalsIgnoreCase(authEmail)) {
            throw new BadRequestException("You do not have permission to delete this device credential");
        }

        webAuthnCredentialRepo.delete(cred);
    }
}
