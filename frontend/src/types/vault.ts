export type CredentialType =
  | 'DATABASE_MYSQL'
  | 'DATABASE_POSTGRESQL'
  | 'FTP'
  | 'FTPS'
  | 'SFTP'
  | 'GENERIC_SECRET';

export interface CredentialResponse {
  id: string;
  name: string;
  description?: string;
  credentialType: CredentialType;
  host?: string;
  port?: number;
  username?: string;
  databaseName?: string;
  secretMasked?: string;
  hasSshKey: boolean;
  extraMetadata?: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
}

export interface CreateCredentialRequest {
  name: string;
  description?: string;
  credentialType: CredentialType;
  host?: string;
  port?: number;
  username?: string;
  databaseName?: string;
  secretPassword?: string;
  sshPrivateKey?: string;
  sshPassphrase?: string;
  extraMetadata?: string;
}

export interface UpdateCredentialRequest {
  name: string;
  description?: string;
  host?: string;
  port?: number;
  username?: string;
  databaseName?: string;
  secretPassword?: string;
  sshPrivateKey?: string;
  sshPassphrase?: string;
  extraMetadata?: string;
}
