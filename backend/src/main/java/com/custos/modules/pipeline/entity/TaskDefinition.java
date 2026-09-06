package com.custos.modules.pipeline.entity;

import com.custos.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "task_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskDefinition extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 50)
    private TaskType taskType;

    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String configJson = "{}";
}
