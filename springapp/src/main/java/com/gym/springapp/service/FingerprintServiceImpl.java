package com.gym.springapp.service;

import com.gym.springapp.dto.*;
import com.gym.springapp.exceptions.BadRequestException;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.model.*;
import com.gym.springapp.repository.*;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FingerprintServiceImpl implements FingerprintService {

    private static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");

    private final RelyingParty relyingParty;
    private final WebAuthnChallengeStore challengeStore;
    private final MemberPasskeyRepo memberPasskeyRepo;
    private final MemberRepo memberRepo;
    private final UserRepo userRepo;
    private final KioskTokenRepo kioskTokenRepo;
    private final AttendanceService attendanceService;
    private final AttendanceRepo attendanceRepo;
    private final FaceAttendanceRepo faceAttendanceRepo;

    @Value("${checkin.fingerprint.simulator-enabled:false}")
    private boolean simulatorEnabled;

    public FingerprintServiceImpl(
            RelyingParty relyingParty,
            WebAuthnChallengeStore challengeStore,
            MemberPasskeyRepo memberPasskeyRepo,
            MemberRepo memberRepo,
            UserRepo userRepo,
            KioskTokenRepo kioskTokenRepo,
            AttendanceService attendanceService,
            AttendanceRepo attendanceRepo,
            FaceAttendanceRepo faceAttendanceRepo) {
        this.relyingParty = relyingParty;
        this.challengeStore = challengeStore;
        this.memberPasskeyRepo = memberPasskeyRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.kioskTokenRepo = kioskTokenRepo;
        this.attendanceService = attendanceService;
        this.attendanceRepo = attendanceRepo;
        this.faceAttendanceRepo = faceAttendanceRepo;
    }

    private Member getMemberByEmail(String email) {
        return memberRepo.findByUserEmail(email).orElseGet(() -> {
            User user = userRepo.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
            Member newMember = new Member();
            newMember.setUser(user);
            newMember.setStatus("PENDING_PAYMENT");
            return memberRepo.save(newMember);
        });
    }

    @Override
    @Transactional
    public RegisterStartResponseDTO startRegistration(String authEmail) {
        Member member = getMemberByEmail(authEmail);
        User user = member.getUser();

        String requestId = UUID.randomUUID().toString();

        UserIdentity userIdentity = UserIdentity.builder()
                .name(user.getEmail())
                .displayName(user.getName())
                .id(new ByteArray(user.getEmail().getBytes(StandardCharsets.UTF_8)))
                .build();

        AuthenticatorSelectionCriteria authSelection = AuthenticatorSelectionCriteria.builder()
                .authenticatorAttachment(AuthenticatorAttachment.PLATFORM)
                .residentKey(ResidentKeyRequirement.REQUIRED)
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build();

        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(userIdentity)
                        .authenticatorSelection(authSelection)
                        .build());

        challengeStore.putRegistration(requestId, options);

        try {
            return new RegisterStartResponseDTO(requestId, options.toJson());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize creation options: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public FingerprintDeviceDTO finishRegistration(String authEmail, RegisterFinishRequestDTO request) {
        if (request == null || request.requestId() == null || request.credential() == null) {
            throw new BadRequestException("Request body cannot be empty");
        }

        Member member = getMemberByEmail(authEmail);

        PublicKeyCredentialCreationOptions options = challengeStore.removeRegistration(request.requestId());
        if (options == null) {
            throw new BadRequestException("Registration session expired or invalid. Please try again.");
        }

        try {
            PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc = PublicKeyCredential
                    .parseRegistrationResponseJson(request.credential());

            RegistrationResult result = relyingParty.finishRegistration(
                    FinishRegistrationOptions.builder()
                            .request(options)
                            .response(pkc)
                            .build());

            String credentialId = result.getKeyId().getId().getBase64Url();
            byte[] publicKeyCose = result.getPublicKeyCose().getBytes();
            long signCount = result.getSignatureCount();
            String deviceName = (request.deviceName() != null && !request.deviceName().trim().isEmpty())
                    ? request.deviceName().trim()
                    : "My Phone";

            LocalDateTime now = LocalDateTime.now(KOLKATA_ZONE);

            MemberPasskey passkey = memberPasskeyRepo.findByCredentialId(credentialId).orElseGet(() -> {
                MemberPasskey mp = new MemberPasskey();
                mp.setMember(member);
                mp.setCredentialId(credentialId);
                mp.setCreatedAt(now);
                return mp;
            });

            passkey.setMember(member);
            passkey.setUserHandle(member.getUser().getEmail());
            passkey.setPublicKeyCose(publicKeyCose);
            passkey.setSignCount(signCount);
            passkey.setDeviceName(deviceName);
            passkey.setLastUsedAt(now);

            MemberPasskey saved = memberPasskeyRepo.save(passkey);

            return new FingerprintDeviceDTO(
                    saved.getId(),
                    saved.getDeviceName(),
                    saved.getCreatedAt(),
                    saved.getLastUsedAt());
        } catch (Exception e) {
            throw new BadRequestException("Failed to verify fingerprint passkey: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FingerprintDeviceDTO> getMemberDevices(String authEmail) {
        Member member = getMemberByEmail(authEmail);
        return memberPasskeyRepo.findByMemberId(member.getId()).stream()
                .map(p -> new FingerprintDeviceDTO(p.getId(), p.getDeviceName(), p.getCreatedAt(), p.getLastUsedAt()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteMyDevice(String authEmail, Long deviceId) {
        Member member = getMemberByEmail(authEmail);
        MemberPasskey passkey = memberPasskeyRepo.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found with id: " + deviceId));

        if (!passkey.getMember().getId().equals(member.getId())) {
            throw new BadRequestException("You can only remove your own registered devices.");
        }

        memberPasskeyRepo.delete(passkey);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FingerprintDeviceDTO> getDevicesByMemberId(Long memberId) {
        return memberPasskeyRepo.findByMemberId(memberId).stream()
                .map(p -> new FingerprintDeviceDTO(p.getId(), p.getDeviceName(), p.getCreatedAt(), p.getLastUsedAt()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void adminDeleteDevice(Long deviceId) {
        MemberPasskey passkey = memberPasskeyRepo.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found with id: " + deviceId));
        memberPasskeyRepo.delete(passkey);
    }

    @Override
    @Transactional
    public CheckinStartResponseDTO startCheckin(String authEmail, CheckinStartRequestDTO request) {
        if (request == null || request.kioskToken() == null || request.kioskToken().trim().isEmpty()) {
            throw new BadRequestException("Kiosk token is required");
        }

        KioskToken kt = kioskTokenRepo.findByToken(request.kioskToken().trim())
                .orElseThrow(() -> new BadRequestException("Invalid kiosk QR code"));

        if (kt.isUsed()) {
            throw new BadRequestException(
                    "This QR code has already been used. Please scan the current QR on the screen.");
        }

        if (kt.getExpiresAt().isBefore(LocalDateTime.now(KOLKATA_ZONE))) {
            throw new BadRequestException("This QR code has expired. Please scan the new QR on the screen.");
        }

        Member member = getMemberByEmail(authEmail);
        List<MemberPasskey> passkeys = memberPasskeyRepo.findByMemberId(member.getId());
        if (passkeys.isEmpty()) {
            throw new BadRequestException(
                    "No fingerprint device registered. Please enable fingerprint check-in first.");
        }

        StartAssertionOptions.StartAssertionOptionsBuilder builder = StartAssertionOptions.builder()
                .username(Optional.of(authEmail))
                .userVerification(UserVerificationRequirement.REQUIRED);

        AssertionRequest assertionRequest = relyingParty.startAssertion(builder.build());
        String requestId = UUID.randomUUID().toString();

        challengeStore.putAssertion(requestId, assertionRequest);

        try {
            return new CheckinStartResponseDTO(requestId, assertionRequest.toCredentialsGetJson());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize assertion options: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public CheckinResultDTO finishCheckin(String authEmail, CheckinFinishRequestDTO request) {
        if (request == null || request.requestId() == null || request.kioskToken() == null
                || request.credential() == null) {
            throw new BadRequestException("Invalid check-in request payload");
        }

        Member member = getMemberByEmail(authEmail);

        AssertionRequest assertionRequest = challengeStore.removeAssertion(request.requestId());
        if (assertionRequest == null) {
            throw new BadRequestException("Check-in session timed out or invalid. Please try again.");
        }

        try {
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc = PublicKeyCredential
                    .parseAssertionResponseJson(request.credential());

            AssertionResult result = relyingParty.finishAssertion(
                    FinishAssertionOptions.builder()
                            .request(assertionRequest)
                            .response(pkc)
                            .build());

            if (!result.isSuccess()) {
                throw new BadRequestException("Fingerprint verification failed.");
            }

            // Step 2: Check credential belongs to the logged-in member
            String credentialId = result.getCredential().getCredentialId().getBase64Url();
            MemberPasskey passkey = memberPasskeyRepo.findByCredentialId(credentialId)
                    .orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Unrecognized device credential"));

            if (!passkey.getMember().getId().equals(member.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This fingerprint belongs to another member");
            }

            // Step 3: Check signCount did not go backwards
            long newSignCount = result.getSignatureCount();
            if (newSignCount <= passkey.getSignCount() && passkey.getSignCount() > 0) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Device verification failed: sign count did not advance");
            }
            passkey.setSignCount(newSignCount);
            passkey.setLastUsedAt(LocalDateTime.now(KOLKATA_ZONE));
            memberPasskeyRepo.save(passkey);

            // Step 4: Validate and burn kiosk token
            KioskToken kt = kioskTokenRepo.findByToken(request.kioskToken().trim())
                    .orElseThrow(() -> new BadRequestException("Invalid kiosk QR code"));

            if (kt.isUsed()) {
                throw new BadRequestException(
                        "This QR code has already been used. Please scan the current QR on the screen.");
            }

            if (kt.getExpiresAt().isBefore(LocalDateTime.now(KOLKATA_ZONE))) {
                throw new BadRequestException("This QR code has expired. Please scan the new QR on the screen.");
            }

            kt.setUsed(true);
            kt.setUsedByMember(member);
            kioskTokenRepo.save(kt);

            // Step 5: Perform check-in or check-out
            return processCheckinOrCheckout(member);

        } catch (BadRequestException | ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Fingerprint verification error: " + e.getMessage());
        }
    }

    private CheckinResultDTO processCheckinOrCheckout(Member member) {
        LocalDate today = LocalDate.now(KOLKATA_ZONE);
        Optional<Attendance> todayAttOpt = attendanceRepo.findByMemberIdAndAttendanceDate(member.getId(), today);

        String action;
        String timeStr;

        if (todayAttOpt.isPresent()) {
            Attendance att = todayAttOpt.get();
            if (att.getCheckOutTime() == null) {
                attendanceService.checkOut(member.getId());
                att.setCheckOutTime(LocalTime.now(KOLKATA_ZONE));
                att.setMethod(AttendanceMethod.FINGERPRINT);
                att.setCheckInMethod(CheckInMethod.FINGERPRINT);
                attendanceRepo.save(att);

                faceAttendanceRepo.findByUserIdAndDate(member.getUser().getId(), today).ifPresent(fa -> {
                    fa.setCheckOutTime(LocalDateTime.now(KOLKATA_ZONE));
                    faceAttendanceRepo.save(fa);
                });

                action = "CHECK_OUT";
                timeStr = att.getCheckOutTime().format(TIME_FORMATTER);
            } else {
                throw new BadRequestException("Already completed workout today");
            }
        } else {
            Attendance att = attendanceService.checkIn(member.getId());
            att.setMethod(AttendanceMethod.FINGERPRINT);
            att.setCheckInMethod(CheckInMethod.FINGERPRINT);
            attendanceRepo.save(att);

            FaceAttendance fa = new FaceAttendance();
            fa.setUserId(member.getUser().getId());
            fa.setUserName(member.getUser().getName());
            fa.setRole("MEMBER");
            fa.setDate(today);
            fa.setCheckInTime(LocalDateTime.now(KOLKATA_ZONE));
            fa.setMethod("FINGERPRINT");
            faceAttendanceRepo.save(fa);

            action = "CHECK_IN";
            timeStr = att.getCheckInTime().format(TIME_FORMATTER);
        }

        Long daysLeft = member.getExpiryDate() != null ? ChronoUnit.DAYS.between(today, member.getExpiryDate()) : 0L;
        String planName = member.getPlan() != null ? member.getPlan().getName() : "Standard Plan";

        return new CheckinResultDTO(
                action,
                timeStr,
                member.getUser().getName(),
                planName,
                member.getExpiryDate(),
                daysLeft,
                member.getStatus());
    }

    @Override
    @Transactional
    public CheckinResultDTO simulateCheckin(Long memberId) {
        if (!simulatorEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Simulator is disabled");
        }

        Member member = memberRepo.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));

        return processCheckinOrCheckout(member);
    }

    @Scheduled(fixedRate = 600000)
    @Transactional
    public void cleanupExpiredKioskTokens() {
        kioskTokenRepo.deleteByExpiresAtBefore(LocalDateTime.now(KOLKATA_ZONE));
    }
}
