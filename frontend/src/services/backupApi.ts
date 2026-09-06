import { api } from './api';
import { ApiResponse } from '../types/api';
import {
  BackupResult,
  DatabaseBackupRequest,
  FileBackupRequest,
  RetentionCleanupRequest,
  RetentionCleanupResult,
} from '../types/backup';

export const backupApi = {
  triggerDatabaseBackup: async (
    data: DatabaseBackupRequest
  ): Promise<BackupResult> => {
    const response = await api.post<ApiResponse<BackupResult>>(
      '/backup/database',
      data
    );
    return response.data.data!;
  },

  triggerFileSystemBackup: async (
    data: FileBackupRequest
  ): Promise<BackupResult> => {
    const response = await api.post<ApiResponse<BackupResult>>(
      '/backup/filesystem',
      data
    );
    return response.data.data!;
  },

  triggerRetentionCleanup: async (
    data: RetentionCleanupRequest
  ): Promise<RetentionCleanupResult> => {
    const response = await api.post<ApiResponse<RetentionCleanupResult>>(
      '/backup/retention-cleanup',
      data
    );
    return response.data.data!;
  },
};
