package com.custos.modules.pipeline.repository;

import com.custos.modules.pipeline.entity.ExecutionStatus;
import com.custos.modules.pipeline.entity.PipelineExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PipelineExecutionRepository extends JpaRepository<PipelineExecution, UUID> {
    List<PipelineExecution> findByPipelineIdOrderByCreatedAtDesc(UUID pipelineId);
    Page<PipelineExecution> findByPipelineIdOrderByCreatedAtDesc(UUID pipelineId, Pageable pageable);
    List<PipelineExecution> findByStatus(ExecutionStatus status);
}
