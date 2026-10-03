import { api } from './api';
import { ApiResponse } from '../types/api';
import { StorageBrowseResponse } from '../types/backup';
import { DockerContainersResponse } from '../types/docker';

export const dockerApi = {
  listContainers: async (): Promise<DockerContainersResponse> => {
    const response = await api.get<ApiResponse<DockerContainersResponse>>('/docker/containers');
    return response.data.data!;
  },

  browseContainer: async (
    container: string,
    path?: string
  ): Promise<StorageBrowseResponse> => {
    const response = await api.get<ApiResponse<StorageBrowseResponse>>(
      `/docker/containers/${encodeURIComponent(container)}/browse`,
      { params: path ? { path } : {} }
    );
    return response.data.data!;
  },
};
