package com.gym.springapp.service;

import com.gym.springapp.automation.EmailNotificationService;
import com.gym.springapp.config.CheckInPolicyProperties;
import com.gym.springapp.dto.RegisteredDeviceDTO;
import com.gym.springapp.dto.WebAuthnDtoBundle.RegisterOptionsRequest;
import com.gym.springapp.dto.WebAuthnDtoBundle.RegisterOptionsResponse;
import com.gym.springapp.dto.WebAuthnDtoBundle.RegisterVerifyRequest;
import com.gym.springapp.exceptions.BadRequestException;
import com.gym.springapp.exceptions.ForbiddenOperationException;
import com.gym.springapp.exceptions.QrSessionConflictException;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.exceptions.VerificationRuleException;
import com.gym.springapp.model.Member;
import com.gym.springapp.model.MemberWebAuthnCredential;
import com.gym.springapp.model.User;
import com.gym.springapp.model.WebAuthnChallenge;
import com.gym.springapp.repository.MemberRepo;
import com.gym.springapp.repository.MemberWebAuthnCredentialRepo;
import com.gym.springapp.repository.UserRepo;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.exception.RegistrationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

@Service
public class BiometricCredentialService {

    private static final Logger log = LoggerFactory.getLogger(BiometricCredentialService.class);

    private final RelyingParty relyingParty;
    private final BiometricCredentialRepository credentialRepository;
    private final MemberWebAuthnCredentialRepo memberCredentialRepo;
    private final WebAuthnChallengeService challengeService;
    private final MemberRepo memberRepo;
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final CheckInPolicyProperties policy;
    private final AttendanceService attendanceService;
    private final ObjectProvider<EmailNotificationService> emailServiceProvider;
    private final ObjectProvider<Executor> automationExecutorProvider;

    public BiometricCredentialService(
            RelyingParty relyingParty,
            BiometricCredentialRepository credentialRepository,
            MemberWebAuthnCredentialRepo memberCredentialRepo,
            WebAuthnChallengeService challengeService,
            MemberRepo memberRepo,
            UserRepo userRepo,
            PasswordEncoder passwordEncoder,
            CheckInPolicyProperties policy,
            AttendanceService attendanceService,
            ObjectProvider<EmailNotificationService> emailServiceProvider,
            @Qualifier("automationExecutor") ObjectProvider<Executor> automationExecutorProvider) {
        this.relyingParty = relyingParty;
        this.credentialRepository = credentialRepository;
        this.memberCredentialRepo = memberCredentialRepo;
        this.challengeService = challengeService;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.policy = policy;
        this.attendanceService = attendanceService;
        this.emailServiceProvider = emailServiceProvider;
        this.automationExecutorProvider = automationExecutorProvider;
    }

    @Transactional
    public RegisterOptionsResponse startRegistration(String authEmail, RegisterOptionsRequest request) {
        Member member = resolveActiveMember(authEmail);
        User user = member.getUser();

        long activeCount = memberCredentialRepo.countByMemberIdAndActiveTrue(member.getId());
        if (activeCount >= policy.getMaxDevicesPerMember()) {
            throw new QrSessionConflictException("Maximum registered devices (" + policy.getMaxDevicesPerMember()
                    + ") reached. Revoke an unused device before registering a new one.");
        }

        boolean reauthVerified = false;
        boolean hasPassword = request != null && request.currentPassword() != null && !request.currentPassword().isBlank();
        if (hasPassword) {
            if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
                throw new BadCredentialsException("Current password is incorrect.");
            }
            reauthVerified = true;
        } else if (activeCount >= 2 && policy.getMaxDevicesPerMember() > 2) {
            // Replacement / additional sensitive device beyond primary+backup warrants re-auth if supplied;
            // explicit enforcement when client opts in or replaces device via revoke-all endpoint.
        }

        String userHandleStr = credentialRepository.ensureMemberUserHandle(member);
        ByteArray userHandle = credentialRepository.decodeBase64Url(userHandleStr);

        UserIdentity userIdentity = UserIdentity.builder()
                .name("member-" + userHandleStr.substring(0, Math.min(12, userHandleStr.length())))
                .displayName(user.getName() != null && !user.getName().isBlank() ? user.getName() : "Gym Member")
                .id(userHandle)
                .build();

