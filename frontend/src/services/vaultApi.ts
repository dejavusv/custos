import { api } from './api';
import { ApiResponse } from '../types/api';
import {
  CredentialResponse,
  CreateCredentialRequest,
  UpdateCredentialRequest,
  CredentialType,
} from '../types/vault';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export const vaultApi = {
  getCredentials: async (params?: {
    type?: CredentialType;
    search?: string;
    page?: number;
    size?: number;
  }): Promise<PageResponse<CredentialResponse>> => {
    const response = await api.get<ApiResponse<PageResponse<CredentialResponse>>>(
      '/vault/credentials',
      { params }
    );
    return response.data.data!;
  },

  getAllCredentials: async (): Promise<CredentialResponse[]> => {
    const response = await api.get<ApiResponse<CredentialResponse[]>>(
      '/vault/credentials/all'
    );
    return response.data.data!;
  },

  getCredentialById: async (id: string): Promise<CredentialResponse> => {
    const response = await api.get<ApiResponse<CredentialResponse>>(
      `/vault/credentials/${id}`
    );
    return response.data.data!;
  },

  createCredential: async (
    data: CreateCredentialRequest
  ): Promise<CredentialResponse> => {
    const response = await api.post<ApiResponse<CredentialResponse>>(
      '/vault/credentials',
      data
    );
    return response.data.data!;
  },

  updateCredential: async (
    id: string,
    data: UpdateCredentialRequest
  ): Promise<CredentialResponse> => {
    const response = await api.put<ApiResponse<CredentialResponse>>(
      `/vault/credentials/${id}`,
      data
    );
    return response.data.data!;
  },

  deleteCredential: async (id: string): Promise<void> => {
    await api.delete(`/vault/credentials/${id}`);
  },
};
