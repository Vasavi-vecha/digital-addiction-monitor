package com.digitaladdictionmonitor.backend.controller;

import com.digitaladdictionmonitor.backend.dto.LogSyncRequest;
import com.digitaladdictionmonitor.backend.dto.LogSyncResponse;
import com.digitaladdictionmonitor.backend.service.LogSyncService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogSyncController {

    private final LogSyncService logSyncService;

    public LogSyncController(LogSyncService logSyncService) {
        this.logSyncService = logSyncService;
    }

    @PostMapping("/api/logs/sync")
    @ResponseStatus(HttpStatus.CREATED)
    public LogSyncResponse sync(@Valid @RequestBody LogSyncRequest request) {
        return logSyncService.sync(request);
    }
}
