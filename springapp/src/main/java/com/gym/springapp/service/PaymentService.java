package com.gym.springapp.service;

import com.gym.springapp.dto.PaymentReportDTO;
import com.gym.springapp.dto.PaymentRequestDTO;
import com.gym.springapp.model.Payment;

import java.time.LocalDate;
import java.util.List;

public interface PaymentService {
    Payment addPayment(PaymentRequestDTO request);
    Payment updatePaymentStatus(Long id, String status);
    List<Payment> getAllPayments();
    List<Payment> getPaymentsByMember(Long memberId);
    List<Payment> getMyPayments(String email);
    PaymentReportDTO getPaymentReport(LocalDate from, LocalDate to, String mode, String status);
    String exportPaymentReportCsv(LocalDate from, LocalDate to, String mode, String status);
    void deletePayment(Long id);
}
