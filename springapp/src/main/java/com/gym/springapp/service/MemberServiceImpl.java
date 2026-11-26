package com.gym.springapp.service;

import com.gym.springapp.automation.events.MemberChangedEvent;
import com.gym.springapp.automation.events.MemberWelcomeEvent;
import com.gym.springapp.automation.events.PaymentReceivedEvent;
import com.gym.springapp.dto.MemberProfileUpdateDTO;
import com.gym.springapp.dto.MemberRequestDTO;
import com.gym.springapp.dto.MemberResponseDTO;
import com.gym.springapp.dto.RenewRequestDTO;
import com.gym.springapp.exceptions.DuplicateResourceException;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.model.*;
import com.gym.springapp.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
public class MemberServiceImpl implements MemberService {

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
            MissedVisitNotificationRepo missedVisitNotificationRepo) {
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
    }

    @Override
    public MemberResponseDTO toDTO(Member member) {
        if (member.getExpiryDate() != null && member.getExpiryDate().isBefore(LocalDate.now())) {
            if (!"EXPIRED".equals(member.getStatus())) {
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
            long diff = ChronoUnit.DAYS.between(LocalDate.now(), member.getExpiryDate());
            daysLeft = Math.max(0, diff);
        }

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
                daysLeft);
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
    public void deleteMember(Long id) {
        Member member = memberRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + id));

        Long userId = member.getUser() != null ? member.getUser().getId() : null;

        // 1. Delete attendance records
        attendanceRepo.deleteAll(attendanceRepo.findByMemberId(id));

        // 2. Delete payment records
        paymentRepo.deleteAll(paymentRepo.findByMemberId(id));

        // 3. Delete face biometric record (member_biometrics table - FK to members)
        memberBiometricRepo.findByMemberId(id).ifPresent(memberBiometricRepo::delete);

        // 4. Delete WebAuthn passkeys (member_passkeys table - FK to members)
        memberPasskeyRepo.deleteByMemberId(id);

        // 5. Delete WebAuthn credentials mirror (webauthn_credentials table - FK to
        // members)
        webAuthnCredentialRepo.deleteByMemberId(id);

        // 6. Delete missed visit notifications (FK to members)
        missedVisitNotificationRepo.deleteByMemberId(id);

        // 7. Delete workout plan (FK to members)
        workoutPlanRepo.findByMemberId(id).ifPresent(workoutPlanRepo::delete);

        // 8. Delete face profile by userId (no FK to members, but tidy up)
        if (userId != null) {
            faceProfileRepo.findByUserId(userId).ifPresent(faceProfileRepo::delete);
            fingerprintProfileRepo.findByUserId(userId).ifPresent(fingerprintProfileRepo::delete);
            faceAttendanceRepo.deleteAll(faceAttendanceRepo.findByUserId(userId));
        }

        // 9. Finally delete the member and user
        User user = member.getUser();
        memberRepo.delete(member);
        if (user != null) {
            userRepo.delete(user);
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
