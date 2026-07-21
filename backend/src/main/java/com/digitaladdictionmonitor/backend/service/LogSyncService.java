package com.digitaladdictionmonitor.backend.service;

import com.digitaladdictionmonitor.backend.dto.LogSyncRequest;
import com.digitaladdictionmonitor.backend.dto.LogSyncResponse;
import com.digitaladdictionmonitor.backend.dto.UsageLogDto;
import com.digitaladdictionmonitor.backend.entity.UsageLogEntity;
import com.digitaladdictionmonitor.backend.repository.UsageLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LogSyncService {

    private final UsageLogRepository usageLogRepository;

    public LogSyncService(UsageLogRepository usageLogRepository) {
        this.usageLogRepository = usageLogRepository;
    }

    @Transactional
    public LogSyncResponse sync(LogSyncRequest request) {
        List<UsageLogEntity> entities = request.getLogs().stream()
                .map(dto -> toEntity(request.getUserId(), dto))
                .toList();

        usageLogRepository.saveAll(entities);

        return new LogSyncResponse(
                request.getUserId(),
                entities.size(),
                "Synced " + entities.size() + " usage log row(s) for userId=" + request.getUserId()
        );
    }

    private UsageLogEntity toEntity(String userId, UsageLogDto dto) {
        return new UsageLogEntity(
                userId,
                dto.getTimestamp(),
                dto.getAppName(),
                dto.getAppCategory(),
                dto.getSessionDurationSec(),
                dto.getUnlockCount(),
                dto.isNightUsage()
        );
    }
}
