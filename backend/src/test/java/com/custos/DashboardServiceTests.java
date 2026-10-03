package com.custos;

import com.custos.modules.dashboard.dto.DashboardSummaryResponse;
import com.custos.modules.dashboard.dto.DashboardSummaryResponse.PipelineSummaryDto;
import com.custos.modules.dashboard.service.DashboardService;
import com.custos.modules.pipeline.entity.ExecutionStatus;
import com.custos.modules.pipeline.entity.PipelineDefinition;
import com.custos.modules.pipeline.entity.PipelineExecution;
import com.custos.modules.pipeline.repository.PipelineDefinitionRepository;
import com.custos.modules.pipeline.repository.PipelineExecutionRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
public class DashboardServiceTests {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private PipelineDefinitionRepository pipelineDefinitionRepository;

    @Autowired
    private PipelineExecutionRepository pipelineExecutionRepository;

    private PipelineDefinition savePipeline(String prefix, boolean active) {
        return pipelineDefinitionRepository.save(PipelineDefinition.builder()
                .name(prefix + "-" + UUID.randomUUID())
                .isActive(active)
                .build());
    }

    private PipelineExecution saveExecution(PipelineDefinition pipeline, ExecutionStatus status) throws InterruptedException {
        Thread.sleep(5); // ให้ createdAt ของแต่ละรายการต่างกันเพื่อทดสอบการเรียงลำดับ
        return pipelineExecutionRepository.save(PipelineExecution.builder()
                .pipeline(pipeline)
                .status(status)
                .startTime(Instant.now())
                .durationMs(120L)
                .build());
    }

    @Test
    @DisplayName("Dashboard summary - counts pipelines and reports last run per pipeline")
    public void testSummaryPipelinesAndLastRun() throws InterruptedException {
        DashboardSummaryResponse before = dashboardService.getSummary();

        PipelineDefinition active = savePipeline("dash-active", true);
        PipelineDefinition paused = savePipeline("dash-paused", false);
        PipelineDefinition never = savePipeline("dash-never", true);

        saveExecution(active, ExecutionStatus.FAILED);
        PipelineExecution lastActive = saveExecution(active, ExecutionStatus.SUCCESS);
        PipelineExecution lastPaused = saveExecution(paused, ExecutionStatus.FAILED);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        Assertions.assertEquals(before.getTotalPipelines() + 3, summary.getTotalPipelines());
        Assertions.assertEquals(before.getActivePipelines() + 2, summary.getActivePipelines());

        PipelineSummaryDto activeDto = find(summary, active.getId());
        Assertions.assertEquals(ExecutionStatus.SUCCESS, activeDto.getLastStatus());
        Assertions.assertEquals(lastActive.getStartTime().truncatedTo(ChronoUnit.MILLIS), activeDto.getLastRunAt().truncatedTo(ChronoUnit.MILLIS));
        Assertions.assertTrue(activeDto.isActive());

        PipelineSummaryDto pausedDto = find(summary, paused.getId());
        Assertions.assertEquals(ExecutionStatus.FAILED, pausedDto.getLastStatus());
        Assertions.assertEquals(lastPaused.getStartTime().truncatedTo(ChronoUnit.MILLIS), pausedDto.getLastRunAt().truncatedTo(ChronoUnit.MILLIS));
        Assertions.assertFalse(pausedDto.isActive());

        PipelineSummaryDto neverDto = find(summary, never.getId());
        Assertions.assertNull(neverDto.getLastStatus());
        Assertions.assertNull(neverDto.getLastRunAt());
    }

    @Test
    @DisplayName("Dashboard summary - recent executions are capped at 10 and newest first")
    public void testSummaryRecentExecutionsLimit() throws InterruptedException {
        PipelineDefinition pipeline = savePipeline("dash-recent", true);
        PipelineExecution newest = null;
        for (int i = 0; i < 12; i++) {
            newest = saveExecution(pipeline, ExecutionStatus.SUCCESS);
        }

        List<DashboardSummaryResponse.RecentExecutionDto> recent = dashboardService.getSummary().getRecentExecutions();

        Assertions.assertEquals(10, recent.size());
        Assertions.assertEquals(newest.getId(), recent.get(0).getId());
        Assertions.assertEquals(pipeline.getName(), recent.get(0).getPipelineName());
        Assertions.assertEquals("SYSTEM", recent.get(0).getTriggeredBy());
    }

    private PipelineSummaryDto find(DashboardSummaryResponse summary, UUID id) {
        return summary.getPipelines().stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Pipeline missing from summary: " + id));
    }
}
