package com.gym.springapp.repository;

import com.gym.springapp.model.WebAuthnChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface WebAuthnChallengeRepository extends JpaRepository<WebAuthnChallenge, Long> {

    Optional<WebAuthnChallenge> findByChallengeId(String challengeId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE WebAuthnChallenge c
            SET c.consumed = true, c.consumedAt = :now
            WHERE c.challengeId = :challengeId
              AND c.operation = :operation
              AND c.memberId = :memberId
              AND (:qrSessionId IS NULL OR c.qrSessionId = :qrSessionId)
              AND c.consumed = false
              AND c.expiresAt > :now
            """)
    int consumeValidChallenge(
            @Param("challengeId") String challengeId,
            @Param("operation") String operation,
            @Param("memberId") Long memberId,
            @Param("qrSessionId") Long qrSessionId,
            @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM WebAuthnChallenge c WHERE c.expiresAt < :cutoff OR (c.consumed = true AND c.consumedAt < :cutoff)")
    int deleteStaleChallenges(@Param("cutoff") Instant cutoff);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE WebAuthnChallenge c SET c.consumed = true, c.consumedAt = :now WHERE c.memberId = :memberId AND c.consumed = false")
    int invalidatePendingForMember(@Param("memberId") Long memberId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM WebAuthnChallenge c WHERE c.memberId = :memberId")
    void deleteDirectByMemberId(@Param("memberId") Long memberId);
}
