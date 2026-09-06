package com.custos.modules.pipeline.repository;

import com.custos.modules.pipeline.entity.StepExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StepExecutionLogRepository extends JpaRepository<StepExecutionLog, UUID> {
    List<StepExecutionLog> findByExecutionIdOrderByStartTimeAsc(UUID executionId);
}
