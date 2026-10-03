import { api } from './api';
import { ApiResponse } from '../types/api';
import { DashboardSummary } from '../types/dashboard';

export const dashboardApi = {
  async getSummary(): Promise<DashboardSummary> {
    const res = await api.get<ApiResponse<DashboardSummary>>('/dashboard/summary');
    return res.data.data!;
  },
};
