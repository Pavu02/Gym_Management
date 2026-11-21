package com.gym.springapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "face_attendance", uniqueConstraints = {
        @UniqueConstraint(name = "uk_face_attendance_user_date", columnNames = { "user_id", "date" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FaceAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_name", nullable = false, length = 100)
    private String userName;

    @Column(name = "role", nullable = false, length = 20)
    private String role; // "MEMBER" or "TRAINER"

    @Column(name = "check_in_time", nullable = false)
    private LocalDateTime checkInTime;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "method", nullable = false, length = 20)
    private String method = "FACE"; // "FACE" or "MANUAL"
}
