export interface DockerContainer {
  name: string;
  image: string;
  status: string;
  networks: string[];
}

export interface DockerContainersResponse {
  // false when Docker integration is disabled or the Docker Engine cannot be reached
  available: boolean;
  message?: string | null;
  containers: DockerContainer[];
}
