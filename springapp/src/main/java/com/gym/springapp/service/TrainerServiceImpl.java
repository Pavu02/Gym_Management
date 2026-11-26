package com.gym.springapp.service;

import com.gym.springapp.dto.TrainerRequestDTO;
import com.gym.springapp.exceptions.DuplicateResourceException;
import com.gym.springapp.exceptions.ForbiddenOperationException;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.model.Member;
import com.gym.springapp.model.Role;
import com.gym.springapp.model.Trainer;
import com.gym.springapp.model.User;
import com.gym.springapp.repository.MemberRepo;
import com.gym.springapp.repository.TrainerRepo;
import com.gym.springapp.repository.UserRepo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TrainerServiceImpl implements TrainerService {

    private final TrainerRepo trainerRepo;
    private final UserRepo userRepo;
    private final MemberRepo memberRepo;
    private final PasswordEncoder passwordEncoder;

    public TrainerServiceImpl(TrainerRepo trainerRepo,
            UserRepo userRepo,
            MemberRepo memberRepo,
            PasswordEncoder passwordEncoder) {
        this.trainerRepo = trainerRepo;
        this.userRepo = userRepo;
        this.memberRepo = memberRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public Trainer addTrainer(TrainerRequestDTO request) {
        if (userRepo.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already exists: " + request.email());
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password() != null ? request.password() : "password123"));
        user.setPhone(request.phone());
        user.setRole(Role.TRAINER);
        User savedUser = userRepo.save(user);

        Trainer trainer = new Trainer();
        trainer.setUser(savedUser);
        trainer.setSpecialization(request.specialization());
        trainer.setExperienceYears(request.experienceYears());
        trainer.setBio(request.bio());
        trainer.setRating(request.rating() != null && request.rating() > 0 ? request.rating() : 4.5);
        trainer.setMonthlyFee(request.monthlyFee() != null ? request.monthlyFee() : 0.0);

        return trainerRepo.save(trainer);
    }

    @Override
    public List<Trainer> getAllTrainers() {
        return trainerRepo.findAll();
    }

    @Override
    public Trainer getTrainerById(Long id) {
        return trainerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trainer not found with id: " + id));
    }

    @Override
    @Transactional
    public Trainer updateTrainer(Long id, TrainerRequestDTO request) {
        Trainer trainer = getTrainerById(id);
        User user = trainer.getUser();

        if (user != null) {
            if (request.name() != null)
                user.setName(request.name());
            if (request.phone() != null)
                user.setPhone(request.phone());
            userRepo.save(user);
        }

        if (request.specialization() != null)
            trainer.setSpecialization(request.specialization());
        trainer.setExperienceYears(request.experienceYears());
        if (request.bio() != null)
            trainer.setBio(request.bio());
        if (request.rating() != null && request.rating() > 0)
            trainer.setRating(request.rating());
        if (request.monthlyFee() != null)
            trainer.setMonthlyFee(request.monthlyFee());

        return trainerRepo.save(trainer);
    }

    @Override
    @Transactional
    public void deleteTrainer(Long id) {
        Trainer trainer = getTrainerById(id);

        User user = trainer.getUser();
        if (user != null && user.getRole() == Role.ADMIN) {
            throw new ForbiddenOperationException("Admin account cannot be deleted from the database.");
        }

        List<Member> members = memberRepo.findByTrainerId(id);
        for (Member member : members) {
            member.setTrainer(null);
            memberRepo.save(member);
        }

        trainerRepo.delete(trainer);
        if (user != null) {
            userRepo.delete(user);
        }
    }
}
