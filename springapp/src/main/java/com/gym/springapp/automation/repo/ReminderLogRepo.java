package com.gym.springapp.automation.repo;

import com.gym.springapp.automation.entity.ReminderChannel;
import com.gym.springapp.automation.entity.ReminderLog;
import com.gym.springapp.automation.entity.ReminderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface ReminderLogRepo extends JpaRepository<ReminderLog, Long> {

    boolean existsByMemberIdAndChannelAndReminderTypeAndExpiryDate(
            Long memberId,
            ReminderChannel channel,
            ReminderType reminderType,
            LocalDate expiryDate
    );
}
