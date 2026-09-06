export type CompressionFormat = 'GZIP' | 'TAR_GZ' | 'ZIP' | 'NONE';

export interface BackupResult {
  success: boolean;
  destinationPath: string;
  fileName: string;
  fileSizeBytes: number;
  uncompressedSizeBytes: number;
  checksumSha256: string;
  durationMs: number;
  itemCount: number;
  compressionRatio?: number;
  createdAt: string;
  errorMessage?: string;
}

export interface DatabaseBackupRequest {
  credentialId?: string;
  host?: string;
  port?: number;
  username?: string;
  password?: string;
  databaseName: string;
  tables?: string[];
  destinationDir?: string;
  customFileName?: string;
  compressionFormat?: CompressionFormat;
}

export interface FileBackupRequest {
  sourcePath: string;
  destinationDir?: string;
  customFileName?: string;
  compressionFormat?: CompressionFormat;
  exclusionPatterns?: string[];
}

export interface RetentionCleanupRequest {
  backupDirectory: string;
  retentionDays: number;
  filePattern?: string;
}

export interface RetentionCleanupResult {
  success: boolean;
  directory: string;
  retentionDays: number;
  scannedFilesCount: number;
  deletedFilesCount: number;
  freedSpaceBytes: number;
  deletedFileNames: string[];
  executedAt: string;
  message: string;
}
