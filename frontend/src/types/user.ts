export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'LOCKED';

export interface User {
  id: string;
  username: string;
  email: string;
  status: UserStatus;
  failedLoginAttempts: number;
  lockoutUntil: string | null;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  roles: string[];
}

export interface CreateUserInput {
  username: string;
  email: string;
  password: string;
  roles: string[];
}

export interface UpdateUserInput {
  email: string;
  roles: string[];
}

export interface AuditLog {
  id: string;
  userId?: string;
  username: string;
  action: string;
  targetResource?: string;
  ipAddress?: string;
  details?: string;
  createdAt: string;
}
