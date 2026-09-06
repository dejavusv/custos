package com.custos.modules.scheduling.job;

import com.custos.modules.pipeline.engine.DagPipelineOrchestrator;
import com.custos.modules.pipeline.entity.TriggerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class PipelineJob implements Job {

    private final DagPipelineOrchestrator dagPipelineOrchestrator;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String pipelineIdStr = context.getMergedJobDataMap().getString("pipelineId");
        if (pipelineIdStr == null) {
            log.error("PipelineJob triggered without pipelineId in JobDataMap");
            return;
        }

        UUID pipelineId = UUID.fromString(pipelineIdStr);
        log.info("Quartz Scheduler triggered PipelineJob for pipeline: {}", pipelineId);

        try {
            dagPipelineOrchestrator.executePipeline(pipelineId, "QUARTZ_SCHEDULER", TriggerType.SCHEDULED);
        } catch (Exception e) {
            log.error("Error executing scheduled pipeline {}: {}", pipelineId, e.getMessage(), e);
            throw new JobExecutionException(e, false);
        }
    }
}
