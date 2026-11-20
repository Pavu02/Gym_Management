package com.gym.springapp.service;

import com.gym.springapp.config.SecurityUtils;
import com.gym.springapp.model.Member;
import com.gym.springapp.model.Trainer;
import com.gym.springapp.repository.MemberRepo;
import com.gym.springapp.repository.TrainerRepo;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service("accessService")
public class AccessService {

    private final MemberRepo memberRepo;
    private final TrainerRepo trainerRepo;

    public AccessService(MemberRepo memberRepo, TrainerRepo trainerRepo) {
        this.memberRepo = memberRepo;
        this.trainerRepo = trainerRepo;
    }

    public boolean canAccessMember(Long memberId) {
        if (SecurityUtils.isAdmin()) {
            return true;
        }
        if (memberId == null) {
            return false;
        }

        Optional<Member> memberOpt = memberRepo.findByIdAndDeletedFalse(memberId);
        if (memberOpt.isEmpty()) {
            return false;
        }

        Member member = memberOpt.get();
        String currentEmail = SecurityUtils.getCurrentUserEmail();
        if (currentEmail == null) {
            return false;
        }

        if (SecurityUtils.isTrainer()) {
            return member.getTrainer() != null &&
                    member.getTrainer().getUser() != null &&
                    currentEmail.equalsIgnoreCase(member.getTrainer().getUser().getEmail());
        }

        if (SecurityUtils.isMember()) {
            return member.getUser() != null &&
                    currentEmail.equalsIgnoreCase(member.getUser().getEmail());
        }

        return false;
    }

    public boolean canAccessTrainer(Long trainerId) {
        if (SecurityUtils.isAdmin()) {
            return true;
        }
        if (trainerId == null) {
            return false;
        }

        Optional<Trainer> trainerOpt = trainerRepo.findByIdAndDeletedFalse(trainerId);
        if (trainerOpt.isEmpty()) {
            return false;
        }

        Trainer trainer = trainerOpt.get();
        String currentEmail = SecurityUtils.getCurrentUserEmail();
        if (currentEmail == null) {
            return false;
        }

        if (SecurityUtils.isTrainer()) {
            return trainer.getUser() != null &&
                    currentEmail.equalsIgnoreCase(trainer.getUser().getEmail());
        }

        if (SecurityUtils.isMember()) {
            Optional<Member> memberOpt = memberRepo.findByUserEmailAndDeletedFalse(currentEmail);
            return memberOpt.isPresent() &&
                    memberOpt.get().getTrainer() != null &&
                    trainerId.equals(memberOpt.get().getTrainer().getId());
        }

        return false;
    }
}
