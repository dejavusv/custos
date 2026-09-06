export type TaskType = 'DATABASE_BACKUP' | 'FILE_BACKUP' | 'SPLIT_TRANSFER' | 'EMAIL_ALERT';

export type ExecutionStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'ABORTED' | 'SKIPPED';

export type MisfirePolicy = 'SMART_POLICY' | 'FIRE_NOW' | 'IGNORE' | 'DO_NOTHING';

export type TriggerType = 'MANUAL' | 'SCHEDULED' | 'RETRY';

export interface StepNodeDto {
  id?: string;
  taskId?: string;
  nodeKey: string;
  nodeLabel: string;
  nodeType: TaskType;
  stepOrder: number;
  positionX: number;
  positionY: number;
  configOverrideJson?: string;
  onSuccessNodeId?: string | null;
  onFailureNodeId?: string | null;
}

export interface SavePipelineRequest {
  id?: string;
  name: string;
  description?: string;
  cronExpression?: string;
  timezone?: string;
  misfirePolicy?: MisfirePolicy;
  isActive?: boolean;
  nodes: StepNodeDto[];
}

export interface PipelineDetailResponse {
  id: string;
  name: string;
  description?: string;
  cronExpression?: string;
  timezone: string;
  misfirePolicy: MisfirePolicy;
  isActive: boolean;
  nextFireTime?: string | null;
  createdAt: string;
  updatedAt: string;
  nodes: StepNodeDto[];
}

export interface StepLogDto {
  id: string;
  stepNodeId?: string;
  stepName: string;
  status: ExecutionStatus;
  startTime: string;
  endTime?: string;
  durationMs?: number;
  logsText?: string;
  fileSizeBytes?: number;
  outputPath?: string;
  checksumSha256?: string;
  awsSesMessageId?: string;
  errorMessage?: string;
}

export interface PipelineExecutionResponse {
  id: string;
  pipelineId: string;
  pipelineName: string;
  status: ExecutionStatus;
  startTime: string;
  endTime?: string;
  durationMs?: number;
  errorMessage?: string;
  contextDataJson?: string;
  triggeredBy: string;
  triggerType: TriggerType;
  stepLogs: StepLogDto[];
}
