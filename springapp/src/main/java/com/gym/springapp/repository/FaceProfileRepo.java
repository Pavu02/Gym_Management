package com.gym.springapp.repository;

import com.gym.springapp.model.FaceProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FaceProfileRepo extends JpaRepository<FaceProfile, Long> {
    Optional<FaceProfile> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
