package com.gym.springapp.repository;

import com.gym.springapp.model.QrCheckInSession;
import com.gym.springapp.model.QrSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface QrCheckInSessionRepository extends JpaRepository<QrCheckInSession, Long> {

    Optional<QrCheckInSession> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM QrCheckInSession s WHERE s.tokenHash = :tokenHash")
    Optional<QrCheckInSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    Optional<QrCheckInSession> findBySessionCode(String sessionCode);

    Optional<QrCheckInSession> findByIdempotencyKey(String idempotencyKey);

    List<QrCheckInSession> findTop10ByBranchIdOrderByCreatedAtDesc(String branchId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE QrCheckInSession s
            SET s.status = :cancelledStatus, s.failureReason = :reason
            WHERE s.createdByAdminId = :adminId
              AND s.status IN :activeStatuses
            """)
    int cancelActiveSessionsForAdmin(
            @Param("adminId") Long adminId,
            @Param("activeStatuses") List<QrSessionStatus> activeStatuses,
            @Param("cancelledStatus") QrSessionStatus cancelledStatus,
            @Param("reason") String reason);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE QrCheckInSession s
            SET s.status = com.gym.springapp.model.QrSessionStatus.EXPIRED
            WHERE s.expiresAt < :now
              AND s.status IN :activeStatuses
            """)
    int markExpiredSessions(@Param("now") Instant now, @Param("activeStatuses") List<QrSessionStatus> activeStatuses);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE QrCheckInSession s SET s.memberId = null WHERE s.memberId = :memberId")
    void clearMemberReference(@Param("memberId") Long memberId);
}
