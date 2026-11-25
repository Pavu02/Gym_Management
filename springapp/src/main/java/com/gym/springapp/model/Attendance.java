package com.gym.springapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "attendance", uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_attendance_date", columnNames = { "member_id", "attendance_date" })
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
    @Column(name = "method", nullable = false, length = 30)
    private AttendanceMethod method = AttendanceMethod.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_in_method")
    private CheckInMethod checkInMethod = CheckInMethod.MANUAL;

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
