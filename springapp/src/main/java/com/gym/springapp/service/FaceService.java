package com.gym.springapp.service;

import com.gym.springapp.dto.FaceProfileDescriptorDTO;
import com.gym.springapp.model.FaceProfile;

import java.util.List;

public interface FaceService {
    FaceProfile enrollFace(Long userId, List<Double> descriptor, String authEmail, boolean isAdmin);
    List<FaceProfileDescriptorDTO> getAllDescriptors();
    void deleteProfile(Long userId);
}
