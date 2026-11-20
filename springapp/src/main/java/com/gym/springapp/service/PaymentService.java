package com.gym.springapp.service;

import com.gym.springapp.dto.PaymentRequestDTO;
import com.gym.springapp.model.Payment;

import java.util.List;

public interface PaymentService {
    Payment addPayment(PaymentRequestDTO request);
    List<Payment> getAllPayments();
    List<Payment> getPaymentsByMember(Long memberId);
    List<Payment> getMyPayments(String email);
    void deletePayment(Long id);
}
