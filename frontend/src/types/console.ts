export type LogLevel = 'INFO' | 'WARN' | 'ERROR' | 'DEBUG';

export interface LogMessageDto {
  executionId: string;
  stepName: string;
  level: LogLevel;
  message: string;
  timestamp: string;
}

export interface ProgressUpdateDto {
  executionId: string;
  stepNodeId?: string;
  stepName: string;
  percentage: number;
  currentChunk: number;
  totalChunks: number;
  bytesTransferred: number;
  totalBytes: number;
  status: string;
  timestamp: string;
}
