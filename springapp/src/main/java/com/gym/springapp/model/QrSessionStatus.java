package com.gym.springapp.model;

public enum QrSessionStatus {
    PENDING,
    PHONE_CONNECTED,
    AWAITING_USER_VERIFICATION,
    VERIFYING,
    COMPLETED,
    FAILED,
    EXPIRED,
    CANCELLED,
    CONSUMED
}
