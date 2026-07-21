package com.digitaladdictionmonitor.backend.repository;

import com.digitaladdictionmonitor.backend.entity.UsageLogEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UsageLogRepositoryTest {

    @Autowired
    private UsageLogRepository repository;

    @Test
    void findByUserIdOrderByTimestampAscReturnsOnlyThatUsersRowsInOrder() {
        Instant t0 = Instant.parse("2026-07-01T10:00:00Z");
        repository.saveAll(List.of(
                new UsageLogEntity("u0001", t0.plusSeconds(120), "B", "social", 60, 0, false),
                new UsageLogEntity("u0001", t0, "A", "productivity", 900, 0, false),
                new UsageLogEntity("u0002", t0.plusSeconds(60), "C", "entertainment", 300, 0, false)
        ));

        List<UsageLogEntity> result = repository.findByUserIdOrderByTimestampAsc("u0001");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAppName()).isEqualTo("A");
        assertThat(result.get(1).getAppName()).isEqualTo("B");
        assertThat(result).allMatch(e -> e.getUserId().equals("u0001"));
    }

    @Test
    void existsByUserIdReflectsPersistedRows() {
        repository.save(new UsageLogEntity(
                "u0001", Instant.parse("2026-07-01T10:00:00Z"), "A", "social", 60, 0, false));

        assertThat(repository.existsByUserId("u0001")).isTrue();
        assertThat(repository.existsByUserId("does-not-exist")).isFalse();
    }
}
