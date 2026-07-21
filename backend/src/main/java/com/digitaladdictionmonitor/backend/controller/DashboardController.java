package com.digitaladdictionmonitor.backend.controller;

import com.digitaladdictionmonitor.backend.dto.DashboardResponse;
import com.digitaladdictionmonitor.backend.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard/{userId}")
    public DashboardResponse getDashboard(@PathVariable String userId) {
        return dashboardService.getDashboard(userId);
    }
}
