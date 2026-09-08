import { api } from './api';
import { ApiResponse } from '../types/api';
import {
  BackupResult,
  DatabaseBackupRequest,
  FileBackupRequest,
  RetentionCleanupRequest,
  RetentionCleanupResult,
  StorageBrowseResponse,
  StorageItem,
  CreateDirectoryRequest,
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

  browseStorage: async (path?: string): Promise<StorageBrowseResponse> => {
    const response = await api.get<ApiResponse<StorageBrowseResponse>>(
      '/backup/storage/browse',
      { params: path ? { path } : {} }
    );
    return response.data.data!;
  },

  createDirectory: async (data: CreateDirectoryRequest): Promise<StorageItem> => {
    const response = await api.post<ApiResponse<StorageItem>>(
      '/backup/storage/mkdir',
      data
    );
    return response.data.data!;
  },
};
