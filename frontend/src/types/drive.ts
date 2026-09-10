export interface DriveUploadResponse {
  driveFileId: string;
  fileName: string;
  fileExtension: string;
  fileSize: number;
  mimeType: string;
  folderId?: string;
  webViewLink?: string;
  webContentLink?: string;
  systemSource: string;
  uploadedBy: string;
  status: string;
  createdAt: string;
}

export interface FileUploadAuditRecord {
  id: string;
  systemSource: string;
  driveFileId: string;
  fileName: string;
  fileExtension: string;
  fileSize: number;
  mimeType: string;
  folderId?: string;
  webViewLink?: string;
  webContentLink?: string;
  uploadedBy: string;
  status: string;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface DriveHistoryQueryParams {
  systemSource?: string;
  status?: string;
  limit?: number;
}
