package com.custos.modules.pipeline.repository;

import com.custos.modules.pipeline.entity.TaskDefinition;
import com.custos.modules.pipeline.entity.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskDefinitionRepository extends JpaRepository<TaskDefinition, UUID> {
    Optional<TaskDefinition> findByName(String name);
    List<TaskDefinition> findByTaskType(TaskType taskType);
    boolean existsByName(String name);
}
