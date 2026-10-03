import { api } from './api';
import { ApiResponse } from '../types/api';
import {
  ExternalNotifyTask,
  ExternalNotifySendRequest,
  ExternalNotifySendResult,
} from '../types/externalNotify';

export const externalNotifyApi = {
  listTasks: async (credentialId: string): Promise<ExternalNotifyTask[]> => {
    const response = await api.get<ApiResponse<ExternalNotifyTask[]>>('/external-notify/tasks', {
      params: { credentialId },
    });
    return response.data.data ?? [];
  },

  send: async (request: ExternalNotifySendRequest): Promise<ExternalNotifySendResult> => {
    const response = await api.post<ApiResponse<ExternalNotifySendResult>>('/external-notify/send', request);
    return response.data.data!;
  },
};
