package com.gym.springapp.dto;

import java.time.LocalDate;
import java.util.List;

public record PaymentReportDTO(
        double totalCollected,
        double totalPending,
        long paidCount,
        long pendingCount,
        long totalTransactions,
        double averageTicket,
        double collectionRate,
        List<ModeBreakdown> byMode,
        List<MonthlyCollection> byMonth,
        List<TransactionRow> transactions
) {
    public record ModeBreakdown(String mode, long count, double amount) {}
    public record MonthlyCollection(String label, double paidAmount, double pendingAmount, long count) {}
    public record TransactionRow(
            Long id,
            String invoiceNo,
            String memberName,
            String memberEmail,
            String planName,
            double amount,
            String mode,
            String status,
            LocalDate paymentDate
    ) {}
}
