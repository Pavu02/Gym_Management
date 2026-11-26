package com.gym.springapp.service;

import com.gym.springapp.config.CheckInPolicyProperties;
import com.gym.springapp.exceptions.VerificationRuleException;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class LocationRiskService {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private final CheckInPolicyProperties policy;

    public LocationRiskService(CheckInPolicyProperties policy) {
        this.policy = policy;
    }

    public String evaluateLocationAndNetwork(
            Double clientLat,
            Double clientLng,
            String wifiSsid,
            String clientIp) {
        if (policy.getLocationPolicy() == CheckInPolicyProperties.LocationPolicyMode.OFF) {
            return checkNetworkMatch(clientIp) ? "NETWORK_VERIFIED" : "STANDARD";
        }

        boolean ipMatch = checkNetworkMatch(clientIp);
        if (clientLat == null || clientLng == null) {
            if (ipMatch) {
                return "BRANCH_NETWORK_VERIFIED";
            }
            if (policy.isRejectOnLocationDenied()
                    || policy.getLocationPolicy() == CheckInPolicyProperties.LocationPolicyMode.STRICT) {
                throw new VerificationRuleException(
                        "Gym location verification is required for this branch. Enable location access or request admin approval.");
            }
            return "LOCATION_UNAVAILABLE_ADVISORY";
        }

        if (policy.getBranchLat() != null && policy.getBranchLng() != null) {
            double meters = haversineMeters(
                    policy.getBranchLat(), policy.getBranchLng(),
                    clientLat, clientLng);
            if (meters > policy.getMaxDistanceMeters()) {
                if (policy.getLocationPolicy() == CheckInPolicyProperties.LocationPolicyMode.STRICT && !ipMatch) {
                    throw new VerificationRuleException(
                            "You appear outside the gym check-in perimeter (" + Math.round(meters)
                                    + "m away). Connect to branch Wi-Fi or ask an administrator for assistance.");
                }
                return "OUT_OF_GEOFENCE_ADVISORY";
            }
            return "GEOFENCE_VERIFIED";
        }

        return ipMatch ? "BRANCH_NETWORK_VERIFIED" : "LOCATION_PROVIDED";
    }

    private boolean checkNetworkMatch(String clientIp) {
        if (clientIp == null || clientIp.isBlank() || policy.getAllowedIpPrefixes() == null || policy.getAllowedIpPrefixes().isBlank()) {
            return false;
        }
        return Arrays.stream(policy.getAllowedIpPrefixes().split(","))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .anyMatch(clientIp::startsWith);
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }
}
