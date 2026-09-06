import { create } from 'zustand';
import { UserSession, LoginResponseData } from '../types/auth';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: UserSession | null;
  isAuthenticated: boolean;
  login: (data: LoginResponseData) => void;
  logout: () => void;
  updateTokens: (accessToken: string, refreshToken: string) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: localStorage.getItem('custos_access_token'),
  refreshToken: localStorage.getItem('custos_refresh_token'),
  user: localStorage.getItem('custos_user')
    ? JSON.parse(localStorage.getItem('custos_user')!)
    : null,
  isAuthenticated: !!localStorage.getItem('custos_access_token'),

  login: (data: LoginResponseData) => {
    const userSession: UserSession = {
      userId: data.userId,
      username: data.username,
      email: data.email,
      roles: data.roles,
    };
    localStorage.setItem('custos_access_token', data.accessToken);
    localStorage.setItem('custos_refresh_token', data.refreshToken);
    localStorage.setItem('custos_user', JSON.stringify(userSession));

    set({
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
      user: userSession,
      isAuthenticated: true,
    });
  },

  logout: () => {
    localStorage.removeItem('custos_access_token');
    localStorage.removeItem('custos_refresh_token');
    localStorage.removeItem('custos_user');

    set({
      accessToken: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,
    });
  },

  updateTokens: (accessToken: string, refreshToken: string) => {
    localStorage.setItem('custos_access_token', accessToken);
    localStorage.setItem('custos_refresh_token', refreshToken);

    set({
      accessToken,
      refreshToken,
    });
  },
}));
