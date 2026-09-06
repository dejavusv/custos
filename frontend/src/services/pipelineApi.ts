import { api } from './api';
import {
  PipelineDetailResponse,
  PipelineExecutionResponse,
  SavePipelineRequest,
} from '../types/pipeline';

export const pipelineApi = {
  listPipelines: async (): Promise<PipelineDetailResponse[]> => {
    const response = await api.get<PipelineDetailResponse[]>('/pipelines');
    return response.data;
  },

  getPipeline: async (id: string): Promise<PipelineDetailResponse> => {
    const response = await api.get<PipelineDetailResponse>(`/pipelines/${id}`);
    return response.data;
  },

  createPipeline: async (data: SavePipelineRequest): Promise<PipelineDetailResponse> => {
    const response = await api.post<PipelineDetailResponse>('/pipelines', data);
    return response.data;
  },

  updatePipeline: async (id: string, data: SavePipelineRequest): Promise<PipelineDetailResponse> => {
    const response = await api.put<PipelineDetailResponse>(`/pipelines/${id}`, data);
    return response.data;
  },

  deletePipeline: async (id: string): Promise<void> => {
    await api.delete(`/pipelines/${id}`);
  },

  triggerPipeline: async (id: string): Promise<PipelineExecutionResponse> => {
    const response = await api.post<PipelineExecutionResponse>(`/pipelines/${id}/trigger`);
    return response.data;
  },

  pausePipeline: async (id: string): Promise<void> => {
    await api.post(`/pipelines/${id}/pause`);
  },

  resumePipeline: async (id: string): Promise<void> => {
    await api.post(`/pipelines/${id}/resume`);
  },

  listExecutions: async (pipelineId: string): Promise<PipelineExecutionResponse[]> => {
    const response = await api.get<PipelineExecutionResponse[]>(`/pipelines/${pipelineId}/executions`);
    return response.data;
  },

  listAllExecutions: async (status?: string): Promise<PipelineExecutionResponse[]> => {
    const params = status ? { status } : {};
    const response = await api.get<PipelineExecutionResponse[]>('/pipelines/executions/all', { params });
    return response.data;
  },

  getExecution: async (executionId: string): Promise<PipelineExecutionResponse> => {
    const response = await api.get<PipelineExecutionResponse>(`/pipelines/executions/${executionId}`);
    return response.data;
  },

  abortExecution: async (executionId: string): Promise<void> => {
    await api.post(`/pipelines/executions/${executionId}/abort`);
  },
};
