import { ExecutionStatus, TriggerType } from './pipeline';

export interface PipelineSummary {
  id: string;
  name: string;
  active: boolean;
  cronExpression?: string | null;
  nextFireTime?: string | null;
  lastRunAt?: string | null;
  lastStatus?: ExecutionStatus | null;
  lastDurationMs?: number | null;
}

export interface RecentExecution {
  id: string;
  pipelineId: string;
  pipelineName: string;
  status: ExecutionStatus;
  startTime?: string | null;
  endTime?: string | null;
  durationMs?: number | null;
  triggeredBy: string;
  triggerType: TriggerType;
  errorMessage?: string | null;
}

export interface DashboardSummary {
  totalPipelines: number;
  activePipelines: number;
  pipelines: PipelineSummary[];
  recentExecutions: RecentExecution[];
}
