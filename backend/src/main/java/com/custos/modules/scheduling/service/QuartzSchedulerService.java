package com.custos.modules.scheduling.service;

import com.custos.modules.pipeline.entity.MisfirePolicy;
import com.custos.modules.pipeline.entity.PipelineDefinition;
import com.custos.modules.pipeline.repository.PipelineDefinitionRepository;
import com.custos.modules.scheduling.job.PipelineJob;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuartzSchedulerService {

    private final Scheduler scheduler;
    private final PipelineDefinitionRepository pipelineDefinitionRepository;

    private static final String JOB_GROUP = "PIPELINES";
    private static final String TRIGGER_GROUP = "PIPELINE_TRIGGERS";

    @EventListener(ApplicationReadyEvent.class)
    public void syncAllPipelinesOnStartup() {
        log.info("Synchronizing active pipelines with Quartz Scheduler...");
        List<PipelineDefinition> pipelines = pipelineDefinitionRepository.findAll();
        for (PipelineDefinition pipeline : pipelines) {
            try {
                if (pipeline.isActive() && pipeline.getCronExpression() != null && !pipeline.getCronExpression().trim().isEmpty()) {
                    schedulePipeline(pipeline);
                }
            } catch (Exception e) {
                log.error("Failed to schedule pipeline {} on startup: {}", pipeline.getName(), e.getMessage());
            }
        }
    }

    public void schedulePipeline(PipelineDefinition pipeline) throws SchedulerException {
        UUID id = pipeline.getId();
        JobKey jobKey = new JobKey("job_" + id, JOB_GROUP);
        TriggerKey triggerKey = new TriggerKey("trigger_" + id, TRIGGER_GROUP);

        if (pipeline.getCronExpression() == null || pipeline.getCronExpression().trim().isEmpty()) {
            log.info("Pipeline {} has no cron expression, removing any existing schedule.", id);
            unschedulePipeline(id);
            return;
        }

        // 1. Build JobDetail
        JobDetail jobDetail = JobBuilder.newJob(PipelineJob.class)
                .withIdentity(jobKey)
                .withDescription(pipeline.getName())
                .usingJobData("pipelineId", id.toString())
                .storeDurably()
                .build();

        // 2. Build CronScheduleBuilder with timezone & misfire policy
        TimeZone tz = TimeZone.getTimeZone(pipeline.getTimezone() != null ? pipeline.getTimezone() : "UTC");
        CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(pipeline.getCronExpression())
                .inTimeZone(tz);

        MisfirePolicy misfire = pipeline.getMisfirePolicy() != null ? pipeline.getMisfirePolicy() : MisfirePolicy.SMART_POLICY;
        switch (misfire) {
            case FIRE_NOW -> scheduleBuilder.withMisfireHandlingInstructionFireAndProceed();
            case IGNORE -> scheduleBuilder.withMisfireHandlingInstructionIgnoreMisfires();
            case DO_NOTHING -> scheduleBuilder.withMisfireHandlingInstructionDoNothing();
            case SMART_POLICY -> {
                // Default Quartz smart policy
            }
        }

        // 3. Build CronTrigger
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey)
                .withSchedule(scheduleBuilder)
                .build();

        // 4. Register or reschedule
        if (scheduler.checkExists(jobKey)) {
            scheduler.addJob(jobDetail, true);
            if (scheduler.checkExists(triggerKey)) {
                scheduler.rescheduleJob(triggerKey, trigger);
                log.info("Rescheduled pipeline {} with cron: {}", id, pipeline.getCronExpression());
            } else {
                scheduler.scheduleJob(trigger);
                log.info("Attached new trigger for pipeline {} with cron: {}", id, pipeline.getCronExpression());
            }
        } else {
            scheduler.scheduleJob(jobDetail, trigger);
            log.info("Scheduled new job and trigger for pipeline {} with cron: {}", id, pipeline.getCronExpression());
        }

        if (!pipeline.isActive()) {
            scheduler.pauseJob(jobKey);
            log.info("Paused scheduled pipeline {} because isActive = false", id);
        }
    }

    public void unschedulePipeline(UUID pipelineId) throws SchedulerException {
        JobKey jobKey = new JobKey("job_" + pipelineId, JOB_GROUP);
        TriggerKey triggerKey = new TriggerKey("trigger_" + pipelineId, TRIGGER_GROUP);

        if (scheduler.checkExists(triggerKey)) {
            scheduler.unscheduleJob(triggerKey);
        }
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
        }
        log.info("Unscheduled pipeline: {}", pipelineId);
    }

    public void pausePipeline(UUID pipelineId) throws SchedulerException {
        JobKey jobKey = new JobKey("job_" + pipelineId, JOB_GROUP);
        if (scheduler.checkExists(jobKey)) {
            scheduler.pauseJob(jobKey);
            log.info("Paused pipeline in Quartz: {}", pipelineId);
        }
    }

    public void resumePipeline(UUID pipelineId) throws SchedulerException {
        JobKey jobKey = new JobKey("job_" + pipelineId, JOB_GROUP);
        if (scheduler.checkExists(jobKey)) {
            scheduler.resumeJob(jobKey);
            log.info("Resumed pipeline in Quartz: {}", pipelineId);
        }
    }

    public void triggerNow(UUID pipelineId) throws SchedulerException {
        JobKey jobKey = new JobKey("job_" + pipelineId, JOB_GROUP);
        if (!scheduler.checkExists(jobKey)) {
            PipelineDefinition pipeline = pipelineDefinitionRepository.findById(pipelineId)
                    .orElseThrow(() -> new IllegalArgumentException("Pipeline not found: " + pipelineId));
            schedulePipeline(pipeline);
        }
        scheduler.triggerJob(jobKey);
        log.info("Triggered immediate execution for pipeline: {}", pipelineId);
    }

    public Instant getNextFireTime(UUID pipelineId) {
        TriggerKey triggerKey = new TriggerKey("trigger_" + pipelineId, TRIGGER_GROUP);
        try {
            Trigger trigger = scheduler.getTrigger(triggerKey);
            if (trigger != null) {
                Date nextFire = trigger.getNextFireTime();
                return nextFire != null ? nextFire.toInstant() : null;
            }
        } catch (SchedulerException e) {
            log.warn("Could not get next fire time for pipeline {}: {}", pipelineId, e.getMessage());
        }
        return null;
    }
}
