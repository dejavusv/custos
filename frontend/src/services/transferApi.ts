import { api } from './api';
import { ApiResponse } from '../types/api';
import {
  StoragePrecheckRequest,
  StoragePrecheckResult,
  SplitFileRequest,
  TransferManifest,
  MergeFileRequest,
  TransferRequest,
  TransferResult,
} from '../types/transfer';

export const transferApi = {
  precheckStorage: async (
    data: StoragePrecheckRequest
  ): Promise<StoragePrecheckResult> => {
    const response = await api.post<ApiResponse<StoragePrecheckResult>>(
      '/transfer/precheck',
      data
    );
    return response.data.data!;
  },

  splitFile: async (data: SplitFileRequest): Promise<TransferManifest> => {
    const response = await api.post<ApiResponse<TransferManifest>>(
      '/transfer/split',
      data
    );
    return response.data.data!;
  },

  mergeChunks: async (
    data: MergeFileRequest
  ): Promise<{ mergedFilePath: string; mergedSizeBytes: number; checksumSha256: string }> => {
    const response = await api.post<ApiResponse<{ mergedFilePath: string; mergedSizeBytes: number; checksumSha256: string }>>(
      '/transfer/merge',
      data
    );
    return response.data.data!;
  },

  uploadFile: async (data: TransferRequest): Promise<TransferResult> => {
    const response = await api.post<ApiResponse<TransferResult>>(
      '/transfer/upload',
      data
    );
    return response.data.data!;
  },
};
