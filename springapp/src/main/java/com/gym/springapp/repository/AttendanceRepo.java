package com.gym.springapp.repository;

import com.gym.springapp.model.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepo extends JpaRepository<Attendance, Long> {
    List<Attendance> findByMemberId(Long id);

    Optional<Attendance> findByMemberIdAndAttendanceDate(Long id, LocalDate attendanceDate);

    Optional<Attendance> findFirstByMemberIdAndAttendanceDateOrderByIdDesc(Long id, LocalDate attendanceDate);

    Optional<Attendance> findFirstByMemberIdAndCheckOutTimeIsNullOrderByIdDesc(Long memberId);

    List<Attendance> findByMemberIdAndCheckOutTimeIsNull(Long memberId);

    boolean existsByMemberIdAndCheckOutTimeIsNull(Long memberId);

    List<Attendance> findAllByMemberIdAndAttendanceDateOrderByIdDesc(Long id, LocalDate attendanceDate);

    Optional<Attendance> findByIdempotencyKey(String idempotencyKey);

    boolean existsByMemberIdAndQrSessionId(Long memberId, Long qrSessionId);

    long countByAttendanceDate(LocalDate attendanceDate);

    List<Attendance> findByAttendanceDate(LocalDate attendanceDate);

    List<Attendance> findByAttendanceDateBetween(LocalDate start, LocalDate end);

    @Query("SELECT a.attendanceDate, COUNT(a) FROM Attendance a WHERE a.member.deleted = false AND a.attendanceDate BETWEEN :start AND :end GROUP BY a.attendanceDate")
    List<Object[]> countActiveByDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT a.method, COUNT(a) FROM Attendance a WHERE a.member.deleted = false AND a.attendanceDate BETWEEN :start AND :end GROUP BY a.method")
    List<Object[]> countActiveByMethodBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT a FROM Attendance a WHERE a.member.deleted = false AND a.attendanceDate BETWEEN :start AND :end")
    List<Attendance> findActiveByAttendanceDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    default Optional<Attendance> findByMemberIdAndDate(Long id, LocalDate date) {
        return findByMemberIdAndAttendanceDate(id, date);
    }

    default long countByDate(LocalDate date) {
        return countByAttendanceDate(date);
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Attendance a WHERE a.member.id = :memberId")
    void deleteDirectByMemberId(@Param("memberId") Long memberId);
}
