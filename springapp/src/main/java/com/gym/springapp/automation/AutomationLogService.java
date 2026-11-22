package com.gym.springapp.automation;

import com.gym.springapp.automation.entity.AutomationLog;
import com.gym.springapp.automation.entity.AutomationStatus;
import com.gym.springapp.automation.entity.AutomationType;
import com.gym.springapp.automation.repo.AutomationLogRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class AutomationLogService {

    private static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");
    private final AutomationLogRepo automationLogRepo;

    public AutomationLogService(AutomationLogRepo automationLogRepo) {
        this.automationLogRepo = automationLogRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AutomationLog log(AutomationType type, String target, AutomationStatus status, String message) {
        String safeMessage = message;
        if (safeMessage != null && safeMessage.length() > 1000) {
            safeMessage = safeMessage.substring(0, 997) + "...";
        }

        AutomationLog entry = new AutomationLog();
        entry.setType(type);
        entry.setTarget(target);
        entry.setStatus(status);
        entry.setMessage(safeMessage);
        entry.setCreatedAt(LocalDateTime.now(KOLKATA_ZONE));

        return automationLogRepo.save(entry);
    }
}