        AuthenticatorSelectionCriteria selection = AuthenticatorSelectionCriteria.builder()
                .authenticatorAttachment(AuthenticatorAttachment.PLATFORM)
                .residentKey(ResidentKeyRequirement.PREFERRED)
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build();

        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(userIdentity)
                        .authenticatorSelection(selection)
                        .timeout(policy.getRegistrationChallengeSeconds() * 1000L)
                        .build());

        WebAuthnChallenge savedChallenge = challengeService.createRegistrationChallenge(member, options);
        return new RegisterOptionsResponse(
                savedChallenge.getChallengeId(),
                savedChallenge.getOptionsJson(),
                policy.getRegistrationChallengeSeconds(),
                reauthVerified);
    }

    @Transactional
    public RegisteredDeviceDTO finishRegistration(String authEmail, RegisterVerifyRequest request) {
        if (request == null || request.credentialJson() == null || request.credentialJson().isBlank()) {
            throw new BadRequestException("Credential payload is required.");
        }
        if (!request.consent()) {
            throw new BadRequestException("Biometric enrollment consent is required.");
        }

        Member member = resolveActiveMember(authEmail);
        WebAuthnChallenge storedChallenge = challengeService.consumeRegistrationChallenge(request.challengeId(), member);

        PublicKeyCredentialCreationOptions creationOptions;
        try {
            creationOptions = PublicKeyCredentialCreationOptions.fromJson(storedChallenge.getOptionsJson());
        } catch (Exception e) {
            throw new BadRequestException("Corrupted registration challenge options.");
        }

        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc;
        try {
            pkc = PublicKeyCredential.parseRegistrationResponseJson(request.credentialJson());
        } catch (Exception e) {
            throw new BadRequestException("Malformed WebAuthn registration response.");
        }

        RegistrationResult result;
        try {
            result = relyingParty.finishRegistration(
                    FinishRegistrationOptions.builder()
                            .request(creationOptions)
                            .response(pkc)
                            .build());
        } catch (RegistrationFailedException e) {
            throw new VerificationRuleException("WebAuthn device verification failed: " + e.getMessage());
        }

        String credentialIdB64 = result.getKeyId().getId().getBase64Url();
        Optional<MemberWebAuthnCredential> existing = memberCredentialRepo.findByCredentialId(credentialIdB64);
        if (existing.isPresent()) {
            throw new QrSessionConflictException("This passkey credential is already registered. Duplicate registration is blocked.");
        }

        long activeCount = memberCredentialRepo.countByMemberIdAndActiveTrue(member.getId());
        if (activeCount >= policy.getMaxDevicesPerMember()) {
            throw new QrSessionConflictException("Maximum registered devices reached.");
        }

        String cleanName = sanitizeDeviceName(request.deviceName(), activeCount + 1);
        String cleanType = sanitizeDeviceType(request.deviceType());
        String transports = request.transports() != null && !request.transports().isEmpty()
                ? String.join(",", request.transports())
                : "internal";

        Instant now = Instant.now();
        MemberWebAuthnCredential entity = new MemberWebAuthnCredential();
        entity.setMember(member);
        entity.setCredentialId(credentialIdB64);
        entity.setPublicKeyCose(result.getPublicKeyCose().getBytes());
        entity.setUserHandle(credentialRepository.ensureMemberUserHandle(member));
        entity.setSignatureCount(result.getSignatureCount());
        entity.setDeviceName(cleanName);
        entity.setDeviceType(cleanType);
        entity.setTransports(transports);
        entity.setRegisteredAt(now);
        entity.setActive(true);

        MemberWebAuthnCredential saved = memberCredentialRepo.save(entity);
        notifyMemberDeviceEvent(member.getUser(), "New Check-In Device Registered",
                "Your biometric check-in device \"" + cleanName + "\" was added to your account.");
        return toSafeDto(saved);
    }

    @Transactional(readOnly = true)
    public List<RegisteredDeviceDTO> getMyDevices(String authEmail) {
        Member member = resolveActiveMember(authEmail);
        return memberCredentialRepo.findByMemberIdOrderByRegisteredAtDesc(member.getId()).stream()
                .map(this::toSafeDto)
                .toList();
    }

    @Transactional
    public RegisteredDeviceDTO renameMyDevice(String authEmail, Long deviceId, String newName) {
        Member member = resolveActiveMember(authEmail);
        MemberWebAuthnCredential device = memberCredentialRepo.findByIdAndMemberId(deviceId, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Check-in device not found."));

        if (!device.isActive()) {
            throw new QrSessionConflictException("Cannot rename a revoked device.");
        }
        String sanitized = sanitizeDeviceName(newName, 1);
        device.setDeviceName(sanitized);
        return toSafeDto(memberCredentialRepo.save(device));
    }

    @Transactional
    public void revokeMyDevice(String authEmail, Long deviceId) {
        Member member = resolveActiveMember(authEmail);
        MemberWebAuthnCredential device = memberCredentialRepo.findByIdAndMemberId(deviceId, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Check-in device not found."));

        if (!device.isActive()) {
            return;
        }
        device.setActive(false);
        device.setRevokedAt(Instant.now());
        memberCredentialRepo.save(device);
        notifyMemberDeviceEvent(member.getUser(), "Check-In Device Revoked",
                "Biometric check-in access for \"" + device.getDeviceName() + "\" was revoked.");
    }

    @Transactional
    public int revokeAllMyDevices(String authEmail, String currentPassword) {
        Member member = resolveActiveMember(authEmail);
        User user = member.getUser();
        if (currentPassword == null || currentPassword.isBlank()
                || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadCredentialsException("Re-authentication failed: valid current password is required to revoke all devices.");
        }

        List<MemberWebAuthnCredential> activeDevices = memberCredentialRepo.findByMemberIdAndActiveTrue(member.getId());
        Instant now = Instant.now();
        for (MemberWebAuthnCredential device : activeDevices) {
            device.setActive(false);
            device.setRevokedAt(now);
        }
        memberCredentialRepo.saveAll(activeDevices);
        if (!activeDevices.isEmpty()) {
            notifyMemberDeviceEvent(user, "All Check-In Devices Revoked",
                    "All " + activeDevices.size() + " registered check-in devices were revoked from your account.");
        }
        return activeDevices.size();
    }

    @Transactional(readOnly = true)
    public boolean hasActiveDevices(Long memberId) {
        return memberCredentialRepo.countByMemberIdAndActiveTrue(memberId) > 0;
    }

    public RegisteredDeviceDTO toSafeDto(MemberWebAuthnCredential entity) {
        return new RegisteredDeviceDTO(
                entity.getId(),
                entity.getDeviceName(),
                entity.getDeviceType(),
                entity.getTransports(),
                entity.getRegisteredAt(),
                entity.getLastUsedAt(),
                entity.isActive(),
                entity.getRevokedAt());
    }

    private Member resolveMember(String authEmail) {
        User user = userRepo.findByEmailIgnoreCase(authEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found."));
        return memberRepo.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found."));
    }

    private Member resolveActiveMember(String authEmail) {
        Member member = resolveMember(authEmail);
        attendanceService.validateAccountNotDeactivated(member, "BIOMETRIC");
        return member;
    }

    private String sanitizeDeviceName(String raw, long index) {
        if (raw == null || raw.isBlank()) {
            return "Phone Device " + index;
        }
        String trimmed = raw.trim().replaceAll("[\\r\\n\\t]+", " ");
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }

    private String sanitizeDeviceType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "MOBILE_PHONE";
        }
        String trimmed = raw.trim().toUpperCase().replaceAll("[^A-Z0-9_\\-]", "_");
        return trimmed.length() > 60 ? trimmed.substring(0, 60) : trimmed;
    }

    private void notifyMemberDeviceEvent(User user, String subject, String message) {
        EmailNotificationService mailService = emailServiceProvider.getIfAvailable();
        if (mailService == null || user == null || user.getEmail() == null) {
            return;
        }
        Runnable sendTask = () -> {
            try {
                String html = "<p>Hi " + (user.getName() != null ? user.getName() : "Member") + ",</p>"
                        + "<p>" + message + "</p>"
                        + "<p>If you did not initiate this change, please contact gym support immediately and revoke unfamiliar devices.</p>";
                mailService.sendHtml(user.getEmail(), subject, html);
            } catch (Exception ex) {
                log.warn("Non-fatal failure sending security alert email: {}", ex.getMessage());
            }
        };
        Executor exec = automationExecutorProvider.getIfAvailable();
        if (exec != null) {
            exec.execute(sendTask);
        } else {
            sendTask.run();
        }
    }
}
