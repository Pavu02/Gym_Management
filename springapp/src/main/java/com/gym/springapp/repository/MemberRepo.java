package com.gym.springapp.repository;

import com.gym.springapp.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepo extends JpaRepository<Member, Long> {
    Optional<Member> findByUserEmail(String email);
    Optional<Member> findByUserId(Long userId);
    Optional<Member> findByWebAuthnUserHandle(String webAuthnUserHandle);
    Optional<Member> findByUserEmailAndDeletedFalse(String email);
    Optional<Member> findByIdAndDeletedFalse(Long id);
    List<Member> findByTrainerId(Long id);
    List<Member> findByTrainerIdAndDeletedFalse(Long id);
    List<Member> findByPlanId(Long id);
    long countByStatus(String status);
    long countByTrainerIdAndDeletedFalse(Long id);
    List<Member> findByDeletedFalse();

    @Query("SELECT COUNT(m) FROM Member m WHERE m.deleted = false AND UPPER(m.status) = UPPER(:status)")
    long countActiveByStatus(@Param("status") String status);

    @Query("SELECT COUNT(m) FROM Member m WHERE m.deleted = false AND (UPPER(m.status) = 'PENDING_PAYMENT' OR (m.onboardingCompleted = false AND (m.status IS NULL OR UPPER(m.status) NOT IN ('ACTIVE', 'EXPIRED'))))")
    long countPendingOnboarding();

    @Query("SELECT p.name, COUNT(m) FROM Member m JOIN m.plan p WHERE m.deleted = false GROUP BY p.name ORDER BY COUNT(m) DESC")
    List<Object[]> countMembersByPlan();

    @Query("SELECT m FROM Member m WHERE m.deleted = false AND m.joinDate BETWEEN :start AND :end")
    List<Member> findActiveJoinedBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
