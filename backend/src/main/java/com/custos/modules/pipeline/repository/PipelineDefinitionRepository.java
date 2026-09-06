package com.custos.modules.pipeline.repository;

import com.custos.modules.pipeline.entity.PipelineDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PipelineDefinitionRepository extends JpaRepository<PipelineDefinition, UUID> {
    Optional<PipelineDefinition> findByName(String name);
    List<PipelineDefinition> findByIsActiveTrue();
    boolean existsByName(String name);
}
