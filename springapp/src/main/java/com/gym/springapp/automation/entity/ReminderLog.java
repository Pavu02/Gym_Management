package com.gym.springapp.automation.entity;

import com.gym.springapp.model.Member;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reminder_logs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_reminder_log_unique", columnNames = { "member_id", "channel", "reminder_type", "expiry_date" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private ReminderChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 30)
    private ReminderType reminderType;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
