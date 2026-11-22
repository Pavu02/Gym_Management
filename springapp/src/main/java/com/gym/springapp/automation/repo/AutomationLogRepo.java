package com.gym.springapp.automation.repo;

import com.gym.springapp.automation.entity.AutomationLog;
import com.gym.springapp.automation.entity.AutomationStatus;
import com.gym.springapp.automation.entity.AutomationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AutomationLogRepo extends JpaRepository<AutomationLog, Long> {

    List<AutomationLog> findTop100ByOrderByCreatedAtDesc();

    long countByStatusAndCreatedAtAfter(AutomationStatus status, LocalDateTime createdAt);

    Optional<AutomationLog> findTopByTypeOrderByCreatedAtDesc(AutomationType type);
}
