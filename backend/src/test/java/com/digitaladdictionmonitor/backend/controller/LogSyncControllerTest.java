package com.digitaladdictionmonitor.backend.controller;

import com.digitaladdictionmonitor.backend.dto.LogSyncResponse;
import com.digitaladdictionmonitor.backend.service.LogSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LogSyncController.class)
class LogSyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LogSyncService logSyncService;

    @Test
    void validRequestReturns201WithSyncResult() throws Exception {
        when(logSyncService.sync(any())).thenReturn(
                new LogSyncResponse("u0001", 1, "Synced 1 usage log row(s) for userId=u0001"));

        String body = """
                {
                  "userId": "u0001",
                  "logs": [
                    {
                      "timestamp": "2026-07-20T10:15:00Z",
                      "appName": "Instagram",
                      "appCategory": "social",
                      "sessionDurationSec": 120.0,
                      "unlockCount": 2,
                      "isNightUsage": false
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/logs/sync").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("u0001"))
                .andExpect(jsonPath("$.acceptedCount").value(1))
                .andExpect(jsonPath("$.message").value("Synced 1 usage log row(s) for userId=u0001"));
    }

    @Test
    void missingUserIdReturns400WithValidationDetails() throws Exception {
        String body = """
                {
                  "logs": [
                    {
                      "timestamp": "2026-07-20T10:15:00Z",
                      "appName": "Instagram",
                      "appCategory": "social",
                      "sessionDurationSec": 120.0,
                      "unlockCount": 2,
                      "isNightUsage": false
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/logs/sync").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("userId: must not be blank"));
    }

    @Test
    void emptyLogsListReturns400() throws Exception {
        String body = """
                { "userId": "u0001", "logs": [] }
                """;

        mockMvc.perform(post("/api/logs/sync").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
