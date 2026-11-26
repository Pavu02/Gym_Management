package com.gym.springapp.repository;

import com.gym.springapp.model.KioskToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface KioskTokenRepo extends JpaRepository<KioskToken, Long> {
    Optional<KioskToken> findByToken(String token);
    void deleteByExpiresAtBefore(LocalDateTime time);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE KioskToken kt SET kt.usedByMember = null WHERE kt.usedByMember.id = :memberId")
    void clearMemberReference(@Param("memberId") Long memberId);
}
