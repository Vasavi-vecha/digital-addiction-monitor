package com.digitaladdictionmonitor.backend.service;

import com.digitaladdictionmonitor.backend.dto.LogSyncRequest;
import com.digitaladdictionmonitor.backend.dto.LogSyncResponse;
import com.digitaladdictionmonitor.backend.dto.UsageLogDto;
import com.digitaladdictionmonitor.backend.entity.UsageLogEntity;
import com.digitaladdictionmonitor.backend.repository.UsageLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogSyncServiceTest {

    @Mock
    private UsageLogRepository usageLogRepository;

    @Test
    void mapsDtosToEntitiesStampsUserIdAndSavesAll() {
        LogSyncService service = new LogSyncService(usageLogRepository);
        Instant now = Instant.parse("2026-07-20T10:15:00Z");
        UsageLogDto dto1 = new UsageLogDto(now, "Instagram", "social", 120.0, 3, false);
        UsageLogDto dto2 = new UsageLogDto(now.plusSeconds(60), "Notion", "productivity", 900.0, 0, false);
        LogSyncRequest request = new LogSyncRequest("u0001", List.of(dto1, dto2));

        when(usageLogRepository.saveAll(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LogSyncResponse response = service.sync(request);

        ArgumentCaptor<List<UsageLogEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(usageLogRepository).saveAll(captor.capture());
        List<UsageLogEntity> saved = captor.getValue();

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getUserId()).isEqualTo("u0001");
        assertThat(saved.get(0).getAppName()).isEqualTo("Instagram");
        assertThat(saved.get(0).getAppCategory()).isEqualTo("social");
        assertThat(saved.get(0).getSessionDurationSec()).isEqualTo(120.0);
        assertThat(saved.get(0).getUnlockCount()).isEqualTo(3);
        assertThat(saved.get(1).getUserId()).isEqualTo("u0001");
        assertThat(saved.get(1).getAppName()).isEqualTo("Notion");

        assertThat(response.getUserId()).isEqualTo("u0001");
        assertThat(response.getAcceptedCount()).isEqualTo(2);
        assertThat(response.getMessage()).isEqualTo("Synced 2 usage log row(s) for userId=u0001");
    }
}
