package com.digitaladdictionmonitor.backend.controller;

import com.digitaladdictionmonitor.backend.dto.DashboardResponse;
import com.digitaladdictionmonitor.backend.dto.FocusMetricsDto;
import com.digitaladdictionmonitor.backend.exception.MlServiceUnavailableException;
import com.digitaladdictionmonitor.backend.exception.UserNotFoundException;
import com.digitaladdictionmonitor.backend.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    void validUserReturns200WithDashboard() throws Exception {
        DashboardResponse response = new DashboardResponse(
                "u0001", 42.5, "increasing", Map.of("social", 30.0),
                new FocusMetricsDto(5.0, 80.0), List.of(), "borderline", List.of()
        );
        when(dashboardService.getDashboard("u0001")).thenReturn(response);

        mockMvc.perform(get("/api/dashboard/u0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u0001"))
                .andExpect(jsonPath("$.riskScore").value(42.5))
                .andExpect(jsonPath("$.trendDirection").value("increasing"))
                .andExpect(jsonPath("$.clusterPersona").value("borderline"));
    }

    @Test
    void unknownUserReturns404() throws Exception {
        when(dashboardService.getDashboard("u9999")).thenThrow(new UserNotFoundException("u9999"));

        mockMvc.perform(get("/api/dashboard/u9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No usage logs found for userId=u9999"));
    }

    @Test
    void mlServiceDownReturns502() throws Exception {
        when(dashboardService.getDashboard("u0001"))
                .thenThrow(new MlServiceUnavailableException("ml-service /analyze/weekly call failed for userId=u0001",
                        new RuntimeException("connection refused")));

        mockMvc.perform(get("/api/dashboard/u0001"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("ml-service /analyze/weekly call failed for userId=u0001"));
    }
}
