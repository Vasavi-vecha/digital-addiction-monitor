package com.digitaladdictionmonitor.backend.repository;

import com.digitaladdictionmonitor.backend.entity.UsageLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsageLogRepository extends JpaRepository<UsageLogEntity, Long> {

    List<UsageLogEntity> findByUserIdOrderByTimestampAsc(String userId);

    boolean existsByUserId(String userId);
}
