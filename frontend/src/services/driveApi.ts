import { api } from './api';
import { ApiResponse } from '../types/api';
import { DriveUploadResponse, FileUploadAuditRecord, DriveHistoryQueryParams } from '../types/drive';

export const driveApi = {
  uploadToDrive: async (
    file: File,
    systemSource: string,
    folderId?: string,
    onProgress?: (percent: number) => void
  ): Promise<DriveUploadResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('systemSource', systemSource);
    if (folderId && folderId.trim()) {
      formData.append('folderId', folderId.trim());
    }

    const response = await api.post<ApiResponse<DriveUploadResponse>>('/drive/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total) {
          const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          onProgress?.(percent);
        }
      },
    });

    return response.data.data!;
  },

  getDriveUploadHistory: async (params?: DriveHistoryQueryParams): Promise<FileUploadAuditRecord[]> => {
    const response = await api.get<ApiResponse<FileUploadAuditRecord[]>>('/drive/history', {
      params,
    });
    return response.data.data || [];
  },

  checkDriveHealth: async (): Promise<boolean> => {
    try {
      const response = await api.get<ApiResponse<{ status: string }>>('/drive/health');
      return response.data.data?.status === 'UP';
    } catch {
      return false;
    }
  },
};
