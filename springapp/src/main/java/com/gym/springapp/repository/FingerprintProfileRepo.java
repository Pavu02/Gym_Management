package com.gym.springapp.repository;

import com.gym.springapp.model.FingerprintProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FingerprintProfileRepo extends JpaRepository<FingerprintProfile, Long> {
    Optional<FingerprintProfile> findByUserId(Long userId);
    Optional<FingerprintProfile> findByFingerprintHash(String fingerprintHash);
    boolean existsByUserId(Long userId);
    boolean existsByFingerprintHash(String fingerprintHash);
    void deleteByUserId(Long userId);
}
