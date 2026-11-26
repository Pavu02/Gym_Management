package com.gym.springapp.service;

import com.gym.springapp.model.MemberAuditLog;
import com.gym.springapp.repository.MemberAuditLogRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class MemberAuditService {

    private final MemberAuditLogRepo auditLogRepo;

    public MemberAuditService(MemberAuditLogRepo auditLogRepo) {
        this.auditLogRepo = auditLogRepo;
    }

    @Transactional
    public void logDeactivation(Long memberId, Long performedBy, String performedByEmail, String reason) {
        if (memberId == null) {
            return;
        }
        MemberAuditLog entry = new MemberAuditLog();
        entry.setMemberId(memberId);
        entry.setAction("MEMBER_DEACTIVATED");
        entry.setPerformedBy(performedBy);
        entry.setPerformedByEmail(performedByEmail);
        entry.setResult("ADMIN_ACTION");
        entry.setReason(sanitizeReason(reason, "Deactivated by administrator"));
        entry.setCreatedAt(Instant.now());
        auditLogRepo.save(entry);
    }

    @Transactional
    public void logReactivation(Long memberId, Long performedBy, String performedByEmail, String note) {
        if (memberId == null) {
            return;
        }
        MemberAuditLog entry = new MemberAuditLog();
        entry.setMemberId(memberId);
        entry.setAction("MEMBER_REACTIVATED");
        entry.setPerformedBy(performedBy);
        entry.setPerformedByEmail(performedByEmail);
        entry.setResult("ADMIN_ACTION");
        entry.setReason(sanitizeReason(note, "Reactivated by administrator"));
        entry.setCreatedAt(Instant.now());
        auditLogRepo.save(entry);
    }

    /**
     * Runs in REQUIRES_NEW so security denial logs persist even when the check-in
     * transaction throws an exception and rolls back attendance/session writes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logDeniedEntry(Long memberId, String method, String reason) {
        if (memberId == null) {
            return;
        }
        MemberAuditLog entry = new MemberAuditLog();
        entry.setMemberId(memberId);
        entry.setAction("CHECKIN_DENIED");
        entry.setMethod(method != null && !method.isBlank() ? method : "UNKNOWN");
        entry.setResult("DENIED");
        entry.setReason(sanitizeReason(reason, "MEMBER_DEACTIVATED"));
        entry.setCreatedAt(Instant.now());
        auditLogRepo.save(entry);
    }

    @Transactional(readOnly = true)
    public List<MemberAuditLog> getAuditTrail(Long memberId) {
        return auditLogRepo.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    private String sanitizeReason(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String trimmed = raw.trim().replaceAll("[\\r\\n\\t]+", " ");
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }
}
