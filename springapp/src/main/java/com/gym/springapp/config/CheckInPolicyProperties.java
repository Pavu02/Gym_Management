package com.gym.springapp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "checkin.policy")
@Data
public class CheckInPolicyProperties {

    public enum LocationPolicyMode {
        OFF,
        ADVISORY,
        STRICT
    }

    private int qrExpirationSeconds = 60;
    private int challengeExpirationSeconds = 120;
    private int registrationChallengeSeconds = 180;
    private long clockSkewSeconds = 10;
    private int maxDevicesPerMember = 5;
    private boolean allowReentryAfterCheckout = true;
    private LocationPolicyMode locationPolicy = LocationPolicyMode.OFF;
    private boolean rejectOnLocationDenied = false;
    private Double branchLat;
    private Double branchLng;
    private double maxDistanceMeters = 250.0;
    private String allowedIpPrefixes = "";
    private boolean otpFallbackEnabled = false;
    private boolean allowAdminManualFallback = true;
    private String defaultGymId = "BEASTX-01";
    private String defaultBranchId = "MAIN";
    private int sessionRateLimitPerMinute = 25;
    private int verifyFailureRateLimitPerMinute = 10;
}
