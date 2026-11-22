package com.gym.springapp.service;

import com.gym.springapp.automation.events.MemberChangedEvent;
import com.gym.springapp.automation.events.MemberWelcomeEvent;
import com.gym.springapp.automation.events.PaymentReceivedEvent;
import com.gym.springapp.dto.*;
import com.gym.springapp.exceptions.BadRequestException;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.model.*;
import com.gym.springapp.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class OnboardingServiceImpl implements OnboardingService {

    private final MemberRepo memberRepo;
    private final UserRepo userRepo;
    private final PlanRepo planRepo;
    private final TrainerRepo trainerRepo;
    private final PaymentRepo paymentRepo;
    private final MemberService memberService;
    private final ApplicationEventPublisher eventPublisher;

    public OnboardingServiceImpl(MemberRepo memberRepo,
            UserRepo userRepo,
            PlanRepo planRepo,
            TrainerRepo trainerRepo,
            PaymentRepo paymentRepo,
            MemberService memberService,
            ApplicationEventPublisher eventPublisher) {
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.planRepo = planRepo;
        this.trainerRepo = trainerRepo;
        this.paymentRepo = paymentRepo;
        this.memberService = memberService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public OnboardingStatusDTO getStatus(String email) {
        Optional<Member> optionalMember = memberRepo.findByUserEmail(email);
        if (optionalMember.isEmpty()) {
            return new OnboardingStatusDTO(false, false, null, null, null);
        }

        Member member = optionalMember.get();
        Long planId = member.getPlan() != null ? member.getPlan().getId() : null;
        Long trainerId = member.getTrainer() != null ? member.getTrainer().getId() : null;

        return new OnboardingStatusDTO(
                true,
                member.isOnboardingCompleted(),
                member.getStatus(),
                planId,
                trainerId);
    }

    @Override
    public List<Plan> getPlansSortedByPrice() {
        return planRepo.findAllByOrderByPriceAsc();
    }

    @Override
    public List<Trainer> getAllTrainers() {
        return trainerRepo.findAll();
    }

    @Override
    @Transactional
    public void enroll(String email, EnrollRequestDTO request) {
        if (request.planId() == null) {
            throw new BadRequestException("Plan ID is required");
        }

        Plan plan = planRepo.findById(request.planId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with id: " + request.planId()));

        Trainer trainer = null;
        if (request.trainerId() != null) {
            trainer = trainerRepo.findById(request.trainerId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("Trainer not found with id: " + request.trainerId()));
        }

        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        Member member = memberRepo.findByUserEmail(email).orElse(null);
        if (member == null) {
            member = new Member();
            member.setUser(user);
        }

        member.setPlan(plan);
        member.setTrainer(trainer);
        member.setStatus("PENDING_PAYMENT");
        member.setJoinDate(null);
        member.setExpiryDate(null);
        member.setOnboardingCompleted(false);

        Member saved = memberRepo.save(member);
        eventPublisher.publishEvent(new MemberChangedEvent(saved.getId()));
    }

    @Override
    @Transactional
    public void setupProfile(String email, ProfileSetupDTO request) {
        Member member = memberRepo.findByUserEmail(email)
                .orElseThrow(() -> new BadRequestException("Please enroll in a plan first"));

        if (member.getPlan() == null) {
            throw new BadRequestException("Please enroll in a plan first");
        }

        member.setGender(request.gender());
        member.setDateOfBirth(request.dateOfBirth());
        member.setHeightCm(request.heightCm());
        member.setWeightKg(request.weightKg());
        member.setFitnessGoal(request.fitnessGoal());
        member.setMedicalConditions(request.medicalConditions());
        member.setEmergencyContactName(request.emergencyContactName());
        member.setEmergencyContactPhone(request.emergencyContactPhone());

        Member saved = memberRepo.save(member);
        eventPublisher.publishEvent(new MemberChangedEvent(saved.getId()));
    }

    @Override
    public OnboardingSummaryDTO getSummary(String email) {
        Member member = memberRepo.findByUserEmail(email)
                .orElseThrow(() -> new BadRequestException("No active enrollment found"));

        if (member.getPlan() == null) {
            throw new BadRequestException("No plan selected yet");
        }

        Plan plan = member.getPlan();
        Trainer trainer = member.getTrainer();

        String trainerName = (trainer != null && trainer.getUser() != null) ? trainer.getUser().getName() : null;
        double trainerMonthly = (trainer != null) ? trainer.getMonthlyFee() : 0.0;
        double trainerTotal = (trainer != null) ? (trainer.getMonthlyFee() * plan.getDurationMonths()) : 0.0;
        double totalAmount = plan.getPrice() + trainerTotal;

        return new OnboardingSummaryDTO(
                plan.getName(),
                plan.getPrice(),
                plan.getDurationMonths(),
                trainerName,
                trainerTotal,
                totalAmount);
    }

    @Override
    @Transactional
    public MemberResponseDTO completePayment(String email, OnboardingPaymentDTO request) {
        Member member = memberRepo.findByUserEmail(email)
                .orElseThrow(() -> new BadRequestException("Membership enrollment not found"));

        if (member.getPlan() == null) {
            throw new BadRequestException("Please choose a plan first");
        }

        if (member.getGender() == null || member.getEmergencyContactName() == null) {
            throw new BadRequestException("Please complete your profile details first");
        }

        Plan plan = member.getPlan();
        Trainer trainer = member.getTrainer();
        double trainerFee = (trainer != null) ? (trainer.getMonthlyFee() * plan.getDurationMonths()) : 0.0;
        double totalAmount = plan.getPrice() + trainerFee;

        LocalDate today = LocalDate.now();

        Payment payment = new Payment();
        payment.setMember(member);
        payment.setAmount(totalAmount);
        payment.setMode(request.mode() != null && !request.mode().isBlank() ? request.mode().toUpperCase() : "UPI");
        payment.setStatus("PAID");
        payment.setPaymentDate(today);
        Payment savedPayment = paymentRepo.save(payment);

        member.setJoinDate(today);
        member.setExpiryDate(today.plusMonths(plan.getDurationMonths()));
        member.setStatus("ACTIVE");
        member.setOnboardingCompleted(true);

        Member savedMember = memberRepo.save(member);
        eventPublisher.publishEvent(new PaymentReceivedEvent(savedPayment.getId()));
        eventPublisher.publishEvent(new MemberChangedEvent(savedMember.getId()));
        eventPublisher.publishEvent(new MemberWelcomeEvent(savedMember.getId()));
        return memberService.toDTO(savedMember);
    }
}
