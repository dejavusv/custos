export interface LoginResponseData {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  userId: string;
  username: string;
  email: string;
  roles: string[];
}

export interface UserSession {
  userId: string;
  username: string;
  email: string;
  roles: string[];
}
