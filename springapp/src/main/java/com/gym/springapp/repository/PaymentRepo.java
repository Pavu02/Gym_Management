package com.gym.springapp.repository;

import com.gym.springapp.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepo extends JpaRepository<Payment, Long> {
    List<Payment> findByMemberId(Long id);

    @Query("SELECT COALESCE(SUM(p.amount),0) FROM Payment p WHERE p.status='PAID' AND MONTH(p.paymentDate)=:month AND YEAR(p.paymentDate)=:year")
    Double sumPaidByMonth(@Param("month") int month, @Param("year") int year);

    @Query("SELECT COALESCE(SUM(p.amount),0) FROM Payment p WHERE UPPER(p.status) IN ('PAID','SUCCESS') AND MONTH(p.paymentDate)=:month AND YEAR(p.paymentDate)=:year")
    Double sumPaidOrSuccessByMonth(@Param("month") int month, @Param("year") int year);
}
