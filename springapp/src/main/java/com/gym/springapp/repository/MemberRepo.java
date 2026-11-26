package com.gym.springapp.repository;

import com.gym.springapp.model.Member;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepo extends JpaRepository<Member, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :id")
    Optional<Member> findByIdForUpdate(@Param("id") Long id);

    Optional<Member> findByUserEmail(String email);
    Optional<Member> findByUserId(Long userId);
    Optional<Member> findByWebAuthnUserHandle(String webAuthnUserHandle);
    Optional<Member> findByUserEmailAndDeletedFalse(String email);
    Optional<Member> findByIdAndDeletedFalse(Long id);
    List<Member> findByTrainerId(Long id);
    List<Member> findByTrainerIdAndDeletedFalse(Long id);
    List<Member> findByPlanId(Long id);
    long countByStatus(String status);
    long countByDeletedFalse();
    long countByTrainerIdAndDeletedFalse(Long id);
    List<Member> findByDeletedFalse();

    @Query("SELECT COUNT(m) FROM Member m WHERE m.deleted = false AND UPPER(m.status) = UPPER(:status) AND (UPPER(:status) = 'DEACTIVATED' OR UPPER(:status) = 'SUSPENDED' OR m.active = true)")
    long countActiveByStatus(@Param("status") String status);

    @Query("SELECT COUNT(m) FROM Member m WHERE m.deleted = false AND m.active = true AND (UPPER(m.status) = 'PENDING_PAYMENT' OR UPPER(m.status) = 'PENDING' OR (m.onboardingCompleted = false AND (m.status IS NULL OR UPPER(m.status) NOT IN ('ACTIVE', 'EXPIRED', 'DEACTIVATED', 'SUSPENDED'))))")
    long countPendingOnboarding();

    @Query("SELECT p.name, COUNT(m) FROM Member m JOIN m.plan p WHERE m.deleted = false AND (m.status IS NULL OR UPPER(m.status) <> 'DEACTIVATED') GROUP BY p.name ORDER BY COUNT(m) DESC")
    List<Object[]> countMembersByPlan();

    @Query("SELECT m FROM Member m WHERE m.deleted = false AND m.joinDate BETWEEN :start AND :end")
    List<Member> findActiveJoinedBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Modifying
    @Query("UPDATE Member m SET m.version = 0 WHERE m.version IS NULL")
    void backfillNullVersions();
}
