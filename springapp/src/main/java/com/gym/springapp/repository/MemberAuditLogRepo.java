package com.gym.springapp.repository;

import com.gym.springapp.model.MemberAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberAuditLogRepo extends JpaRepository<MemberAuditLog, Long> {
    List<MemberAuditLog> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM MemberAuditLog mal WHERE mal.memberId = :memberId")
    void deleteDirectByMemberId(@Param("memberId") Long memberId);
}
