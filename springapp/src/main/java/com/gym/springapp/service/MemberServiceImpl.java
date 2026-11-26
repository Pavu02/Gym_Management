package com.gym.springapp.service;

import com.gym.springapp.automation.events.MemberChangedEvent;
import com.gym.springapp.automation.events.MemberWelcomeEvent;
import com.gym.springapp.automation.events.PaymentReceivedEvent;
import com.gym.springapp.automation.repo.ReminderLogRepo;
import com.gym.springapp.dto.MemberProfileUpdateDTO;
import com.gym.springapp.dto.MemberRequestDTO;
import com.gym.springapp.dto.MemberResponseDTO;
import com.gym.springapp.dto.MemberStatusActionResponseDTO;
import com.gym.springapp.dto.RenewRequestDTO;
import com.gym.springapp.exceptions.DuplicateResourceException;
import com.gym.springapp.exceptions.ForbiddenOperationException;
import com.gym.springapp.exceptions.QrSessionConflictException;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.model.*;
import com.gym.springapp.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MemberServiceImpl implements MemberService {

    private static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");
    private static final List<QrSessionStatus> ACTIVE_QR_STATUSES = List.of(
            QrSessionStatus.PENDING,
            QrSessionStatus.PHONE_CONNECTED,
            QrSessionStatus.AWAITING_USER_VERIFICATION,
            QrSessionStatus.VERIFYING);

    private final MemberRepo memberRepo;
    private final UserRepo userRepo;
    private final PlanRepo planRepo;
    private final TrainerRepo trainerRepo;
    private final AttendanceRepo attendanceRepo;
    private final PaymentRepo paymentRepo;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final MemberBiometricRepo memberBiometricRepo;
    private final MemberPasskeyRepo memberPasskeyRepo;
    private final WebAuthnCredentialRepo webAuthnCredentialRepo;
    private final FaceProfileRepo faceProfileRepo;
    private final FingerprintProfileRepo fingerprintProfileRepo;
    private final FaceAttendanceRepo faceAttendanceRepo;
    private final WorkoutPlanRepo workoutPlanRepo;
    private final MissedVisitNotificationRepo missedVisitNotificationRepo;
    private final KioskTokenRepo kioskTokenRepo;
    private final ReminderLogRepo reminderLogRepo;
    private final MemberWebAuthnCredentialRepo memberWebAuthnCredentialRepo;
    private final QrCheckInSessionRepository qrCheckInSessionRepository;
    private final WebAuthnChallengeRepository webAuthnChallengeRepository;
    private final MemberAuditLogRepo memberAuditLogRepo;
    private final MemberAuditService memberAuditService;
    private final AttendanceService attendanceService;

    public MemberServiceImpl(MemberRepo memberRepo,
            UserRepo userRepo,
            PlanRepo planRepo,
            TrainerRepo trainerRepo,
            AttendanceRepo attendanceRepo,
            PaymentRepo paymentRepo,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            MemberBiometricRepo memberBiometricRepo,
            MemberPasskeyRepo memberPasskeyRepo,
            WebAuthnCredentialRepo webAuthnCredentialRepo,
            FaceProfileRepo faceProfileRepo,
            FingerprintProfileRepo fingerprintProfileRepo,
            FaceAttendanceRepo faceAttendanceRepo,
            WorkoutPlanRepo workoutPlanRepo,
            MissedVisitNotificationRepo missedVisitNotificationRepo,
            KioskTokenRepo kioskTokenRepo,
            ReminderLogRepo reminderLogRepo,
            MemberWebAuthnCredentialRepo memberWebAuthnCredentialRepo,
            QrCheckInSessionRepository qrCheckInSessionRepository,
            WebAuthnChallengeRepository webAuthnChallengeRepository,
            MemberAuditLogRepo memberAuditLogRepo,
            MemberAuditService memberAuditService,
            AttendanceService attendanceService) {
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.planRepo = planRepo;
        this.trainerRepo = trainerRepo;
        this.attendanceRepo = attendanceRepo;
        this.paymentRepo = paymentRepo;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.memberBiometricRepo = memberBiometricRepo;
        this.memberPasskeyRepo = memberPasskeyRepo;
        this.webAuthnCredentialRepo = webAuthnCredentialRepo;
        this.faceProfileRepo = faceProfileRepo;
        this.fingerprintProfileRepo = fingerprintProfileRepo;
        this.faceAttendanceRepo = faceAttendanceRepo;
        this.workoutPlanRepo = workoutPlanRepo;
        this.missedVisitNotificationRepo = missedVisitNotificationRepo;
        this.kioskTokenRepo = kioskTokenRepo;
        this.reminderLogRepo = reminderLogRepo;
        this.memberWebAuthnCredentialRepo = memberWebAuthnCredentialRepo;
        this.qrCheckInSessionRepository = qrCheckInSessionRepository;
        this.webAuthnChallengeRepository = webAuthnChallengeRepository;
        this.memberAuditLogRepo = memberAuditLogRepo;
        this.memberAuditService = memberAuditService;
        this.attendanceService = attendanceService;
    }

    @Override
    public MemberResponseDTO toDTO(Member member) {
        String currentStatus = member.getStatus();
        boolean isProtectedFromExpiryFlip = "DEACTIVATED".equalsIgnoreCase(currentStatus)
                || "SUSPENDED".equalsIgnoreCase(currentStatus)
                || "INACTIVE".equalsIgnoreCase(currentStatus)
                || !member.isActive();

        if (!isProtectedFromExpiryFlip
                && member.getExpiryDate() != null
                && member.getExpiryDate().isBefore(LocalDate.now(KOLKATA_ZONE))) {
            if (!"EXPIRED".equalsIgnoreCase(currentStatus)) {
                member.setStatus("EXPIRED");
                member = memberRepo.save(member);
                eventPublisher.publishEvent(new MemberChangedEvent(member.getId()));
            }
        }

        String planName = member.getPlan() != null ? member.getPlan().getName() : null;
        Long planId = member.getPlan() != null ? member.getPlan().getId() : null;
        String trainerName = (member.getTrainer() != null && member.getTrainer().getUser() != null)
                ? member.getTrainer().getUser().getName()
                : null;
        Long trainerId = member.getTrainer() != null ? member.getTrainer().getId() : null;

        Double bmi = null;
        if (member.getHeightCm() > 0 && member.getWeightKg() > 0) {
            double hMeter = member.getHeightCm() / 100.0;
            bmi = Math.round((member.getWeightKg() / (hMeter * hMeter)) * 10.0) / 10.0;
        }

        Long daysLeft = null;
        if (member.getExpiryDate() != null) {
            long diff = ChronoUnit.DAYS.between(LocalDate.now(KOLKATA_ZONE), member.getExpiryDate());
            daysLeft = Math.max(0, diff);
        }

        Long userId = member.getUser() != null ? member.getUser().getId() : null;
        Optional<MemberBiometric> mbOpt = member.getId() != null
                ? memberBiometricRepo.findByMemberId(member.getId())
                : Optional.empty();
        Optional<FaceProfile> fpOpt = userId != null
                ? faceProfileRepo.findByUserId(userId)
                : Optional.empty();
        if (fpOpt.isEmpty() && member.getId() != null) {
            fpOpt = faceProfileRepo.findByUserId(member.getId());
        }

        boolean hasFaceRow = mbOpt.isPresent() || fpOpt.isPresent();
        boolean faceActive = member.isActive()
                && !"DEACTIVATED".equalsIgnoreCase(member.getStatus())
                && member.isFaceEnabled()
                && (mbOpt.map(MemberBiometric::isEnabled).orElse(false)
                        || fpOpt.map(FaceProfile::isEnabled).orElse(false));
        boolean faceReenrollmentRequired = !faceActive && (!member.isFaceEnabled() || hasFaceRow);

        Set<String> activeCredIds = new HashSet<>();
        if (member.getId() != null && member.isActive() && !"DEACTIVATED".equalsIgnoreCase(member.getStatus())) {
            memberWebAuthnCredentialRepo.findByMemberIdAndActiveTrue(member.getId())
                    .forEach(c -> activeCredIds.add(c.getCredentialId()));
            memberPasskeyRepo.findByMemberId(member.getId()).stream()
                    .filter(MemberPasskey::isActive)
                    .forEach(c -> activeCredIds.add(c.getCredentialId()));
            webAuthnCredentialRepo.findByMemberId(member.getId()).stream()
                    .filter(WebAuthnCredential::isActive)
                    .forEach(c -> activeCredIds.add(c.getCredentialId()));
        }

        boolean hasOpenAttendance = member.getId() != null
                && attendanceRepo.existsByMemberIdAndCheckOutTimeIsNull(member.getId());

        String roleStr = member.getUser() != null && member.getUser().getRole() != null
                ? member.getUser().getRole().name()
                : Role.MEMBER.name();

        return new MemberResponseDTO(
                member.getId(),
                member.getUser() != null ? member.getUser().getName() : null,
                member.getUser() != null ? member.getUser().getEmail() : null,
                member.getUser() != null ? member.getUser().getPhone() : null,
                planName,
                trainerName,
                member.getJoinDate(),
                member.getExpiryDate(),
                member.getStatus(),
                planId,
                trainerId,
                member.getGender(),
                member.getDateOfBirth(),
                member.getHeightCm(),
                member.getWeightKg(),
                bmi,
                member.getFitnessGoal(),
                member.getMedicalConditions(),
                member.getEmergencyContactName(),
                member.getEmergencyContactPhone(),
                member.isOnboardingCompleted(),
                daysLeft,
                member.isActive() && (member.getUser() == null || member.getUser().isActive()),
                member.isFaceEnabled(),
                faceActive,
                faceReenrollmentRequired,
                activeCredIds.size(),
                hasOpenAttendance,
                member.getDeactivatedAt(),
                member.getDeactivationReason(),
                roleStr,
                member.getVersion() != null ? member.getVersion() : 0L);
    }

    @Override
    @Transactional
    public MemberResponseDTO addMember(MemberRequestDTO request) {
        if (userRepo.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already exists: " + request.email());
        }

        Plan plan = null;
        if (request.planId() != null) {
            plan = planRepo.findById(request.planId())
                    .orElseThrow(() -> new ResourceNotFoundException("Plan not found with id: " + request.planId()));
        }

        Trainer trainer = null;
        if (request.trainerId() != null) {
            trainer = trainerRepo.findById(request.trainerId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("Trainer not found with id: " + request.trainerId()));
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password() != null ? request.password() : "password123"));
        user.setPhone(request.phone());
        user.setRole(Role.MEMBER);
        User savedUser = userRepo.save(user);

        LocalDate today = LocalDate.now();
        Member member = new Member();
        member.setUser(savedUser);
        member.setPlan(plan);
        member.setTrainer(trainer);
        member.setJoinDate(today);
        if (plan != null) {
            member.setExpiryDate(today.plusMonths(plan.getDurationMonths()));
        }
        member.setStatus("ACTIVE");
        member.setOnboardingCompleted(true);

        Member savedMember = memberRepo.save(member);
        eventPublisher.publishEvent(new MemberChangedEvent(savedMember.getId()));
        eventPublisher.publishEvent(new MemberWelcomeEvent(savedMember.getId()));
        return toDTO(savedMember);
    }

    @Override
    public List<MemberResponseDTO> getAllMembers() {
        return memberRepo.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public MemberResponseDTO getMemberById(Long id) {
        Member member = memberRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + id));
        return toDTO(member);
    }

    @Override
    @Transactional
    public MemberResponseDTO updateMember(Long id, MemberRequestDTO request) {
        Member member = memberRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + id));

        User user = member.getUser();
        if (user != null) {
            if (request.name() != null)
                user.setName(request.name());
            if (request.phone() != null)
                user.setPhone(request.phone());
            userRepo.save(user);
        }

        if (request.planId() != null
                && (member.getPlan() == null || !request.planId().equals(member.getPlan().getId()))) {
            Plan plan = planRepo.findById(request.planId())
                    .orElseThrow(() -> new ResourceNotFoundException("Plan not found with id: " + request.planId()));
            member.setPlan(plan);
            member.setExpiryDate(LocalDate.now().plusMonths(plan.getDurationMonths()));
        }

        if (request.trainerId() != null) {
            Trainer trainer = trainerRepo.findById(request.trainerId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("Trainer not found with id: " + request.trainerId()));
            member.setTrainer(trainer);
        } else {
            member.setTrainer(null);
        }

        Member updatedMember = memberRepo.save(member);
        eventPublisher.publishEvent(new MemberChangedEvent(updatedMember.getId()));
        return toDTO(updatedMember);
    }

    @Override
    @Transactional
    public MemberStatusActionResponseDTO deactivateMember(
            Long memberId,
            String adminEmail,
            String reason,
            Long expectedVersion) {
        User admin = userRepo.findByEmailIgnoreCase(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin account not found."));
        if (admin.getRole() != Role.ADMIN || !admin.isActive() || admin.isDeleted()) {
            throw new ForbiddenOperationException("Only active administrators can deactivate members.");
        }

        Member member = memberRepo.findByIdForUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));

        User ownerUser = member.getUser();
        if (ownerUser != null && ownerUser.getRole() == Role.ADMIN) {
            throw new ForbiddenOperationException("Admin account cannot be deactivated or deleted from the database.");
        }

        if (expectedVersion != null && member.getVersion() != null && !expectedVersion.equals(member.getVersion())) {
            throw new QrSessionConflictException(
                    "Member record was modified concurrently by another administrator. Please refresh and try again.");
        }

        if ("DEACTIVATED".equalsIgnoreCase(member.getStatus())) {
            throw new QrSessionConflictException("Member is already deactivated.");
        }

        Instant now = Instant.now();
        LocalDateTime nowLdt = LocalDateTime.now(KOLKATA_ZONE);
        LocalTime nowTime = LocalTime.now(KOLKATA_ZONE);

        // 1. Automatically close any open attendance sessions cleanly
        boolean closedOpenAttendance = false;
        List<Attendance> openAttendances = attendanceRepo.findByMemberIdAndCheckOutTimeIsNull(member.getId());
        if (!openAttendances.isEmpty()) {
            closedOpenAttendance = true;
            for (Attendance att : openAttendances) {
                att.setCheckOutTime(nowTime);
                att.setCheckOutTimestamp(now);
                att.setCheckoutReason("ADMIN_DEACTIVATION");
                att.setCheckedOutBy(admin.getId());
            }
            attendanceRepo.saveAll(openAttendances);
        }

        if (ownerUser != null) {
            List<FaceAttendance> openFaceAttendances = faceAttendanceRepo.findByUserIdAndCheckOutTimeIsNull(ownerUser.getId());
            if (!openFaceAttendances.isEmpty()) {
                closedOpenAttendance = true;
                for (FaceAttendance fa : openFaceAttendances) {
                    fa.setCheckOutTime(nowLdt);
                }
                faceAttendanceRepo.saveAll(openFaceAttendances);
            }
        }

        // 2. Revoke active WebAuthn / passkey / fingerprint credentials (keep audit rows)
        List<MemberWebAuthnCredential> activeMwc = memberWebAuthnCredentialRepo.findByMemberIdAndActiveTrue(member.getId());
        for (MemberWebAuthnCredential cred : activeMwc) {
            cred.setActive(false);
            cred.setRevokedAt(now);
        }
        memberWebAuthnCredentialRepo.saveAll(activeMwc);

        List<MemberPasskey> passkeys = memberPasskeyRepo.findByMemberId(member.getId());
        for (MemberPasskey mp : passkeys) {
            if (mp.isActive()) {
                mp.setActive(false);
                mp.setRevokedAt(nowLdt);
            }
        }
        memberPasskeyRepo.saveAll(passkeys);

        List<WebAuthnCredential> legacyWac = webAuthnCredentialRepo.findByMemberId(member.getId());
        for (WebAuthnCredential wc : legacyWac) {
            if (wc.isActive()) {
                wc.setActive(false);
                wc.setRevokedAt(nowLdt);
            }
        }
        webAuthnCredentialRepo.saveAll(legacyWac);

        if (ownerUser != null) {
            fingerprintProfileRepo.findByUserId(ownerUser.getId()).ifPresent(fp -> {
                fp.setEnabled(false);
                fp.setStatus("DISABLED");
                fp.setDisabledAt(nowLdt);
                fingerprintProfileRepo.save(fp);
            });
        }
        fingerprintProfileRepo.findByUserId(member.getId()).ifPresent(fp -> {
            fp.setEnabled(false);
            fp.setStatus("DISABLED");
            fp.setDisabledAt(nowLdt);
            fingerprintProfileRepo.save(fp);
        });

        // 3. Disable Face ID without deleting templates
        member.setFaceEnabled(false);
        memberBiometricRepo.findByMemberId(member.getId()).ifPresent(mb -> {
            mb.setEnabled(false);
            mb.setStatus("DISABLED");
            mb.setDisabledAt(nowLdt);
            memberBiometricRepo.save(mb);
        });

        if (ownerUser != null) {
            faceProfileRepo.findByUserId(ownerUser.getId()).ifPresent(fp -> {
                fp.setEnabled(false);
                fp.setStatus("DISABLED");
                fp.setDisabledAt(nowLdt);
                faceProfileRepo.save(fp);
            });
        }
        faceProfileRepo.findByUserId(member.getId()).ifPresent(fp -> {
            fp.setEnabled(false);
            fp.setStatus("DISABLED");
            fp.setDisabledAt(nowLdt);
            faceProfileRepo.save(fp);
        });

        // 4. Invalidate pending WebAuthn challenges and cancel any member-bound QR sessions
        webAuthnChallengeRepository.invalidatePendingForMember(member.getId(), now);
        qrCheckInSessionRepository.cancelActiveSessionsForMember(
                member.getId(),
                ACTIVE_QR_STATUSES,
                QrSessionStatus.CANCELLED,
                "Member deactivated");

        // Reload managed entity cleanly after modifying queries cleared persistence context
        member = memberRepo.findByIdForUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));
        ownerUser = member.getUser();

        // 5. Update Member + User state and invalidate existing JWT tokens
        String cleanReason = (reason != null && !reason.isBlank())
                ? (reason.trim().length() > 500 ? reason.trim().substring(0, 500) : reason.trim())
                : "Deactivated by administrator";

        member.setStatus("DEACTIVATED");
        member.setActive(false);
        member.setFaceEnabled(false);
        member.setDeactivatedAt(now);
        member.setDeactivatedBy(admin.getId());
        member.setDeactivationReason(cleanReason);

        if (ownerUser != null) {
            ownerUser.setActive(false);
            ownerUser.setTokenVersion(ownerUser.getEffectiveTokenVersion() + 1L);
            userRepo.save(ownerUser);
        }

        Member saved = memberRepo.saveAndFlush(member);

        // 6. Audit Log & Events
        memberAuditService.logDeactivation(saved.getId(), admin.getId(), admin.getEmail(), cleanReason);
        eventPublisher.publishEvent(new MemberChangedEvent(saved.getId()));

        return new MemberStatusActionResponseDTO(
                true,
                saved.getId(),
                "DEACTIVATED",
                "Member deactivated successfully",
                saved.getVersion(),
                closedOpenAttendance,
                false);
    }

    @Override
    @Transactional
    public MemberStatusActionResponseDTO reactivateMember(
            Long memberId,
            String adminEmail,
            Long expectedVersion) {
        User admin = userRepo.findByEmailIgnoreCase(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin account not found."));
        if (admin.getRole() != Role.ADMIN || !admin.isActive() || admin.isDeleted()) {
            throw new ForbiddenOperationException("Only active administrators can reactivate members.");
        }

        Member member = memberRepo.findByIdForUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));

        if (expectedVersion != null && member.getVersion() != null && !expectedVersion.equals(member.getVersion())) {
            throw new QrSessionConflictException(
                    "Member record was modified concurrently by another administrator. Please refresh and try again.");
        }

        if (!"DEACTIVATED".equalsIgnoreCase(member.getStatus())) {
            throw new QrSessionConflictException("Member is not currently deactivated.");
        }

        LocalDate today = LocalDate.now(KOLKATA_ZONE);
        String restoredStatus;
        if (member.getExpiryDate() != null && member.getExpiryDate().isBefore(today)) {
            restoredStatus = "EXPIRED";
        } else if (member.getPlan() == null || !member.isOnboardingCompleted()) {
            restoredStatus = "PENDING_PAYMENT";
        } else {
            restoredStatus = "ACTIVE";
        }

        member.setStatus(restoredStatus);
        member.setActive(true);
        member.setDeactivatedAt(null);
        member.setDeactivatedBy(null);
        member.setDeactivationReason(null);
        // Security: Never automatically re-enable Face ID or restore previously revoked passkeys.
        member.setFaceEnabled(false);

        User ownerUser = member.getUser();
        if (ownerUser != null) {
            ownerUser.setActive(true);
            userRepo.save(ownerUser);
        }

        Member saved = memberRepo.saveAndFlush(member);

        memberAuditService.logReactivation(
                saved.getId(),
                admin.getId(),
                admin.getEmail(),
                "Reactivated with status " + restoredStatus + "; biometric re-enrollment required");
        eventPublisher.publishEvent(new MemberChangedEvent(saved.getId()));

        return new MemberStatusActionResponseDTO(
                true,
                saved.getId(),
                restoredStatus,
                "Member reactivated with status " + restoredStatus + ". Biometric re-enrollment is required.",
                saved.getVersion(),
                false,
                true);
    }

    @Override
    @Transactional
    public void deleteMember(Long id) {
        Member member = memberRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + id));

        User ownerUser = member.getUser();
        if (ownerUser != null && ownerUser.getRole() == Role.ADMIN) {
            throw new ForbiddenOperationException("Admin account cannot be deleted from the database.");
        }

        Long userId = ownerUser != null ? ownerUser.getId() : null;

        if (userId != null) {
            faceProfileRepo.findByUserId(userId).ifPresent(faceProfileRepo::delete);
            fingerprintProfileRepo.findByUserId(userId).ifPresent(fingerprintProfileRepo::delete);
            faceAttendanceRepo.deleteAll(faceAttendanceRepo.findByUserId(userId));
            faceAttendanceRepo.flush();
        }

        attendanceRepo.deleteDirectByMemberId(id);
        paymentRepo.deleteDirectByMemberId(id);
        memberBiometricRepo.deleteDirectByMemberId(id);
        memberPasskeyRepo.deleteDirectByMemberId(id);
        webAuthnCredentialRepo.deleteDirectByMemberId(id);
        missedVisitNotificationRepo.deleteDirectByMemberId(id);
        workoutPlanRepo.deleteDirectByMemberId(id);
        reminderLogRepo.deleteDirectByMemberId(id);
        kioskTokenRepo.clearMemberReference(id);
        memberWebAuthnCredentialRepo.deleteDirectByMemberId(id);
        webAuthnChallengeRepository.deleteDirectByMemberId(id);
        qrCheckInSessionRepository.clearMemberReference(id);
        memberAuditLogRepo.deleteDirectByMemberId(id);

        Member targetMember = memberRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + id));
        User targetUser = targetMember.getUser();
        memberRepo.delete(targetMember);
        memberRepo.flush();

        if (targetUser != null) {
            userRepo.delete(targetUser);
            userRepo.flush();
        }
    }

    @Override
    @Transactional
    public MemberResponseDTO assignTrainer(Long memberId, Long trainerId) {
        Member member = memberRepo.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));

        Trainer trainer = trainerRepo.findById(trainerId)
                .orElseThrow(() -> new ResourceNotFoundException("Trainer not found with id: " + trainerId));

        member.setTrainer(trainer);
        Member saved = memberRepo.save(member);
        eventPublisher.publishEvent(new MemberChangedEvent(saved.getId()));
        return toDTO(saved);
    }

    @Override
    public MemberResponseDTO getMyProfile(String email) {
        Member member = memberRepo.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found for email: " + email));
        return toDTO(member);
    }

    @Override
    public List<MemberResponseDTO> getMembersOfTrainer(String email) {
        Trainer trainer = trainerRepo.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Trainer not found for email: " + email));

        return memberRepo.findByTrainerId(trainer.getId()).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MemberResponseDTO updateMyProfile(String email, MemberProfileUpdateDTO request) {
        Member member = memberRepo.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found for email: " + email));
        attendanceService.validateAccountNotDeactivated(member, "PROFILE");

        User user = member.getUser();
        if (user != null) {
            if (request.name() != null && !request.name().isBlank())
                user.setName(request.name());
            if (request.phone() != null && !request.phone().isBlank())
                user.setPhone(request.phone());
            userRepo.save(user);
        }

        if (request.gender() != null)
            member.setGender(request.gender());
        if (request.dateOfBirth() != null)
            member.setDateOfBirth(request.dateOfBirth());
        if (request.heightCm() > 0)
            member.setHeightCm(request.heightCm());
        if (request.weightKg() > 0)
            member.setWeightKg(request.weightKg());
        if (request.fitnessGoal() != null)
            member.setFitnessGoal(request.fitnessGoal());
        if (request.medicalConditions() != null)
            member.setMedicalConditions(request.medicalConditions());
        if (request.emergencyContactName() != null)
            member.setEmergencyContactName(request.emergencyContactName());
        if (request.emergencyContactPhone() != null)
            member.setEmergencyContactPhone(request.emergencyContactPhone());

        Member saved = memberRepo.save(member);
        eventPublisher.publishEvent(new MemberChangedEvent(saved.getId()));
        return toDTO(saved);
    }

    @Override
    @Transactional
    public MemberResponseDTO changeTrainer(String email, Long trainerId) {
        Member member = memberRepo.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found for email: " + email));
        attendanceService.validateAccountNotDeactivated(member, "PROFILE");

        Trainer trainer = trainerRepo.findById(trainerId)
                .orElseThrow(() -> new ResourceNotFoundException("Trainer not found with id: " + trainerId));

        member.setTrainer(trainer);
        Member saved = memberRepo.save(member);
        eventPublisher.publishEvent(new MemberChangedEvent(saved.getId()));
        return toDTO(saved);
    }

    @Override
    @Transactional
    public MemberResponseDTO removeTrainer(String email) {
        Member member = memberRepo.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found for email: " + email));
        attendanceService.validateAccountNotDeactivated(member, "PROFILE");

        member.setTrainer(null);
        Member saved = memberRepo.save(member);
        eventPublisher.publishEvent(new MemberChangedEvent(saved.getId()));
        return toDTO(saved);
    }

    @Override
    @Transactional
    public MemberResponseDTO renewMembership(String email, RenewRequestDTO request) {
        Member member = memberRepo.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found for email: " + email));
        attendanceService.validateAccountNotDeactivated(member, "RENEW");

        Plan plan = planRepo.findById(request.planId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with id: " + request.planId()));

        LocalDate today = LocalDate.now();
        LocalDate baseDate = (member.getExpiryDate() != null && member.getExpiryDate().isAfter(today))
                ? member.getExpiryDate()
                : today;

        member.setPlan(plan);
        member.setExpiryDate(baseDate.plusMonths(plan.getDurationMonths()));
        member.setStatus("ACTIVE");
        if (member.getJoinDate() == null) {
            member.setJoinDate(today);
        }
        member.setOnboardingCompleted(true);

        Payment payment = new Payment();
        payment.setMember(member);
        payment.setAmount(plan.getPrice());
        payment.setMode(request.mode() != null ? request.mode().toUpperCase() : "UPI");
        payment.setStatus("PAID");
        payment.setPaymentDate(today);
        Payment savedPayment = paymentRepo.save(payment);

        Member savedMember = memberRepo.save(member);
        eventPublisher.publishEvent(new PaymentReceivedEvent(savedPayment.getId()));
        eventPublisher.publishEvent(new MemberChangedEvent(savedMember.getId()));

        return toDTO(savedMember);
    }
}
