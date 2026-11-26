package com.gym.springapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.springapp.dto.FaceProfileDescriptorDTO;
import com.gym.springapp.exceptions.BadRequestException;
import com.gym.springapp.exceptions.MemberDeactivatedException;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.model.FaceProfile;
import com.gym.springapp.model.Member;
import com.gym.springapp.model.MemberBiometric;
import com.gym.springapp.model.User;
import com.gym.springapp.repository.FaceProfileRepo;
import com.gym.springapp.repository.MemberBiometricRepo;
import com.gym.springapp.repository.MemberRepo;
import com.gym.springapp.repository.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class FaceServiceImpl implements FaceService {

    private static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");
    private final FaceProfileRepo faceProfileRepo;
    private final UserRepo userRepo;
    private final MemberRepo memberRepo;
    private final MemberBiometricRepo memberBiometricRepo;
    private final BiometricEncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    public FaceServiceImpl(FaceProfileRepo faceProfileRepo,
            UserRepo userRepo,
            MemberRepo memberRepo,
            MemberBiometricRepo memberBiometricRepo,
            BiometricEncryptionService encryptionService) {
        this.faceProfileRepo = faceProfileRepo;
        this.userRepo = userRepo;
        this.memberRepo = memberRepo;
        this.memberBiometricRepo = memberBiometricRepo;
        this.encryptionService = encryptionService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @Transactional
    public FaceProfile enrollFace(Long userId, List<Double> descriptor, String authEmail, boolean isAdmin) {
        if (descriptor == null || descriptor.size() != 128) {
            throw new BadRequestException("Face descriptor must contain exactly 128 float values");
        }

        User user = null;
        if (!isAdmin) {
            user = userRepo.findByEmail(authEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + authEmail));
            userId = user.getId();
        } else {
            user = userRepo.findById(userId).orElse(null);
            if (user == null) {
                // Check if memberId was passed instead of userId
                var memberOpt = memberRepo.findById(userId);
                if (memberOpt.isPresent() && memberOpt.get().getUser() != null) {
                    user = memberOpt.get().getUser();
                    userId = user.getId();
                }
            }
            if (user == null) {
                throw new ResourceNotFoundException("User not found with id: " + userId);
            }
        }

        var memberCheckOpt = memberRepo.findByUserId(userId);
        if (memberCheckOpt.isPresent()) {
            Member targetMember = memberCheckOpt.get();
            if ("DEACTIVATED".equalsIgnoreCase(targetMember.getStatus()) || !targetMember.isActive() || !user.isActive()) {
                throw new MemberDeactivatedException("MEMBER_DEACTIVATED", targetMember.getId(), user.getName(),
                        "Cannot enroll Face ID for a deactivated member account. Reactivate the member first.");
            }
        } else if (!user.isActive() || user.isDeleted()) {
            throw new MemberDeactivatedException("USER_DEACTIVATED", user.getId(), user.getName(),
                    "Cannot enroll Face ID for an inactive user account.");
        }

        String descriptorJson;
        try {
            descriptorJson = objectMapper.writeValueAsString(descriptor);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Could not serialize descriptor to JSON");
        }

        LocalDateTime now = LocalDateTime.now(KOLKATA_ZONE);
        FaceProfile profile = faceProfileRepo.findByUserId(userId)
                .orElse(new FaceProfile());

        profile.setUserId(userId);
        profile.setRole(user.getRole() != null ? user.getRole().name() : "MEMBER");
        profile.setDescriptor(descriptorJson);
        profile.setEnabled(true);
        profile.setStatus("ACTIVE");
        profile.setDisabledAt(null);
        profile.setUpdatedAt(now);
        if (profile.getId() == null) {
            profile.setCreatedAt(now);
        }

        FaceProfile savedProfile = faceProfileRepo.save(profile);

        // Also sync to MemberBiometric if user has a Member profile
        var memberOpt = memberRepo.findByUserId(userId);
        if (memberOpt.isPresent()) {
            Member member = memberOpt.get();
            member.setFaceEnabled(true);
            memberRepo.save(member);

            MemberBiometric mb = memberBiometricRepo.findByMemberId(member.getId())
                    .orElse(new MemberBiometric());
            mb.setMember(member);
            mb.setFaceDescriptorEncrypted(encryptionService.encryptDescriptor(descriptor));
            mb.setFaceEnrolledAt(now);
            mb.setConsentAt(now);
            mb.setEnabled(true);
            mb.setStatus("ACTIVE");
            mb.setDisabledAt(null);
            memberBiometricRepo.save(mb);
        }

        return savedProfile;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FaceProfileDescriptorDTO> getAllDescriptors() {
        List<FaceProfile> all = faceProfileRepo.findAll();
        List<FaceProfileDescriptorDTO> result = new ArrayList<>();

        for (FaceProfile fp : all) {
            if (!fp.isEnabled()) {
                continue;
            }
            User user = userRepo.findById(fp.getUserId()).orElse(null);
            if (user == null || user.isDeleted() || !user.isActive()) {
                continue;
            }
            if ("MEMBER".equalsIgnoreCase(fp.getRole())) {
                Member member = memberRepo.findByUserId(user.getId()).orElse(null);
                if (member != null && (member.isDeleted() || !member.isActive()
                        || "DEACTIVATED".equalsIgnoreCase(member.getStatus())
                        || !member.isFaceEnabled())) {
                    continue;
                }
            }

            try {
                List<Double> descriptor = objectMapper.readValue(fp.getDescriptor(), new TypeReference<List<Double>>() {
                });
                result.add(new FaceProfileDescriptorDTO(
                        fp.getUserId(),
                        user.getName(),
                        fp.getRole(),
                        descriptor));
            } catch (Exception ignored) {
            }
        }

        return result;
    }

    @Override
    @Transactional
    public void deleteProfile(Long userId) {
        FaceProfile fp = faceProfileRepo.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Face profile not found for user: " + userId));
        faceProfileRepo.delete(fp);

        var memberOpt = memberRepo.findByUserId(userId);
        if (memberOpt.isPresent()) {
            memberBiometricRepo.deleteByMemberId(memberOpt.get().getId());
        }
    }
}
