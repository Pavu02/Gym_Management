package com.gym.springapp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "attendance", uniqueConstraints = {
        @UniqueConstraint(name = "uk_attendance_member_qr_session", columnNames = { "member_id", "qr_session_id" }),
        @UniqueConstraint(name = "uk_attendance_idempotency", columnNames = { "idempotency_key" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "check_in_time", nullable = false)
    private LocalTime checkInTime;

    @Column(name = "check_out_time", nullable = true)
    private LocalTime checkOutTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 40)
    private AttendanceMethod method = AttendanceMethod.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_in_method", length = 40)
    private CheckInMethod checkInMethod = CheckInMethod.MANUAL;

    @Column(name = "gym_id", length = 64)
    private String gymId;

    @Column(name = "branch_id", length = 64)
    private String branchId;

    @Column(name = "check_in_timestamp")
    private Instant checkInTimestamp;

    @Column(name = "verification_result", length = 40)
    private String verificationResult;

    @Column(name = "verification_method_label", length = 60)
    private String verificationMethodLabel;

    @Column(name = "risk_result", length = 120)
    private String riskResult;

    @JsonIgnore
    @Column(name = "qr_session_id")
    private Long qrSessionId;

    @JsonIgnore
    @Column(name = "device_credential_id")
    private Long deviceCredentialId;

    @JsonIgnore
    @Column(name = "idempotency_key", unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "fallback_reason", length = 255)
    private String fallbackReason;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (checkInTimestamp == null) {
            checkInTimestamp = Instant.now();
        }
    }

    // Backward compatibility helper methods
    public LocalDate getDate() {
        return attendanceDate;
    }

    public void setDate(LocalDate date) {
        this.attendanceDate = date;
    }

    public LocalTime getCheckIn() {
        return checkInTime;
    }

    public void setCheckIn(LocalTime checkIn) {
        this.checkInTime = checkIn;
    }

    public LocalTime getCheckOut() {
        return checkOutTime;
    }

    public void setCheckOut(LocalTime checkOut) {
        this.checkOutTime = checkOut;
    }
}
