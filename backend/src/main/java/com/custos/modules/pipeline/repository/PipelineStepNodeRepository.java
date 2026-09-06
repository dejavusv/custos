package com.custos.modules.pipeline.repository;

import com.custos.modules.pipeline.entity.PipelineStepNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PipelineStepNodeRepository extends JpaRepository<PipelineStepNode, UUID> {
    List<PipelineStepNode> findByPipelineIdOrderByStepOrderAsc(UUID pipelineId);
    void deleteByPipelineId(UUID pipelineId);
}
