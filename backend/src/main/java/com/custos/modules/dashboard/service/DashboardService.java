package com.custos.modules.dashboard.service;

import com.custos.modules.dashboard.dto.DashboardSummaryResponse;
import com.custos.modules.dashboard.dto.DashboardSummaryResponse.PipelineSummaryDto;
import com.custos.modules.dashboard.dto.DashboardSummaryResponse.RecentExecutionDto;
import com.custos.modules.pipeline.entity.PipelineDefinition;
import com.custos.modules.pipeline.entity.PipelineExecution;
import com.custos.modules.pipeline.repository.PipelineDefinitionRepository;
import com.custos.modules.pipeline.repository.PipelineExecutionRepository;
import com.custos.modules.scheduling.service.QuartzSchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    static final int RECENT_EXECUTION_LIMIT = 10;

    private final PipelineDefinitionRepository pipelineDefinitionRepository;
    private final PipelineExecutionRepository pipelineExecutionRepository;
    private final QuartzSchedulerService quartzSchedulerService;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        Map<UUID, PipelineExecution> latestByPipeline = new HashMap<>();
        for (PipelineExecution exec : pipelineExecutionRepository.findLatestPerPipeline()) {
            // ถ้ามี execution ที่ createdAt เท่ากันหลายรายการ ให้ใช้รายการที่ startTime ใหม่กว่า
            latestByPipeline.merge(exec.getPipeline().getId(), exec, DashboardService::newer);
        }

        List<PipelineSummaryDto> pipelines = pipelineDefinitionRepository.findAll(Sort.by("name")).stream()
                .map(p -> toPipelineSummary(p, latestByPipeline.get(p.getId())))
                .toList();

        List<RecentExecutionDto> recent = pipelineExecutionRepository
                .findRecentWithPipeline(PageRequest.of(0, RECENT_EXECUTION_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::toRecentExecution)
                .getContent();

        return DashboardSummaryResponse.builder()
                .totalPipelines(pipelineDefinitionRepository.count())
                .activePipelines(pipelineDefinitionRepository.countByIsActiveTrue())
                .pipelines(pipelines)
                .recentExecutions(recent)
                .build();
    }

    private static PipelineExecution newer(PipelineExecution a, PipelineExecution b) {
        return Comparator.comparing(PipelineExecution::getStartTime,
                Comparator.nullsFirst(Comparator.naturalOrder())).compare(a, b) >= 0 ? a : b;
    }

    private PipelineSummaryDto toPipelineSummary(PipelineDefinition pipeline, PipelineExecution last) {
        return PipelineSummaryDto.builder()
                .id(pipeline.getId())
                .name(pipeline.getName())
                .active(pipeline.isActive())
                .cronExpression(pipeline.getCronExpression())
                .nextFireTime(quartzSchedulerService.getNextFireTime(pipeline.getId()))
                .lastRunAt(last != null ? last.getStartTime() : null)
                .lastStatus(last != null ? last.getStatus() : null)
                .lastDurationMs(last != null ? last.getDurationMs() : null)
                .build();
    }

    private RecentExecutionDto toRecentExecution(PipelineExecution exec) {
        return RecentExecutionDto.builder()
                .id(exec.getId())
                .pipelineId(exec.getPipeline().getId())
                .pipelineName(exec.getPipeline().getName())
                .status(exec.getStatus())
                .startTime(exec.getStartTime())
                .endTime(exec.getEndTime())
                .durationMs(exec.getDurationMs())
                .triggeredBy(exec.getTriggeredBy() != null ? exec.getTriggeredBy() : "SYSTEM")
                .triggerType(exec.getTriggerType())
                .errorMessage(exec.getErrorMessage())
                .build();
    }
}
