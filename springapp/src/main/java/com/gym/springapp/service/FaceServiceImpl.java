package com.gym.springapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.springapp.dto.FaceProfileDescriptorDTO;
import com.gym.springapp.exceptions.BadRequestException;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.model.FaceProfile;
import com.gym.springapp.model.User;
import com.gym.springapp.repository.FaceProfileRepo;
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
    private final ObjectMapper objectMapper;

    public FaceServiceImpl(FaceProfileRepo faceProfileRepo, UserRepo userRepo, MemberRepo memberRepo) {
        this.faceProfileRepo = faceProfileRepo;
        this.userRepo = userRepo;
        this.memberRepo = memberRepo;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @Transactional
    public FaceProfile enrollFace(Long userId, List<Double> descriptor, String authEmail, boolean isAdmin) {
        if (descriptor == null || descriptor.size() != 128) {
            throw new BadRequestException("Face descriptor must contain exactly 128 float values");
        }

        User user = userRepo.findById(userId).orElse(null);
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

        if (!isAdmin && !user.getEmail().equalsIgnoreCase(authEmail)) {
            throw new BadRequestException("You are not authorized to enroll face for another user");
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
        profile.setUpdatedAt(now);
        if (profile.getId() == null) {
            profile.setCreatedAt(now);
        }

        return faceProfileRepo.save(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FaceProfileDescriptorDTO> getAllDescriptors() {
        List<FaceProfile> all = faceProfileRepo.findAll();
        List<FaceProfileDescriptorDTO> result = new ArrayList<>();

        for (FaceProfile fp : all) {
            User user = userRepo.findById(fp.getUserId()).orElse(null);
            if (user == null || user.isDeleted() || !user.isActive()) {
                continue;
            }

            try {
                List<Double> descriptor = objectMapper.readValue(fp.getDescriptor(), new TypeReference<List<Double>>() {});
                result.add(new FaceProfileDescriptorDTO(
                        fp.getUserId(),
                        user.getName(),
                        fp.getRole(),
                        descriptor
                ));
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
    }
}
