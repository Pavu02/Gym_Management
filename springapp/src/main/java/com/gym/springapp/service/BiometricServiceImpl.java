package com.gym.springapp.service;

import com.gym.springapp.dto.BiometricResponseDTO;
import com.gym.springapp.dto.BiometricStatusResponseDTO;
import com.gym.springapp.dto.FaceEnrollmentRequestDTO;
import com.gym.springapp.exceptions.BadRequestException;
import com.gym.springapp.exceptions.DuplicateBiometricException;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.model.FaceProfile;
import com.gym.springapp.model.FingerprintProfile;
import com.gym.springapp.model.Member;
import com.gym.springapp.model.MemberBiometric;
import com.gym.springapp.model.MemberWebAuthnCredential;
import com.gym.springapp.model.User;
import com.gym.springapp.repository.FaceProfileRepo;
import com.gym.springapp.repository.FingerprintProfileRepo;
import com.gym.springapp.repository.MemberBiometricRepo;
import com.gym.springapp.repository.MemberPasskeyRepo;
import com.gym.springapp.repository.MemberRepo;
import com.gym.springapp.repository.MemberWebAuthnCredentialRepo;
import com.gym.springapp.repository.UserRepo;
import com.gym.springapp.repository.WebAuthnCredentialRepo;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class BiometricServiceImpl implements BiometricService {

    private static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");
    private static final double FACE_MATCH_THRESHOLD = 0.45;

    private final MemberBiometricRepo memberBiometricRepo;
    private final FaceProfileRepo faceProfileRepo;
    private final FingerprintProfileRepo fingerprintProfileRepo;
    private final WebAuthnCredentialRepo webAuthnCredentialRepo;
    private final MemberPasskeyRepo memberPasskeyRepo;
    private final MemberWebAuthnCredentialRepo memberWebAuthnCredentialRepo;
    private final MemberRepo memberRepo;
    private final UserRepo userRepo;
    private final BiometricEncryptionService encryptionService;
    private final PasswordEncoder passwordEncoder;
    private final AttendanceService attendanceService;

    public BiometricServiceImpl(
            MemberBiometricRepo memberBiometricRepo,
            FaceProfileRepo faceProfileRepo,
            FingerprintProfileRepo fingerprintProfileRepo,
            WebAuthnCredentialRepo webAuthnCredentialRepo,
            MemberPasskeyRepo memberPasskeyRepo,
            MemberWebAuthnCredentialRepo memberWebAuthnCredentialRepo,
            MemberRepo memberRepo,
            UserRepo userRepo,
            BiometricEncryptionService encryptionService,
            PasswordEncoder passwordEncoder,
            AttendanceService attendanceService) {
        this.memberBiometricRepo = memberBiometricRepo;
        this.faceProfileRepo = faceProfileRepo;
        this.fingerprintProfileRepo = fingerprintProfileRepo;
        this.webAuthnCredentialRepo = webAuthnCredentialRepo;
        this.memberPasskeyRepo = memberPasskeyRepo;
        this.memberWebAuthnCredentialRepo = memberWebAuthnCredentialRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.encryptionService = encryptionService;
        this.passwordEncoder = passwordEncoder;
        this.attendanceService = attendanceService;
    }

    @Override
    @Transactional
    public BiometricResponseDTO enrollFace(FaceEnrollmentRequestDTO request, String authEmail, boolean isAdmin) {
        if (request == null) {
            throw new BadRequestException("Request body cannot be null");
        }

        if (Boolean.TRUE.equals(request.consent()) == false) {
            throw new BadRequestException("Biometric consent is mandatory for face enrollment");
        }

        List<Double> descriptor = request.descriptor();
        if (descriptor == null || descriptor.size() != 128) {
            throw new BadRequestException("Face descriptor must contain exactly 128 values");
        }

        for (Double val : descriptor) {
            if (val == null || !Double.isFinite(val)) {
                throw new BadRequestException("All 128 face descriptor values must be finite numbers");
            }
        }

        // Determine target member
        Member member;
        if (isAdmin && request.memberId() != null) {
            member = memberRepo.findById(request.memberId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("Member not found with id: " + request.memberId()));
        } else {
            User user = userRepo.findByEmail(authEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + authEmail));
            member = memberRepo.findByUserEmail(authEmail).orElseGet(() -> {
                Member newMember = new Member();
                newMember.setUser(user);
                newMember.setStatus("PENDING_PAYMENT");
                return memberRepo.save(newMember);
            });
        }

        // Block biometric enrollment while member is deactivated
        attendanceService.validateAccountNotDeactivated(member, "FACE");

        // Check if member is re-enrolling
        Optional<MemberBiometric> existingOpt = memberBiometricRepo.findByMemberId(member.getId());
        if (existingOpt.isPresent() && !isAdmin) {
            if (request.password() == null || request.password().trim().isEmpty()) {
                throw new BadRequestException("Password confirmation is required to re-enroll face biometrics");
            }
            User user = member.getUser();
            if (!passwordEncoder.matches(request.password(), user.getPassword())) {
                throw new BadCredentialsException("Invalid password. Password confirmation failed.");
            }
        }

        // Uniqueness Check: compare with every stored descriptor
        List<MemberBiometric> allBiometrics = memberBiometricRepo.findAll();
        for (MemberBiometric mb : allBiometrics) {
            // Skip the current member's record if re-enrolling
            if (mb.getMember().getId().equals(member.getId())) {
                continue;
            }

            try {
                float[] storedDescriptor = encryptionService.decryptDescriptor(mb.getFaceDescriptorEncrypted());
                double distance = encryptionService.calculateEuclideanDistance(descriptor, storedDescriptor);
                if (distance < FACE_MATCH_THRESHOLD) {
                    throw new DuplicateBiometricException("This face is already registered with another account.");
                }
            } catch (DuplicateBiometricException dbe) {
                throw dbe;
            } catch (Exception e) {
                // Log warning and continue checking other records
            }
        }

        // Encrypt descriptor with AES-GCM
        String encryptedDescriptor = encryptionService.encryptDescriptor(descriptor);
        LocalDateTime now = LocalDateTime.now(KOLKATA_ZONE);

        MemberBiometric biometric = existingOpt.orElse(new MemberBiometric());
        biometric.setMember(member);
        biometric.setFaceDescriptorEncrypted(encryptedDescriptor);
        biometric.setFaceEnrolledAt(now);
        biometric.setConsentAt(now);
        biometric.setEnabled(true);
        biometric.setStatus("ACTIVE");
        biometric.setDisabledAt(null);

        memberBiometricRepo.save(biometric);

        member.setFaceEnabled(true);
        memberRepo.save(member);

        return new BiometricResponseDTO(
                "SUCCESS",
                "Face biometric enrolled successfully",
                member.getId(),
                now);
    }

    @Override
    @Transactional(readOnly = true)
    public BiometricStatusResponseDTO getBiometricStatus(Long memberId, String authEmail, boolean isAdmin) {
        Member member;
        if (isAdmin && memberId != null) {
            member = memberRepo.findById(memberId)
                    .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));
        } else {
            member = memberRepo.findByUserEmail(authEmail)
                    .orElseThrow(
                            () -> new ResourceNotFoundException("Member profile not found for email: " + authEmail));
        }

        Optional<MemberBiometric> bioOpt = memberBiometricRepo.findByMemberId(member.getId());
        Optional<FaceProfile> faceOpt = Optional.empty();
        if (member.getUser() != null) {
            faceOpt = faceProfileRepo.findByUserId(member.getUser().getId());
        }
        if (faceOpt.isEmpty()) {
            faceOpt = faceProfileRepo.findByUserId(member.getId());
        }

        List<MemberWebAuthnCredential> allMwc = memberWebAuthnCredentialRepo
                .findByMemberIdOrderByRegisteredAtDesc(member.getId());
        List<MemberWebAuthnCredential> activeMwc = allMwc.stream().filter(MemberWebAuthnCredential::isActive).toList();

        List<com.gym.springapp.model.WebAuthnCredential> legacyWac = webAuthnCredentialRepo
                .findByMemberId(member.getId());
        long activeLegacyWac = legacyWac.stream().filter(com.gym.springapp.model.WebAuthnCredential::isActive).count();

        List<com.gym.springapp.model.MemberPasskey> memberPasskeys = memberPasskeyRepo.findByMemberId(member.getId());
        List<com.gym.springapp.model.MemberPasskey> activePasskeys = memberPasskeys.stream()
                .filter(com.gym.springapp.model.MemberPasskey::isActive).toList();

        int totalPasskeyCount = (int) (activeMwc.size() + activeLegacyWac + activePasskeys.size());

        boolean hasStoredFace = bioOpt.isPresent() || faceOpt.isPresent();
        boolean faceActive = member.isFaceEnabled()
                && (bioOpt.map(MemberBiometric::isEnabled).orElse(false)
                        || faceOpt.map(FaceProfile::isEnabled).orElse(false));
        boolean faceEnrolled = faceActive;
        boolean faceReenrollmentRequired = !faceActive && (hasStoredFace || !member.isFaceEnabled());

        LocalDateTime faceEnrolledAt = null;
        if (faceOpt.isPresent()) {
            faceEnrolledAt = faceOpt.get().getUpdatedAt() != null ? faceOpt.get().getUpdatedAt()
                    : faceOpt.get().getCreatedAt();
        } else if (bioOpt.isPresent()) {
            faceEnrolledAt = bioOpt.get().getFaceEnrolledAt();
        }

        boolean consentGiven = hasStoredFace;
        LocalDateTime consentAt = faceEnrolledAt;

        Optional<FingerprintProfile> fpOpt = Optional.empty();
        if (member.getUser() != null) {
            fpOpt = fingerprintProfileRepo.findByUserId(member.getUser().getId());
        }
        if (fpOpt.isEmpty()) {
            fpOpt = fingerprintProfileRepo.findByUserId(member.getId());
        }

        boolean fpProfileActive = fpOpt.map(FingerprintProfile::isEnabled).orElse(false);
        boolean fingerprintEnrolled = fpProfileActive || totalPasskeyCount > 0;
        boolean hasRevokedDevice = allMwc.stream().anyMatch(c -> !c.isActive())
                || memberPasskeys.stream().anyMatch(p -> !p.isActive())
                || legacyWac.stream().anyMatch(c -> !c.isActive())
                || fpOpt.map(fp -> !fp.isEnabled()).orElse(false);
        boolean biometricReenrollmentRequired = totalPasskeyCount == 0 && (!member.isFaceEnabled() || hasRevokedDevice);

        LocalDateTime fingerprintEnrolledAt = null;
        if (fpProfileActive && fpOpt.isPresent()) {
            fingerprintEnrolledAt = fpOpt.get().getUpdatedAt() != null ? fpOpt.get().getUpdatedAt()
                    : fpOpt.get().getCreatedAt();
        } else if (!activePasskeys.isEmpty()) {
            fingerprintEnrolledAt = activePasskeys.get(0).getCreatedAt();
        } else if (!activeMwc.isEmpty() && activeMwc.get(0).getRegisteredAt() != null) {
            fingerprintEnrolledAt = LocalDateTime.ofInstant(activeMwc.get(0).getRegisteredAt(), KOLKATA_ZONE);
        }

        return new BiometricStatusResponseDTO(
                faceEnrolled,
                faceEnrolledAt,
                consentGiven,
                consentAt,
                totalPasskeyCount,
                fingerprintEnrolled,
                fingerprintEnrolledAt,
                member.isFaceEnabled(),
                faceReenrollmentRequired,
                biometricReenrollmentRequired);
    }

    @Override
    @Transactional
    public void deleteBiometrics(Long memberId, String authEmail, boolean isAdmin) {
        Member member;
        if (isAdmin && memberId != null) {
            member = memberRepo.findById(memberId)
                    .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));
        } else {
            member = memberRepo.findByUserEmail(authEmail)
                    .orElseThrow(
                            () -> new ResourceNotFoundException("Member profile not found for email: " + authEmail));
        }

        memberBiometricRepo.deleteByMemberId(member.getId());
        webAuthnCredentialRepo.deleteByMemberId(member.getId());

        List<com.gym.springapp.model.MemberPasskey> memberPasskeys = memberPasskeyRepo.findByMemberId(member.getId());
        memberPasskeyRepo.deleteAll(memberPasskeys);

        if (member.getUser() != null) {
            faceProfileRepo.deleteByUserId(member.getUser().getId());
            fingerprintProfileRepo.deleteByUserId(member.getUser().getId());
        }
        faceProfileRepo.deleteByUserId(member.getId());
        fingerprintProfileRepo.deleteByUserId(member.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Member findMatchingMemberByFace(List<Double> queryDescriptor) {
        if (queryDescriptor == null || queryDescriptor.size() != 128) {
            throw new BadRequestException("Query descriptor must contain exactly 128 finite numbers");
        }

        List<MemberBiometric> all = memberBiometricRepo.findAll();
        Member closestMember = null;
        double minDistance = Double.MAX_VALUE;

        for (MemberBiometric mb : all) {
            Member candidate = mb.getMember();
            if (!mb.isEnabled() || candidate == null || candidate.isDeleted() || !candidate.isActive()
                    || "DEACTIVATED".equalsIgnoreCase(candidate.getStatus()) || !candidate.isFaceEnabled()) {
                continue;
            }
            try {
                float[] stored = encryptionService.decryptDescriptor(mb.getFaceDescriptorEncrypted());
                double dist = encryptionService.calculateEuclideanDistance(queryDescriptor, stored);
                if (dist < minDistance) {
                    minDistance = dist;
                    closestMember = candidate;
                }
            } catch (Exception e) {
                // Ignore decrypt failure for malformed legacy test records
            }
        }

        if (closestMember != null && minDistance < FACE_MATCH_THRESHOLD) {
            return closestMember;
        }

        return null;
    }
}
