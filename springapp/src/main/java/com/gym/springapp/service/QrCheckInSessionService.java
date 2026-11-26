package com.gym.springapp.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.gym.springapp.config.CheckInPolicyProperties;
import com.gym.springapp.controller.CheckInWebSocketController;
import com.gym.springapp.dto.WebAuthnDtoBundle.*;
import com.gym.springapp.exceptions.*;
import com.gym.springapp.model.*;
import com.gym.springapp.repository.*;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class QrCheckInSessionService {

    private static final Logger log = LoggerFactory.getLogger(QrCheckInSessionService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{24,128}$");

    private static final List<QrSessionStatus> ACTIVE_SESSION_STATUSES = List.of(
            QrSessionStatus.PENDING,
            QrSessionStatus.PHONE_CONNECTED,
            QrSessionStatus.AWAITING_USER_VERIFICATION,
            QrSessionStatus.VERIFYING);

    private final QrCheckInSessionRepository sessionRepo;
    private final MemberRepo memberRepo;
    private final UserRepo userRepo;
    private final MemberWebAuthnCredentialRepo credentialRepo;
    private final AttendanceRepo attendanceRepo;
    private final AttendanceService attendanceService;
    private final RelyingParty relyingParty;
    private final WebAuthnChallengeService challengeService;
    private final RateLimitService rateLimitService;
    private final LocationRiskService locationRiskService;
    private final CheckInWebSocketController wsController;
    private final CheckInPolicyProperties policy;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    public QrCheckInSessionService(
            QrCheckInSessionRepository sessionRepo,
            MemberRepo memberRepo,
            UserRepo userRepo,
            MemberWebAuthnCredentialRepo credentialRepo,
            AttendanceRepo attendanceRepo,
            AttendanceService attendanceService,
            RelyingParty relyingParty,
            WebAuthnChallengeService challengeService,
            RateLimitService rateLimitService,
            LocationRiskService locationRiskService,
            CheckInWebSocketController wsController,
            CheckInPolicyProperties policy) {
        this.sessionRepo = sessionRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.credentialRepo = credentialRepo;
        this.attendanceRepo = attendanceRepo;
        this.attendanceService = attendanceService;
        this.relyingParty = relyingParty;
        this.challengeService = challengeService;
        this.rateLimitService = rateLimitService;
        this.locationRiskService = locationRiskService;
        this.wsController = wsController;
        this.policy = policy;
    }

    @Transactional
    public QrSessionResponse createSession(String adminEmail, QrSessionCreateRequest request) {
        User admin = userRepo.findByEmailIgnoreCase(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin account not found."));
        if (admin.getRole() != Role.ADMIN || !admin.isActive() || admin.isDeleted()) {
            throw new ForbiddenOperationException("Only active gym administrators can start QR check-in sessions.");
        }

        String gymId = resolveBranchValue(request != null ? request.gymId() : null, admin.getGymId(),
                policy.getDefaultGymId());
        String branchId = resolveBranchValue(request != null ? request.branchId() : null, admin.getBranchId(),
                policy.getDefaultBranchId());

        if (admin.getBranchId() != null && !admin.getBranchId().isBlank()
                && !admin.getBranchId().equalsIgnoreCase(branchId)) {
            throw new ForbiddenOperationException("Admin does not have access to branch: " + branchId);
        }

        rateLimitService.checkSessionGeneration(admin.getId());

        // Invalidate previous active QR sessions for this admin when issuing a fresh QR
        sessionRepo.cancelActiveSessionsForAdmin(
                admin.getId(),
                ACTIVE_SESSION_STATUSES,
                QrSessionStatus.CANCELLED,
                "Replaced by newer QR session");

        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = sha256Hex(rawToken);
        String sessionCode = UUID.randomUUID().toString();

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(policy.getQrExpirationSeconds());

        QrCheckInSession session = new QrCheckInSession();
        session.setSessionCode(sessionCode);
        session.setTokenHash(tokenHash);
        session.setGymId(gymId);
        session.setBranchId(branchId);
        session.setCreatedByAdminId(admin.getId());
        session.setStatus(QrSessionStatus.PENDING);
        session.setCreatedAt(now);
        session.setExpiresAt(expiresAt);

        sessionRepo.save(session);

        String qrUrl = getPrimaryFrontendOrigin() + "/check-in/verify?token=" + rawToken;
        String qrDataUrl = generateQrDataUrl(qrUrl);

        wsController.publishAfterCommit(new CheckInWebSocketEvent(
                sessionCode,
                QrSessionStatus.PENDING.name(),
                "SESSION_CREATED",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "QR + DEVICE_BIOMETRIC",
                "AWAITING_SCAN",
                null,
                null,
                branchId,
                now));

        return new QrSessionResponse(
                sessionCode,
                rawToken,
                qrUrl,
                qrDataUrl,
                gymId,
                branchId,
                QrSessionStatus.PENDING.name(),
                now,
                expiresAt,
                now,
                policy.getQrExpirationSeconds());
    }

    @Transactional
    public QrSessionValidateResponse validateSessionForMember(
            String memberEmail,
            QrSessionValidateRequest request,
            String clientIp) {
        if (request == null || request.token() == null || !TOKEN_PATTERN.matcher(request.token().trim()).matches()) {
            throw new BadRequestException("This QR code is malformed or invalid. Scan a valid gym check-in QR.");
        }

        Member member = resolveMember(memberEmail);
        attendanceService.validateMemberCheckInEligibility(member, "QR");

        String tokenHash = sha256Hex(request.token().trim());
        QrCheckInSession session = sessionRepo.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Check-in QR session does not exist."));

        enforceSessionUsable(session);
        enforceMemberBranchAccess(member, session);

        String riskResult = locationRiskService.evaluateLocationAndNetwork(
                request.latitude(), request.longitude(), request.wifiSsid(), clientIp);

        int deviceCount = (int) credentialRepo.countByMemberIdAndActiveTrue(member.getId());
        boolean hasDevices = deviceCount > 0;

        if (session.getStatus() == QrSessionStatus.PENDING) {
            session.setStatus(QrSessionStatus.PHONE_CONNECTED);
            session.setRiskResult(riskResult);
            sessionRepo.save(session);

            wsController.publishAfterCommit(buildEvent(
                    session,
                    member,
                    "PHONE_CONNECTED",
                    "QR + DEVICE_BIOMETRIC",
                    "PHONE_CONNECTED",
                    riskResult,
                    null,
                    null));
        }

        Instant now = Instant.now();
        long remainingSeconds = Math.max(0L, ChronoUnit.SECONDS.between(now, session.getExpiresAt()));

        return new QrSessionValidateResponse(
                true,
                session.getSessionCode(),
                session.getGymId(),
                session.getBranchId(),
                session.getStatus().name(),
                session.getExpiresAt(),
                now,
                remainingSeconds,
                member.getId(),
                member.getUser().getName(),
                member.getPlan() != null ? member.getPlan().getName() : "Standard",
                member.getStatus(),
                hasDevices,
                deviceCount,
                policy.getLocationPolicy().name(),
                riskResult,
                hasDevices ? "QR verified. Confirm identity with your registered device."
                        : "This phone is not registered for biometric check-in.");
    }

    @Transactional
    public AuthOptionsResponse startWebAuthnAssertion(String memberEmail, AuthOptionsRequest request) {
        if (request == null || request.qrToken() == null
                || !TOKEN_PATTERN.matcher(request.qrToken().trim()).matches()) {
            throw new BadRequestException("Malformed or missing QR token.");
        }

        Member member = resolveMember(memberEmail);
        attendanceService.validateMemberCheckInEligibility(member, "QR");
        rateLimitService.checkVerificationRateLimit(member.getId());

        String tokenHash = sha256Hex(request.qrToken().trim());
        QrCheckInSession session = sessionRepo.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Check-in QR session does not exist."));

        enforceSessionUsable(session);
        enforceMemberBranchAccess(member, session);

        List<MemberWebAuthnCredential> activeCreds = credentialRepo.findByMemberIdAndActiveTrue(member.getId());
        if (activeCreds.isEmpty()) {
            throw new VerificationRuleException(
                    "This phone is not registered for biometric check-in. Please register this device for biometric check-in.");
        }

        AssertionRequest assertionRequest = relyingParty.startAssertion(
                StartAssertionOptions.builder()
                        .username(member.getUser().getEmail())
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .timeout(policy.getChallengeExpirationSeconds() * 1000L)
                        .build());

        WebAuthnChallenge savedChallenge = challengeService.createAuthenticationChallenge(member, session,
                assertionRequest);

        session.setStatus(QrSessionStatus.AWAITING_USER_VERIFICATION);
        sessionRepo.save(session);

        wsController.publishAfterCommit(buildEvent(
                session,
                member,
                "AWAITING_USER_VERIFICATION",
                "QR + DEVICE_BIOMETRIC",
                "AWAITING_BIOMETRIC",
                session.getRiskResult(),
                null,
                null));

        try {
            return new AuthOptionsResponse(
                    savedChallenge.getChallengeId(),
                    session.getSessionCode(),
                    assertionRequest.toCredentialsGetJson(),
                    policy.getChallengeExpirationSeconds(),
                    activeCreds.size());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize WebAuthn authentication options", e);
        }
    }

    @Transactional
    public CheckInVerificationResponse verifyWebAuthnAndCheckIn(
            String memberEmail,
            AuthVerifyRequest request,
            String clientIp) {
        if (request == null || request.qrToken() == null
                || !TOKEN_PATTERN.matcher(request.qrToken().trim()).matches()) {
            throw new BadRequestException("Malformed QR check-in token.");
        }

        Member member = resolveMember(memberEmail);
        attendanceService.validateMemberCheckInEligibility(member, "BIOMETRIC");
        String cleanIdemKey = normalizeIdempotencyKey(request.idempotencyKey(), member.getId());

        // 1. Idempotent lookup if phone lost connectivity before receiving previous
        // commit response
        if (cleanIdemKey != null) {
            Optional<Attendance> priorAtt = attendanceRepo.findByIdempotencyKey(cleanIdemKey);
            if (priorAtt.isPresent() && priorAtt.get().getMember().getId().equals(member.getId())) {
                Attendance a = priorAtt.get();
                QrCheckInSession priorSession = sessionRepo.findByTokenHash(sha256Hex(request.qrToken().trim()))
                        .orElse(null);
                return toVerificationResponse(member, a, priorSession, a.getVerificationResult(), a.getRiskResult(),
                        cleanIdemKey);
            }
        }

        rateLimitService.checkVerificationRateLimit(member.getId());

        // 2. Pessimistically lock QR session row so concurrent scans cannot
        // double-consume
        String tokenHash = sha256Hex(request.qrToken().trim());
        QrCheckInSession session = sessionRepo.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Check-in QR session does not exist."));

        enforceSessionUsable(session);
        enforceMemberBranchAccess(member, session);

        session.setStatus(QrSessionStatus.VERIFYING);
        sessionRepo.save(session);

        boolean usedDevicePin = "DEVICE_PIN".equalsIgnoreCase(request.verificationHint())
                || "PIN".equalsIgnoreCase(request.verificationHint());

        AttendanceMethod attMethod;
        CheckInMethod checkMethod;
        String methodLabel;
        String verificationResult;
        Long matchedCredentialId = null;

        if ("OTP_FALLBACK".equalsIgnoreCase(request.fallbackMethod())) {
            if (!policy.isOtpFallbackEnabled()) {
                throw new ForbiddenOperationException("OTP fallback check-in is disabled for this gym branch.");
            }
            if (request.otpCode() == null || !request.otpCode().trim().matches("^\\d{6}$")) {
                rateLimitService.recordVerificationFailure(member.getId());
                throw new VerificationRuleException("Invalid 6-digit fallback OTP code.");
            }
            attMethod = AttendanceMethod.OTP_FALLBACK;
            checkMethod = CheckInMethod.OTP_FALLBACK;
            methodLabel = "OTP_FALLBACK";
            verificationResult = "VERIFIED_OTP_FALLBACK";
        } else {
            if (request.credentialJson() == null || request.credentialJson().isBlank()) {
                throw new BadRequestException("Signed WebAuthn assertion response is required.");
            }

            // 3. Consume bound single-use WebAuthn challenge (operation=AUTHENTICATION,
            // memberId, qrSessionId)
            WebAuthnChallenge storedChallenge = challengeService.consumeAuthenticationChallenge(
                    request.challengeId(), member, session);

            AssertionRequest assertionRequest;
            try {
                assertionRequest = AssertionRequest.fromJson(storedChallenge.getOptionsJson());
            } catch (Exception e) {
                throw new BadRequestException("Corrupted challenge payload.");
            }

            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc;
            try {
                pkc = PublicKeyCredential.parseAssertionResponseJson(request.credentialJson());
            } catch (Exception e) {
                throw new BadRequestException("Malformed WebAuthn assertion payload.");
            }

            AssertionResult result;
            try {
                result = relyingParty.finishAssertion(
                        FinishAssertionOptions.builder()
                                .request(assertionRequest)
                                .response(pkc)
                                .build());
            } catch (AssertionFailedException e) {
                rateLimitService.recordVerificationFailure(member.getId());
                publishVerificationFailure(session, member, "Cryptographic verification failed");
                throw new VerificationRuleException("Cryptographic WebAuthn verification failed: " + e.getMessage());
            }

            if (!result.isSuccess()) {
                rateLimitService.recordVerificationFailure(member.getId());
                publishVerificationFailure(session, member, "Device verification failed");
                throw new BiometricMismatchException("Device verification failed. Please try again.");
            }

            // 4. Verify User Verified (UV) flag in authenticatorData
            if (!result.isUserVerified()) {
                rateLimitService.recordVerificationFailure(member.getId());
                publishVerificationFailure(session, member, "User verification flag missing");
                throw new VerificationRuleException(
                        "Biometric or screen lock user verification (UV) flag was not satisfied.");
            }

            String credentialIdB64 = result.getCredentialId().getBase64Url();
            MemberWebAuthnCredential cred = credentialRepo.findByCredentialId(credentialIdB64)
                    .orElseThrow(() -> new VerificationRuleException(
                            "Your registered passkey is unavailable. Register this device again or request admin assistance."));

            if (!cred.getMember().getId().equals(member.getId())) {
                rateLimitService.recordVerificationFailure(member.getId());
                publishVerificationFailure(session, member, "Credential owner mismatch");
                throw new ForbiddenOperationException("Presented passkey credential belongs to another member.");
            }

            if (!cred.isActive() || cred.getRevokedAt() != null) {
                rateLimitService.recordVerificationFailure(member.getId());
                publishVerificationFailure(session, member, "Credential revoked");
                throw new VerificationRuleException(
                        "This check-in device has been revoked. Please register a device again.");
            }

            // 5. Verify signature counter when supported by authenticator (stored > 0)
            long storedCount = cred.getSignatureCount();
            long presentedCount = result.getSignatureCount();
            if ((storedCount > 0 && presentedCount < storedCount) || !result.isSignatureCounterValid()) {
                rateLimitService.recordVerificationFailure(member.getId());
                publishVerificationFailure(session, member, "Signature counter regression");
                throw new VerificationRuleException(
                        "Device signature counter validation failed. Please re-register this device or request admin assistance.");
            }

            Instant now = Instant.now();
            cred.setSignatureCount(Math.max(storedCount, presentedCount));
            cred.setLastUsedAt(now);
            credentialRepo.save(cred);
            matchedCredentialId = cred.getId();

            if (usedDevicePin) {
                attMethod = AttendanceMethod.DEVICE_PIN;
                checkMethod = CheckInMethod.DEVICE_PIN;
                methodLabel = "DEVICE_PIN";
                verificationResult = "VERIFIED_DEVICE_PIN";
            } else {
                attMethod = AttendanceMethod.QR_DEVICE_BIOMETRIC;
                checkMethod = CheckInMethod.QR_DEVICE_BIOMETRIC;
                methodLabel = "QR + DEVICE_BIOMETRIC";
                verificationResult = "VERIFIED_BIOMETRIC";
            }
        }

        String riskResult = locationRiskService.evaluateLocationAndNetwork(
                request.latitude(), request.longitude(), request.wifiSsid(), clientIp);

        // Re-check fresh DB account status inside lock immediately before creating
        // attendance
        member = resolveMember(memberEmail);
        attendanceService.validateMemberCheckInEligibility(member, methodLabel);

        Attendance savedAttendance;
        try {
            savedAttendance = attendanceService.recordVerifiedCheckIn(
                    member,
                    session.getGymId(),
                    session.getBranchId(),
                    attMethod,
                    checkMethod,
                    methodLabel,
                    verificationResult,
                    riskResult,
                    session.getId(),
                    matchedCredentialId,
                    cleanIdemKey,
                    null);
        } catch (DataIntegrityViolationException ex) {
            throw new QrSessionConflictException(
                    "This check-in session or attendance record has already been recorded.");
        }

        Instant completedAt = Instant.now();
        session.setStatus(QrSessionStatus.COMPLETED);
        session.setConsumedAt(completedAt);
        session.setMemberId(member.getId());
        session.setVerificationMethod(methodLabel);
        session.setRiskResult(riskResult);
        session.setIdempotencyKey(cleanIdemKey);
        sessionRepo.save(session);

        rateLimitService.clearVerificationFailures(member.getId());

        wsController.publishAfterCommit(buildEvent(
                session,
                member,
                "CHECKIN_COMPLETED",
                methodLabel,
                verificationResult,
                riskResult,
                null,
                savedAttendance));

        return toVerificationResponse(member, savedAttendance, session, verificationResult, riskResult, cleanIdemKey);
    }

    @Transactional
    public CheckInVerificationResponse adminManualCheckIn(String adminEmail, AdminManualCheckInRequest request) {
        if (!policy.isAllowAdminManualFallback()) {
            throw new ForbiddenOperationException("Admin manual fallback is disabled.");
        }
        if (request == null || request.memberId() == null || request.reason() == null
                || request.reason().trim().length() < 4) {
            throw new BadRequestException(
                    "A clear admin reason (minimum 4 characters) is required for assisted manual check-in.");
        }

        User admin = userRepo.findByEmailIgnoreCase(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin account not found."));
        if (admin.getRole() != Role.ADMIN) {
            throw new ForbiddenOperationException("Only ADMIN users may perform manual assisted check-in.");
        }

        Member member = memberRepo.findById(request.memberId())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found."));

        QrCheckInSession session = null;
        if (request.sessionCode() != null && !request.sessionCode().isBlank()) {
            session = sessionRepo.findBySessionCode(request.sessionCode().trim()).orElse(null);
            if (session != null && session.getCreatedByAdminId() != null
                    && !session.getCreatedByAdminId().equals(admin.getId())) {
                throw new ForbiddenOperationException("Admin cannot modify another admin's check-in session.");
            }
        }

        String branchId = session != null ? session.getBranchId() : policy.getDefaultBranchId();
        String gymId = session != null ? session.getGymId() : policy.getDefaultGymId();
        String cleanReason = "Admin assisted (" + admin.getName() + "): " + request.reason().trim();

        Attendance saved = attendanceService.recordVerifiedCheckIn(
                member,
                gymId,
                branchId,
                AttendanceMethod.MANUAL,
                CheckInMethod.MANUAL,
                "ADMIN_MANUAL",
                "VERIFIED_ADMIN_MANUAL",
                "ADMIN_ASSISTED",
                session != null ? session.getId() : null,
                null,
                null,
                cleanReason);

        if (session != null && session.isClaimableForVerification()) {
            session.setStatus(QrSessionStatus.COMPLETED);
            session.setConsumedAt(Instant.now());
            session.setMemberId(member.getId());
            session.setVerificationMethod("ADMIN_MANUAL");
            session.setRiskResult("ADMIN_ASSISTED");
            sessionRepo.save(session);

            wsController.publishAfterCommit(buildEvent(
                    session,
                    member,
                    "CHECKIN_COMPLETED",
                    "ADMIN_MANUAL",
                    "VERIFIED_ADMIN_MANUAL",
                    "ADMIN_ASSISTED",
                    null,
                    saved));
        }

        return toVerificationResponse(member, saved, session, "VERIFIED_ADMIN_MANUAL", "ADMIN_ASSISTED", null);
    }

    @Transactional
    public CheckInWebSocketEvent getSessionStatusForAdmin(String adminEmail, String sessionCode) {
        User admin = userRepo.findByEmailIgnoreCase(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin account not found."));
        QrCheckInSession session = sessionRepo.findBySessionCode(sessionCode)
                .orElseThrow(() -> new ResourceNotFoundException("QR check-in session not found."));
        if (!session.getCreatedByAdminId().equals(admin.getId())) {
            throw new ForbiddenOperationException("You can only view QR check-in sessions you created.");
        }

        if (session.isClaimableForVerification()
                && Instant.now().minusSeconds(policy.getClockSkewSeconds()).isAfter(session.getExpiresAt())) {
            session.setStatus(QrSessionStatus.EXPIRED);
            sessionRepo.save(session);
        }

        Member member = session.getMemberId() != null ? memberRepo.findById(session.getMemberId()).orElse(null) : null;
        return buildEvent(
                session,
                member,
                session.getStatus().name(),
                session.getVerificationMethod() != null ? session.getVerificationMethod() : "QR + DEVICE_BIOMETRIC",
                session.getStatus() == QrSessionStatus.COMPLETED ? "VERIFIED" : session.getStatus().name(),
                session.getRiskResult(),
                session.getFailureReason(),
                null);
    }

    @Transactional
    public void cancelSession(String adminEmail, String sessionCode) {
        User admin = userRepo.findByEmailIgnoreCase(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin account not found."));
        QrCheckInSession session = sessionRepo.findBySessionCode(sessionCode)
                .orElseThrow(() -> new ResourceNotFoundException("QR check-in session not found."));
        if (!session.getCreatedByAdminId().equals(admin.getId())) {
            throw new ForbiddenOperationException("You can only cancel QR sessions you started.");
        }

        if (session.isClaimableForVerification()) {
            session.setStatus(QrSessionStatus.CANCELLED);
            session.setFailureReason("Cancelled by administrator");
            sessionRepo.save(session);

            wsController.publishAfterCommit(buildEvent(
                    session,
                    null,
                    "SESSION_CANCELLED",
                    "QR + DEVICE_BIOMETRIC",
                    "CANCELLED",
                    null,
                    "Cancelled by administrator",
                    null));
        }
    }

    @Transactional(readOnly = true)
    public CheckInVerificationResponse lookupByIdempotencyKey(String memberEmail, String idempotencyKey) {
        Member member = resolveMember(memberEmail);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency key is required.");
        }
        String scopedKey = normalizeIdempotencyKey(idempotencyKey, member.getId());
        Attendance attendance = attendanceRepo.findByIdempotencyKey(scopedKey)
                .orElseThrow(() -> new ResourceNotFoundException("No check-in found for that confirmation key."));
        if (!attendance.getMember().getId().equals(member.getId())) {
            throw new ForbiddenOperationException("Access denied.");
        }
        QrCheckInSession session = attendance.getQrSessionId() != null
                ? sessionRepo.findById(attendance.getQrSessionId()).orElse(null)
                : null;
        return toVerificationResponse(member, attendance, session, attendance.getVerificationResult(),
                attendance.getRiskResult(), scopedKey);
    }

    private void enforceSessionUsable(QrCheckInSession session) {
        if (session.getStatus() == QrSessionStatus.COMPLETED || session.getStatus() == QrSessionStatus.CONSUMED) {
            throw new QrSessionConflictException("This QR code has already been used.");
        }
        if (session.getStatus() == QrSessionStatus.CANCELLED) {
            throw new QrSessionConflictException(
                    "This QR code was cancelled by the administrator. Please scan the latest QR.");
        }
        if (session.getStatus() == QrSessionStatus.EXPIRED
                || Instant.now().minusSeconds(policy.getClockSkewSeconds()).isAfter(session.getExpiresAt())) {
            if (session.getStatus() != QrSessionStatus.EXPIRED) {
                session.setStatus(QrSessionStatus.EXPIRED);
                sessionRepo.save(session);
            }
            throw new QrSessionExpiredException(
                    "This QR code has expired. Ask the administrator to generate a new one.");
        }
    }

    private void enforceMemberBranchAccess(Member member, QrCheckInSession session) {
        String mGym = member.getGymId() != null ? member.getGymId() : policy.getDefaultGymId();
        String mBranch = member.getBranchId() != null ? member.getBranchId() : policy.getDefaultBranchId();
        if (!mGym.equalsIgnoreCase(session.getGymId()) || !mBranch.equalsIgnoreCase(session.getBranchId())) {
            throw new ForbiddenOperationException(
                    "Your membership does not permit check-in at branch " + session.getBranchId());
        }
    }

    private Member resolveMember(String email) {
        User user = userRepo.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found."));
        return memberRepo.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found."));
    }

    private String normalizeIdempotencyKey(String raw, Long memberId) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim().replaceAll("[^A-Za-z0-9_-]", "");
        if (trimmed.length() < 8) {
            return null;
        }
        String scoped = "m" + memberId + "_" + trimmed;
        return scoped.length() > 128 ? scoped.substring(0, 128) : scoped;
    }

    private String resolveBranchValue(String requested, String actorValue, String fallback) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        if (actorValue != null && !actorValue.isBlank()) {
            return actorValue.trim();
        }
        return fallback;
    }

    private String getPrimaryFrontendOrigin() {
        if (frontendUrl != null && !frontendUrl.isBlank()) {
            String first = frontendUrl.split(",")[0].trim();
            return first.endsWith("/") ? first.substring(0, first.length() - 1) : first;
        }
        return "http://localhost:4200";
    }

    private String generateQrDataUrl(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 360, 360);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render check-in QR code", e);
        }
    }

    public String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private CheckInVerificationResponse toVerificationResponse(
            Member member,
            Attendance attendance,
            QrCheckInSession session,
            String verificationResult,
            String riskResult,
            String idempotencyKey) {
        String checkTimeFormatted = attendance.getCheckInTime() != null
                ? attendance.getCheckInTime().format(TIME_FMT)
                : "";
        boolean isDevicePin = "VERIFIED_DEVICE_PIN".equalsIgnoreCase(verificationResult);
        String msg = isDevicePin ? "Device verification successful." : "Check-in completed successfully.";
        return new CheckInVerificationResponse(
                "CHECKED_IN",
                msg,
                isDevicePin ? "DEVICE_PIN" : "DEVICE_BIOMETRIC",
                member.getId(),
                member.getUser().getName(),
                null,
                member.getPlan() != null ? member.getPlan().getName() : "Standard",
                member.getStatus(),
                member.getExpiryDate() != null ? member.getExpiryDate().toString() : null,
                checkTimeFormatted,
                attendance.getCheckInTimestamp() != null ? attendance.getCheckInTimestamp() : Instant.now(),
                attendance.getVerificationMethodLabel() != null ? attendance.getVerificationMethodLabel()
                        : attendance.getMethod().name(),
                session != null ? session.getSessionCode() : null,
                riskResult,
                idempotencyKey);
    }

    private CheckInWebSocketEvent buildEvent(
            QrCheckInSession session,
            Member member,
            String eventType,
            String methodLabel,
            String verificationResult,
            String riskResult,
            String failureReason,
            Attendance attendance) {
        String memberName = member != null && member.getUser() != null ? member.getUser().getName() : null;
        String initials = computeInitials(memberName);
        String planName = member != null && member.getPlan() != null ? member.getPlan().getName() : null;
        String expiryDate = member != null && member.getExpiryDate() != null ? member.getExpiryDate().toString() : null;
        String membershipStatus = member != null ? member.getStatus() : null;
        String checkInTime = attendance != null && attendance.getCheckInTime() != null
                ? attendance.getCheckInTime().format(TIME_FMT)
                : (session.getConsumedAt() != null
                        ? session.getConsumedAt().atZone(KOLKATA_ZONE).format(TIME_FMT)
                        : null);

        return new CheckInWebSocketEvent(
                session.getSessionCode(),
                session.getStatus().name(),
                eventType,
                member != null ? member.getId() : null,
                memberName,
                initials,
                null,
                planName,
                membershipStatus,
                expiryDate,
                checkInTime,
                attendance != null ? attendance.getCheckInTimestamp() : session.getConsumedAt(),
                methodLabel,
                verificationResult,
                riskResult,
                failureReason,
                session.getBranchId(),
                Instant.now());
    }

    private String computeInitials(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    private void publishVerificationFailure(QrCheckInSession session, Member member, String reason) {
        try {
            wsController.publishImmediate(new CheckInWebSocketEvent(
                    session.getSessionCode(),
                    QrSessionStatus.FAILED.name(),
                    "CHECKIN_FAILED",
                    member != null ? member.getId() : null,
                    member != null && member.getUser() != null ? member.getUser().getName() : null,
                    member != null && member.getUser() != null ? computeInitials(member.getUser().getName()) : null,
                    null,
                    member != null && member.getPlan() != null ? member.getPlan().getName() : null,
                    member != null ? member.getStatus() : null,
                    member != null && member.getExpiryDate() != null ? member.getExpiryDate().toString() : null,
                    null,
                    Instant.now(),
                    "QR + DEVICE_BIOMETRIC",
                    "FAILED",
                    session.getRiskResult(),
                    reason,
                    session.getBranchId(),
                    Instant.now()));
        } catch (Exception ex) {
            log.warn("Non-fatal failure broadcasting failure event: {}", ex.getMessage());
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupExpiredQrSessions() {
        sessionRepo.markExpiredSessions(Instant.now(), ACTIVE_SESSION_STATUSES);
    }
}
