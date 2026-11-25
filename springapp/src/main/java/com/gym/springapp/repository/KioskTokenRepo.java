package com.gym.springapp.repository;

import com.gym.springapp.model.KioskToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface KioskTokenRepo extends JpaRepository<KioskToken, Long> {
    Optional<KioskToken> findByToken(String token);
    void deleteByExpiresAtBefore(LocalDateTime time);
}
