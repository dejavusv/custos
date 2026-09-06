export interface ChunkMetadata {
  partNumber: number;
  fileName: string;
  filePath: string;
  sizeBytes: number;
  checksumSha256: string;
  status: string;
  retries: number;
}

export interface TransferManifest {
  originalFileName: string;
  originalFilePath: string;
  originalSizeBytes: number;
  originalChecksumSha256: string;
  chunkSizeBytes: number;
  totalChunks: number;
  chunks: ChunkMetadata[];
  createdAt: string;
}

export interface TransferResult {
  success: boolean;
  targetType: string;
  remoteDestination: string;
  totalChunks: number;
  successfulChunks: number;
  failedChunks: number;
  totalBytesTransferred: number;
  totalRetries: number;
  durationMs: number;
  manifest: TransferManifest;
  errorMessage?: string;
  executedAt: string;
}

export interface SplitFileRequest {
  sourceFilePath: string;
  outputDirectory?: string;
  chunkSizeBytes: number;
}

export interface MergeFileRequest {
  chunkFilePaths: string[];
  destinationFilePath: string;
}

export interface TransferRequest {
  sourceFilePath: string;
  credentialId?: string;
  protocol?: string;
  host?: string;
  port?: number;
  username?: string;
  password?: string;
  remoteDirectory?: string;
  chunkSizeBytes?: number;
  maxRetriesPerChunk?: number;
  uploadManifest?: boolean;
}

export interface StoragePrecheckRequest {
  path: string;
  requiredBytes?: number;
  safetyMargin?: number;
}

export interface StoragePrecheckResult {
  targetPath: string;
  usableSpaceBytes: number;
  totalSpaceBytes: number;
  requiredBytes: number;
  safetyMargin: number;
  hasEnoughSpace: boolean;
  freeSpacePercentage: number;
  message: string;
}
