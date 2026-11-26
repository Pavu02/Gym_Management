package com.gym.springapp.repository;

import com.gym.springapp.model.MissedVisitNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MissedVisitNotificationRepo extends JpaRepository<MissedVisitNotification, Long> {
    Optional<MissedVisitNotification> findByMemberIdAndDate(Long memberId, LocalDate date);
    boolean existsByMemberIdAndDate(Long memberId, LocalDate date);
    List<MissedVisitNotification> findByDate(LocalDate date);
    void deleteByMemberId(Long memberId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM MissedVisitNotification mvn WHERE mvn.member.id = :memberId")
    void deleteDirectByMemberId(@Param("memberId") Long memberId);
}
