package com.gym.springapp.service;

import com.gym.springapp.dto.MemberRequestDTO;
import com.gym.springapp.dto.MemberResponseDTO;
import com.gym.springapp.exceptions.DuplicateResourceException;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.model.*;
import com.gym.springapp.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MemberServiceImpl implements MemberService {

    private final MemberRepo memberRepo;
    private final UserRepo userRepo;
    private final PlanRepo planRepo;
    private final TrainerRepo trainerRepo;
    private final AttendanceRepo attendanceRepo;
    private final PaymentRepo paymentRepo;
    private final PasswordEncoder passwordEncoder;

    public MemberServiceImpl(MemberRepo memberRepo,
                             UserRepo userRepo,
                             PlanRepo planRepo,
                             TrainerRepo trainerRepo,
                             AttendanceRepo attendanceRepo,
                             PaymentRepo paymentRepo,
                             PasswordEncoder passwordEncoder) {
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.planRepo = planRepo;
        this.trainerRepo = trainerRepo;
        this.attendanceRepo = attendanceRepo;
        this.paymentRepo = paymentRepo;
        this.passwordEncoder = passwordEncoder;
    }

    private MemberResponseDTO toDTO(Member member) {
        if (member.getExpiryDate() != null && member.getExpiryDate().isBefore(LocalDate.now())) {
            if (!"EXPIRED".equals(member.getStatus())) {
                member.setStatus("EXPIRED");
                member = memberRepo.save(member);
            }
        }
        String planName = member.getPlan() != null ? member.getPlan().getName() : null;
        String trainerName = (member.getTrainer() != null && member.getTrainer().getUser() != null)
                ? member.getTrainer().getUser().getName() : null;

        return new MemberResponseDTO(
                member.getId(),
                member.getUser() != null ? member.getUser().getName() : null,
                member.getUser() != null ? member.getUser().getEmail() : null,
                member.getUser() != null ? member.getUser().getPhone() : null,
                planName,
                trainerName,
                member.getJoinDate(),
                member.getExpiryDate(),
                member.getStatus()
        );
    }

    @Override
    @Transactional
    public MemberResponseDTO addMember(MemberRequestDTO request) {
        if (userRepo.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already exists: " + request.email());
        }

        Plan plan = planRepo.findById(request.planId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with id: " + request.planId()));

        Trainer trainer = null;
        if (request.trainerId() != null) {
            trainer = trainerRepo.findById(request.trainerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Trainer not found with id: " + request.trainerId()));
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
        member.setExpiryDate(today.plusMonths(plan.getDurationMonths()));
        member.setStatus("ACTIVE");

        Member savedMember = memberRepo.save(member);
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
            if (request.name() != null) user.setName(request.name());
            if (request.phone() != null) user.setPhone(request.phone());
            userRepo.save(user);
        }

        if (request.planId() != null && (member.getPlan() == null || !request.planId().equals(member.getPlan().getId()))) {
            Plan plan = planRepo.findById(request.planId())
                    .orElseThrow(() -> new ResourceNotFoundException("Plan not found with id: " + request.planId()));
            member.setPlan(plan);
            member.setExpiryDate(LocalDate.now().plusMonths(plan.getDurationMonths()));
        }

        if (request.trainerId() != null) {
            Trainer trainer = trainerRepo.findById(request.trainerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Trainer not found with id: " + request.trainerId()));
            member.setTrainer(trainer);
        } else {
            member.setTrainer(null);
        }

        Member updatedMember = memberRepo.save(member);
        return toDTO(updatedMember);
    }

    @Override
    @Transactional
    public void deleteMember(Long id) {
        Member member = memberRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + id));

        List<Attendance> attendances = attendanceRepo.findByMemberId(id);
        attendanceRepo.deleteAll(attendances);

        List<Payment> payments = paymentRepo.findByMemberId(id);
        paymentRepo.deleteAll(payments);

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
        return toDTO(memberRepo.save(member));
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
}
