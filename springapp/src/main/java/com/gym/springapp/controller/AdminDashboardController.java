package com.gym.springapp.controller;

import com.gym.springapp.dto.DashboardAnalyticsDTO;
import com.gym.springapp.service.DashboardAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final DashboardAnalyticsService dashboardAnalyticsService;

    public AdminDashboardController(DashboardAnalyticsService dashboardAnalyticsService) {
        this.dashboardAnalyticsService = dashboardAnalyticsService;
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DashboardAnalyticsDTO> getAnalytics() {
        return ResponseEntity.ok(dashboardAnalyticsService.getAnalytics());
    }
}
