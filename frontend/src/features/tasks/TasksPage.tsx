import React, { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import {
  HardDrive,
  Database,
  FolderArchive,
  Trash2,
  Play,
  Loader2,
  CheckCircle2,
  AlertTriangle,
  Copy,
  Check,
  ShieldCheck,
  Calendar,
  Share2,
  Server,
  Layers,
  FileCheck,
  FolderOpen,
} from 'lucide-react';
import { vaultApi } from '../../services/vaultApi';
import { backupApi } from '../../services/backupApi';
import { transferApi } from '../../services/transferApi';
import {
  BackupResult,
  CompressionFormat,
  RetentionCleanupResult,
} from '../../types/backup';
import {
  TransferManifest,
  TransferResult,
  StoragePrecheckResult,
} from '../../types/transfer';
import { Card, CardContent, CardHeader, CardTitle } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { StorageBrowserDialog } from '../../components/storage/StorageBrowserDialog';

export const TasksPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'database' | 'filesystem' | 'retention' | 'transfer'>('database');
  const [copiedChecksum, setCopiedChecksum] = useState<string | null>(null);

  // Database Backup Form State
  const [selectedCredentialId, setSelectedCredentialId] = useState('');
  const [dbName, setDbName] = useState('');
  const [tables, setTables] = useState('');
  const [dbCompression, setDbCompression] = useState<CompressionFormat>('GZIP');
  const [dbDestinationDir, setDbDestinationDir] = useState('');
  const [dbResult, setDbResult] = useState<BackupResult | null>(null);
  const [dbError, setDbError] = useState<string | null>(null);

  // File Backup Form State
  const [sourcePath, setSourcePath] = useState('');
  const [fileDestinationDir, setFileDestinationDir] = useState('');
  const [fileCompression, setFileCompression] = useState<CompressionFormat>('TAR_GZ');
  const [exclusions, setExclusions] = useState('node_modules/**, *.log, temp/**');
  const [fileResult, setFileResult] = useState<BackupResult | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);

  // Retention Cleanup Form State
  const [cleanupDir, setCleanupDir] = useState('storage/backups');
  const [retentionDays, setRetentionDays] = useState(30);
  const [filePattern, setFilePattern] = useState('*.gz');
  const [cleanupResult, setCleanupResult] = useState<RetentionCleanupResult | null>(null);
  const [cleanupError, setCleanupError] = useState<string | null>(null);

  // Transfer & Chunking Form State
  const [transferSourceFile, setTransferSourceFile] = useState('');
  const [chunkSizeMb, setChunkSizeMb] = useState(50);
  const [transferCredentialId, setTransferCredentialId] = useState('');
  const [remoteUploadDir, setRemoteUploadDir] = useState('/upload');
  const [maxRetries, setMaxRetries] = useState(3);
  const [precheckPath, setPrecheckPath] = useState('storage/backups');
  const [precheckResult, setPrecheckResult] = useState<StoragePrecheckResult | null>(null);
  const [splitManifest, setSplitManifest] = useState<TransferManifest | null>(null);
  const [transferResult, setTransferResult] = useState<TransferResult | null>(null);
  const [transferError, setTransferError] = useState<string | null>(null);

  // Storage Browser Dialog State
  type TargetField =
    | 'dbDestinationDir'
    | 'sourcePath'
    | 'fileDestinationDir'
    | 'transferSourceFile'
    | 'precheckPath'
    | 'cleanupDir';

  const [browserOpen, setBrowserOpen] = useState(false);
  const [browserMode, setBrowserMode] = useState<'folder' | 'file' | 'both'>('folder');
  const [browserTitle, setBrowserTitle] = useState('เลือกโฟลเดอร์จาก Storage บน Server');
  const [browserTargetField, setBrowserTargetField] = useState<TargetField | null>(null);
  const [browserInitialPath, setBrowserInitialPath] = useState<string | undefined>(undefined);

  const openBrowser = (
    field: TargetField,
    mode: 'folder' | 'file' | 'both' = 'folder',
    title?: string,
    initialVal?: string
  ) => {
    setBrowserTargetField(field);
    setBrowserMode(mode);
    setBrowserTitle(title || (mode === 'file' ? 'เลือกไฟล์จาก Storage บน Server' : 'เลือกโฟลเดอร์จาก Storage บน Server'));
    setBrowserInitialPath(initialVal || undefined);
    setBrowserOpen(true);
  };

  const handleStorageSelect = (selectedPath: string) => {
    if (!browserTargetField) return;
    switch (browserTargetField) {
      case 'dbDestinationDir':
        setDbDestinationDir(selectedPath);
        break;
      case 'sourcePath':
        setSourcePath(selectedPath);
        break;
      case 'fileDestinationDir':
        setFileDestinationDir(selectedPath);
        break;
      case 'transferSourceFile':
        setTransferSourceFile(selectedPath);
        break;
      case 'precheckPath':
        setPrecheckPath(selectedPath);
        break;
      case 'cleanupDir':
        setCleanupDir(selectedPath);
        break;
    }
  };

  // Fetch credentials for DB and Transfer dropdowns
  const { data: credentials } = useQuery({
    queryKey: ['vault-credentials-all'],
    queryFn: async () => await vaultApi.getAllCredentials(),
  });

  const dbCredentials = credentials?.filter(
    (c) => c.credentialType === 'DATABASE_POSTGRESQL' || c.credentialType === 'DATABASE_MYSQL'
  ) || [];

  const transferCredentials = credentials?.filter(
    (c) => c.credentialType === 'SFTP' || c.credentialType === 'FTP' || c.credentialType === 'FTPS'
  ) || [];

  const handleCredentialSelect = (credId: string) => {
    setSelectedCredentialId(credId);
    const found = dbCredentials.find((c) => c.id === credId);
    if (found?.databaseName) {
      setDbName(found.databaseName);
    }
  };

  // DB Backup Mutation
  const dbBackupMutation = useMutation({
    mutationFn: async () => {
      setDbError(null);
      setDbResult(null);
      const tablesList = tables.trim() ? tables.split(',').map((t) => t.trim()) : undefined;
      return await backupApi.triggerDatabaseBackup({
        credentialId: selectedCredentialId || undefined,
        databaseName: dbName.trim(),
        tables: tablesList,
        compressionFormat: dbCompression,
        destinationDir: dbDestinationDir.trim() || undefined,
      });
    },
    onSuccess: (data) => setDbResult(data),
    onError: (err: any) => setDbError(err.response?.data?.message || err.message || 'Database backup failed'),
  });

  // File Backup Mutation
  const fileBackupMutation = useMutation({
    mutationFn: async () => {
      setFileError(null);
      setFileResult(null);
      const exclusionList = exclusions.trim() ? exclusions.split(',').map((e) => e.trim()) : undefined;
      return await backupApi.triggerFileSystemBackup({
        sourcePath: sourcePath.trim(),
        destinationDir: fileDestinationDir.trim() || undefined,
        compressionFormat: fileCompression,
        exclusionPatterns: exclusionList,
      });
    },
    onSuccess: (data) => setFileResult(data),
    onError: (err: any) => setFileError(err.response?.data?.message || err.message || 'File backup failed'),
  });

  // Retention Cleanup Mutation
  const retentionMutation = useMutation({
    mutationFn: async () => {
      setCleanupError(null);
      setCleanupResult(null);
      return await backupApi.triggerRetentionCleanup({
        backupDirectory: cleanupDir.trim(),
        retentionDays: Number(retentionDays),
        filePattern: filePattern.trim() || undefined,
      });
    },
    onSuccess: (data) => setCleanupResult(data),
    onError: (err: any) => setCleanupError(err.response?.data?.message || err.message || 'Retention cleanup failed'),
  });

  // Pre-check Mutation
  const precheckMutation = useMutation({
    mutationFn: async () => {
      return await transferApi.precheckStorage({
        path: precheckPath.trim(),
        requiredBytes: chunkSizeMb * 1024 * 1024,
        safetyMargin: 1.2,
      });
    },
    onSuccess: (data) => setPrecheckResult(data),
    onError: (err: any) => setTransferError(err.response?.data?.message || err.message || 'Pre-check failed'),
  });

  // Split File Mutation
  const splitMutation = useMutation({
    mutationFn: async () => {
      setTransferError(null);
      return await transferApi.splitFile({
        sourceFilePath: transferSourceFile.trim(),
        chunkSizeBytes: chunkSizeMb * 1024 * 1024,
      });
    },
    onSuccess: (data) => setSplitManifest(data),
    onError: (err: any) => setTransferError(err.response?.data?.message || err.message || 'File split failed'),
  });

  // Resilient Transfer Mutation
  const transferMutation = useMutation({
    mutationFn: async () => {
      if (!transferCredentialId) {
        throw new Error('กรุณาเลือก SFTP / FTP Credential จาก Vault ก่อนเริ่มส่งไฟล์');
      }
      setTransferError(null);
      setTransferResult(null);
      return await transferApi.uploadFile({
        sourceFilePath: transferSourceFile.trim(),
        credentialId: transferCredentialId,
        remoteDirectory: remoteUploadDir.trim(),
        chunkSizeBytes: chunkSizeMb * 1024 * 1024,
        maxRetriesPerChunk: Number(maxRetries),
        uploadManifest: true,
      });
    },
    onSuccess: (data) => setTransferResult(data),
    onError: (err: any) => setTransferError(err.response?.data?.message || err.message || 'Transfer failed'),
  });

  const handleCopy = (text: string, key: string) => {
    navigator.clipboard.writeText(text);
    setCopiedChecksum(key);
    setTimeout(() => setCopiedChecksum(null), 2000);
  };

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2.5">
          <HardDrive className="w-6 h-6 text-primary" />
          Task & Backup Engine Hub
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          ระบบสำรองฐานข้อมูล (Zero-RAM Gzip Streaming), การบีบอัดไฟล์/ไดเรกทอรี และ Chunk Split & Transfer Engine
        </p>
      </div>

      {/* Navigation Tabs */}
      <div className="flex border-b border-border gap-2 overflow-x-auto">
        <button
          onClick={() => setActiveTab('database')}
          className={`flex items-center gap-2 pb-3 px-4 text-sm font-medium border-b-2 whitespace-nowrap transition-all ${
            activeTab === 'database'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          <Database className="w-4 h-4" />
          Database Backup (Zero-RAM)
        </button>
        <button
          onClick={() => setActiveTab('filesystem')}
          className={`flex items-center gap-2 pb-3 px-4 text-sm font-medium border-b-2 whitespace-nowrap transition-all ${
            activeTab === 'filesystem'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          <FolderArchive className="w-4 h-4" />
          Directory & File Archiver
        </button>
        <button
          onClick={() => setActiveTab('transfer')}
          className={`flex items-center gap-2 pb-3 px-4 text-sm font-medium border-b-2 whitespace-nowrap transition-all ${
            activeTab === 'transfer'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          <Share2 className="w-4 h-4" />
          Chunk Split & Resilient Transfer
        </button>
        <button
          onClick={() => setActiveTab('retention')}
          className={`flex items-center gap-2 pb-3 px-4 text-sm font-medium border-b-2 whitespace-nowrap transition-all ${
            activeTab === 'retention'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          <Trash2 className="w-4 h-4" />
          Retention Policy Cleanup
        </button>
      </div>

      {/* Tab 1: Database Backup */}
      {activeTab === 'database' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <Card className="lg:col-span-2 border-border bg-card shadow-sm">
            <CardHeader className="pb-4">
              <CardTitle className="text-lg font-bold flex items-center gap-2">
                <Database className="w-5 h-5 text-primary" />
                สั่งสำรองฐานข้อมูล On-Demand
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {dbError && (
                <div className="p-3 text-sm text-destructive bg-destructive/10 border border-destructive/20 rounded-md flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 flex-shrink-0" />
                  <span>{dbError}</span>
                </div>
              )}

              <div className="space-y-1.5">
                <label className="text-xs font-semibold uppercase text-muted-foreground">
                  เลือก Connection Profile จาก Vault
                </label>
                <select
                  value={selectedCredentialId}
                  onChange={(e) => handleCredentialSelect(e.target.value)}
                  className="w-full px-3 py-2 text-sm rounded-md border border-input bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
                >
                  <option value="">-- กรุณาเลือก Credential จาก Vault --</option>
                  {dbCredentials.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name} ({c.credentialType === 'DATABASE_POSTGRESQL' ? 'PostgreSQL' : 'MySQL'}) - {c.host}:{c.port}
                    </option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-foreground">
                    ชื่อฐานข้อมูล (Database Name) <span className="text-destructive">*</span>
                  </label>
                  <Input
                    placeholder="เช่น custos_db"
                    value={dbName}
                    onChange={(e) => setDbName(e.target.value)}
                  />
                </div>

                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-foreground">
                    รูปแบบการบีบอัด (Compression)
                  </label>
                  <select
                    value={dbCompression}
                    onChange={(e) => setDbCompression(e.target.value as CompressionFormat)}
                    className="w-full px-3 py-2 text-sm rounded-md border border-input bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
                  >
                    <option value="GZIP">GZIP (.sql.gz) - สตรีม Zero-RAM</option>
                    <option value="NONE">Plain SQL (.sql) - ไม่บีบอัด</option>
                  </select>
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground">
                  เฉพาะตารางที่กำหนด (ระบุคั่นด้วยจุลภาค หรือเว้นว่างเพื่อ Dump ทั้งฐานข้อมูล)
                </label>
                <Input
                  placeholder="เช่น users, audit_logs, credentials_vault"
                  value={tables}
                  onChange={(e) => setTables(e.target.value)}
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground flex items-center justify-between">
                  <span>โฟลเดอร์ปลายทางสำหรับจัดเก็บไฟล์ (Destination Directory)</span>
                  <span className="text-[11px] text-muted-foreground font-normal">
                    (เว้นว่างเพื่อใช้ดีฟอลต์จาก server: custos.backup.default-directory)
                  </span>
                </label>
                <div className="flex gap-2">
                  <Input
                    placeholder="เช่น storage/backups"
                    value={dbDestinationDir}
                    onChange={(e) => setDbDestinationDir(e.target.value)}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() =>
                      openBrowser(
                        'dbDestinationDir',
                        'folder',
                        'เลือกโฟลเดอร์ปลายทางสำหรับจัดเก็บ Database Backup',
                        dbDestinationDir
                      )
                    }
                    className="flex items-center gap-1.5 shrink-0"
                  >
                    <FolderOpen className="w-4 h-4 text-primary" />
                    <span>เลือกโฟลเดอร์</span>
                  </Button>
                </div>
              </div>

              <Button
                onClick={() => dbBackupMutation.mutate()}
                disabled={!dbName.trim() || dbBackupMutation.isPending}
                className="w-full gap-2 mt-4"
              >
                {dbBackupMutation.isPending ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Play className="w-4 h-4 fill-current" />
                )}
                เริ่มกระบวนการ Dump & Stream Gzip
              </Button>
            </CardContent>
          </Card>

          {/* Database Backup Result Card */}
          <Card className="border-border bg-card shadow-sm">
            <CardHeader className="pb-3">
              <CardTitle className="text-base font-bold flex items-center gap-2">
                <ShieldCheck className="w-5 h-5 text-emerald-500" />
                ผลลัพธ์การประมวลผล (Result)
              </CardTitle>
            </CardHeader>
            <CardContent>
              {dbResult ? (
                <div className="space-y-3 text-sm">
                  <div className="p-3 bg-emerald-500/10 border border-emerald-500/20 rounded-md text-emerald-500 font-medium flex items-center gap-2 text-xs">
                    <CheckCircle2 className="w-4 h-4" /> สำรองข้อมูลสำเร็จ 100%
                  </div>
                  <div>
                    <span className="text-xs text-muted-foreground block">ไฟล์ปลายทาง:</span>
                    <span className="font-mono text-xs text-foreground break-all">{dbResult.fileName}</span>
                  </div>
                  <div className="grid grid-cols-2 gap-2 text-xs">
                    <div className="p-2 bg-secondary/30 rounded border border-border">
                      <span className="text-muted-foreground block">ขนาดไฟล์บีบอัด</span>
                      <span className="font-bold text-foreground">{formatBytes(dbResult.fileSizeBytes)}</span>
                    </div>
                    <div className="p-2 bg-secondary/30 rounded border border-border">
                      <span className="text-muted-foreground block">เวลาที่ใช้</span>
                      <span className="font-bold text-foreground">{dbResult.durationMs} ms</span>
                    </div>
                  </div>
                  <div>
                    <span className="text-xs text-muted-foreground block">SHA-256 Checksum:</span>
                    <div className="flex items-center gap-1.5 mt-1">
                      <input
                        readOnly
                        value={dbResult.checksumSha256}
                        className="w-full text-[11px] font-mono p-1.5 bg-secondary/30 border border-border rounded text-foreground"
                      />
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => handleCopy(dbResult.checksumSha256, 'db')}
                        className="h-8 w-8 p-0 flex-shrink-0"
                      >
                        {copiedChecksum === 'db' ? <Check className="w-3.5 h-3.5 text-emerald-500" /> : <Copy className="w-3.5 h-3.5" />}
                      </Button>
                    </div>
                  </div>
                </div>
              ) : (
                <div className="py-12 text-center text-muted-foreground text-xs">
                  ยังไม่มีการรันงานสำรองข้อมูล
                  <br />
                  ผลลัพธ์และ Checksum จะแสดงที่นี่
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}

      {/* Tab 2: File & Directory Archiver */}
      {activeTab === 'filesystem' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <Card className="lg:col-span-2 border-border bg-card shadow-sm">
            <CardHeader className="pb-4">
              <CardTitle className="text-lg font-bold flex items-center gap-2">
                <FolderArchive className="w-5 h-5 text-primary" />
                สั่งบีบอัดไฟล์และโฟลเดอร์ (Directory Archiver)
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {fileError && (
                <div className="p-3 text-sm text-destructive bg-destructive/10 border border-destructive/20 rounded-md flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 flex-shrink-0" />
                  <span>{fileError}</span>
                </div>
              )}

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground">
                  พาธโฟลเดอร์หรือไฟล์ต้นทาง (Source Path) <span className="text-destructive">*</span>
                </label>
                <div className="flex gap-2">
                  <Input
                    placeholder="เช่น storage หรือ storage/backups หรือ /var/www/html"
                    value={sourcePath}
                    onChange={(e) => setSourcePath(e.target.value)}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() =>
                      openBrowser(
                        'sourcePath',
                        'both',
                        'เลือกไฟล์หรือโฟลเดอร์ต้นทาง (Source Path)',
                        sourcePath
                      )
                    }
                    className="flex items-center gap-1.5 shrink-0"
                  >
                    <FolderOpen className="w-4 h-4 text-primary" />
                    <span>เลือกจาก Server</span>
                  </Button>
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground">
                  รูปแบบการบีบอัด (Archive Format)
                </label>
                <div className="flex gap-3">
                  <button
                    type="button"
                    onClick={() => setFileCompression('TAR_GZ')}
                    className={`flex-1 py-2 px-3 text-xs font-medium rounded-lg border text-center transition-all ${
                      fileCompression === 'TAR_GZ'
                        ? 'bg-primary text-primary-foreground border-primary shadow-sm'
                        : 'border-border bg-card hover:bg-secondary/50 text-foreground'
                    }`}
                  >
                    .tar.gz (POSIX Tarball + Gzip)
                  </button>
                  <button
                    type="button"
                    onClick={() => setFileCompression('ZIP')}
                    className={`flex-1 py-2 px-3 text-xs font-medium rounded-lg border text-center transition-all ${
                      fileCompression === 'ZIP'
                        ? 'bg-primary text-primary-foreground border-primary shadow-sm'
                        : 'border-border bg-card hover:bg-secondary/50 text-foreground'
                    }`}
                  >
                    .zip (Standard Zip Archive)
                  </button>
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground">
                  Exclusion Patterns (ระบุ Glob patterns คั่นด้วยจุลภาคเพื่อยกเว้นไฟล์/โฟลเดอร์)
                </label>
                <Input
                  placeholder="node_modules/**, *.log, temp/**, .git/**"
                  value={exclusions}
                  onChange={(e) => setExclusions(e.target.value)}
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground flex items-center justify-between">
                  <span>โฟลเดอร์ปลายทางสำหรับจัดเก็บไฟล์ (Destination Directory)</span>
                  <span className="text-[11px] text-muted-foreground font-normal">
                    (เว้นว่างเพื่อใช้ดีฟอลต์จาก server: custos.backup.default-directory)
                  </span>
                </label>
                <div className="flex gap-2">
                  <Input
                    placeholder="เช่น storage/backups"
                    value={fileDestinationDir}
                    onChange={(e) => setFileDestinationDir(e.target.value)}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() =>
                      openBrowser(
                        'fileDestinationDir',
                        'folder',
                        'เลือกโฟลเดอร์ปลายทางสำหรับจัดเก็บ Archive',
                        fileDestinationDir
                      )
                    }
                    className="flex items-center gap-1.5 shrink-0"
                  >
                    <FolderOpen className="w-4 h-4 text-primary" />
                    <span>เลือกโฟลเดอร์</span>
                  </Button>
                </div>
              </div>

              <Button
                onClick={() => fileBackupMutation.mutate()}
                disabled={!sourcePath.trim() || fileBackupMutation.isPending}
                className="w-full gap-2 mt-4"
              >
                {fileBackupMutation.isPending ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Play className="w-4 h-4 fill-current" />
                )}
                เริ่มกระบวนการ Archive
              </Button>
            </CardContent>
          </Card>

          {/* File Backup Result Card */}
          <Card className="border-border bg-card shadow-sm">
            <CardHeader className="pb-3">
              <CardTitle className="text-base font-bold flex items-center gap-2">
                <ShieldCheck className="w-5 h-5 text-emerald-500" />
                ผลลัพธ์การ Archive
              </CardTitle>
            </CardHeader>
            <CardContent>
              {fileResult ? (
                <div className="space-y-3 text-sm">
                  <div className="p-3 bg-emerald-500/10 border border-emerald-500/20 rounded-md text-emerald-500 font-medium flex items-center gap-2 text-xs">
                    <CheckCircle2 className="w-4 h-4" /> บีบอัดและสร้าง Checksum สำเร็จ
                  </div>
                  <div>
                    <span className="text-xs text-muted-foreground block">ชื่อไฟล์:</span>
                    <span className="font-mono text-xs text-foreground break-all">{fileResult.fileName}</span>
                  </div>
                  <div className="grid grid-cols-2 gap-2 text-xs">
                    <div className="p-2 bg-secondary/30 rounded border border-border">
                      <span className="text-muted-foreground block">จำนวนไฟล์</span>
                      <span className="font-bold text-foreground">{fileResult.itemCount} ไฟล์</span>
                    </div>
                    <div className="p-2 bg-secondary/30 rounded border border-border">
                      <span className="text-muted-foreground block">ขนาดที่ได้</span>
                      <span className="font-bold text-foreground">{formatBytes(fileResult.fileSizeBytes)}</span>
                    </div>
                  </div>
                  <div>
                    <span className="text-xs text-muted-foreground block">SHA-256 Checksum:</span>
                    <div className="flex items-center gap-1.5 mt-1">
                      <input
                        readOnly
                        value={fileResult.checksumSha256}
                        className="w-full text-[11px] font-mono p-1.5 bg-secondary/30 border border-border rounded text-foreground"
                      />
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => handleCopy(fileResult.checksumSha256, 'file')}
                        className="h-8 w-8 p-0 flex-shrink-0"
                      >
                        {copiedChecksum === 'file' ? <Check className="w-3.5 h-3.5 text-emerald-500" /> : <Copy className="w-3.5 h-3.5" />}
                      </Button>
                    </div>
                  </div>
                </div>
              ) : (
                <div className="py-12 text-center text-muted-foreground text-xs">
                  ยังไม่มีการรันงาน Archive
                  <br />
                  ผลลัพธ์และ Checksum จะแสดงที่นี่
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}

      {/* Tab 3: Chunk Split & Resilient Transfer */}
      {activeTab === 'transfer' && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <Card className="lg:col-span-2 border-border bg-card shadow-sm">
              <CardHeader className="pb-4">
                <CardTitle className="text-lg font-bold flex items-center gap-2">
                  <Share2 className="w-5 h-5 text-primary" />
                  Chunk Splitter & Resilient Sequential Transfer
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {transferError && (
                  <div className="p-3 text-sm text-destructive bg-destructive/10 border border-destructive/20 rounded-md flex items-center gap-2">
                    <AlertTriangle className="w-4 h-4 flex-shrink-0" />
                    <span>{transferError}</span>
                  </div>
                )}

                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-foreground">
                    ไฟล์ต้นทางที่ต้องการแบ่งส่วน/ส่ง (Source File Path) <span className="text-destructive">*</span>
                  </label>
                  <div className="flex gap-2">
                    <Input
                      placeholder="เช่น storage/backups/production_db_20260906.sql.gz"
                      value={transferSourceFile}
                      onChange={(e) => setTransferSourceFile(e.target.value)}
                    />
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() =>
                        openBrowser(
                          'transferSourceFile',
                          'file',
                          'เลือกไฟล์ต้นทางสำหรับ Chunk Split & Transfer',
                          transferSourceFile
                        )
                      }
                      className="flex items-center gap-1.5 shrink-0"
                    >
                      <FolderOpen className="w-4 h-4 text-primary" />
                      <span>เลือกไฟล์</span>
                    </Button>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <label className="text-xs font-semibold text-foreground">
                      ขนาดต่อ Chunk (MB)
                    </label>
                    <Input
                      type="number"
                      min="1"
                      value={chunkSizeMb}
                      onChange={(e) => setChunkSizeMb(Number(e.target.value))}
                    />
                  </div>

                  <div className="space-y-1.5">
                    <label className="text-xs font-semibold text-foreground">
                      จำนวนครั้ง Auto-Retry สูงสุดต่อ Chunk
                    </label>
                    <Input
                      type="number"
                      min="1"
                      max="10"
                      value={maxRetries}
                      onChange={(e) => setMaxRetries(Number(e.target.value))}
                    />
                  </div>
                </div>

                {/* Target Transfer Vault Profile */}
                <div className="space-y-1.5 pt-2 border-t border-border">
                  <div className="flex items-center justify-between">
                    <label className="text-xs font-semibold uppercase text-muted-foreground flex items-center gap-1.5">
                      <Server className="w-3.5 h-3.5 text-primary" /> เลือกปลายทาง SFTP / FTP จาก Vault <span className="text-destructive">*</span>
                    </label>
                    <a
                      href="/vault"
                      className="text-xs text-primary hover:underline"
                    >
                      + จัดการ Vault Profile
                    </a>
                  </div>
                  <select
                    value={transferCredentialId}
                    onChange={(e) => setTransferCredentialId(e.target.value)}
                    className="w-full px-3 py-2 text-sm rounded-md border border-input bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
                  >
                    <option value="">-- กรุณาเลือก Credential SFTP/FTP/FTPS จาก Vault --</option>
                    {transferCredentials.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name} ({c.credentialType}) - {c.host}:{c.port}
                      </option>
                    ))}
                  </select>
                  {!transferCredentialId && (
                    <p className="text-[11px] text-amber-400">
                      * จำเป็นต้องเลือก Credential ปลายทางก่อนเริ่มกระบวนการส่งไฟล์ (หากยังไม่มี สามารถสร้างได้ที่หน้า Credentials Vault)
                    </p>
                  )}
                </div>

                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-foreground">
                    โฟลเดอร์ปลายทางบนเซิร์ฟเวอร์รีโมต (Remote Directory)
                  </label>
                  <Input
                    placeholder="/upload หรือ /backup/storage"
                    value={remoteUploadDir}
                    onChange={(e) => setRemoteUploadDir(e.target.value)}
                  />
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2">
                  <Button
                    variant="outline"
                    onClick={() => splitMutation.mutate()}
                    disabled={!transferSourceFile.trim() || splitMutation.isPending}
                    className="gap-2"
                  >
                    {splitMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Layers className="w-4 h-4" />}
                    ตัดแบ่งไฟล์ (Split Only)
                  </Button>

                  <Button
                    onClick={() => transferMutation.mutate()}
                    disabled={!transferSourceFile.trim() || !transferCredentialId || transferMutation.isPending}
                    className="gap-2"
                  >
                    {transferMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4 fill-current" />}
                    เริ่มส่งไฟล์ Sequential + Auto-Retry
                  </Button>
                </div>
              </CardContent>
            </Card>

            {/* Storage Pre-check Box */}
            <div className="space-y-4">
              <Card className="border-border bg-card shadow-sm">
                <CardHeader className="pb-3">
                  <CardTitle className="text-base font-bold flex items-center gap-2">
                    <HardDrive className="w-5 h-5 text-primary" />
                    Storage Pre-check
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                  <div className="space-y-1">
                    <label className="text-xs text-muted-foreground">ตำแหน่งดิสก์ที่ต้องการตรวจสอบ</label>
                    <div className="flex gap-2">
                      <Input
                        value={precheckPath}
                        onChange={(e) => setPrecheckPath(e.target.value)}
                        placeholder="เช่น storage/backups"
                        className="text-xs"
                      />
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() =>
                          openBrowser(
                            'precheckPath',
                            'folder',
                            'เลือกตำแหน่งโฟลเดอร์สำหรับตรวจเช็คดิสก์',
                            precheckPath
                          )
                        }
                        className="px-2 shrink-0"
                      >
                        <FolderOpen className="w-3.5 h-3.5 text-primary" />
                      </Button>
                    </div>
                  </div>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => precheckMutation.mutate()}
                    disabled={precheckMutation.isPending}
                    className="w-full gap-1.5"
                  >
                    {precheckMutation.isPending && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                    ตรวจสอบพื้นที่ดิสก์ว่าง
                  </Button>

                  {precheckResult && (
                    <div className="p-3 bg-secondary/30 rounded border border-border space-y-2 text-xs">
                      <div className="flex items-center justify-between">
                        <span className="text-muted-foreground">สถานะ:</span>
                        <span className={`font-bold ${precheckResult.hasEnoughSpace ? 'text-emerald-500' : 'text-destructive'}`}>
                          {precheckResult.hasEnoughSpace ? 'พื้นที่เพียงพอ' : 'พื้นที่ไม่พอ!'}
                        </span>
                      </div>
                      <div className="flex items-center justify-between">
                        <span className="text-muted-foreground">พื้นที่ว่าง (Usable):</span>
                        <span className="font-mono font-bold text-foreground">{formatBytes(precheckResult.usableSpaceBytes)}</span>
                      </div>
                      <div className="flex items-center justify-between">
                        <span className="text-muted-foreground">เปอร์เซ็นต์ว่าง:</span>
                        <span className="font-bold text-foreground">{precheckResult.freeSpacePercentage.toFixed(1)}%</span>
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>

              {/* Transfer Result Summary */}
              {transferResult && (
                <Card className="border-emerald-500/20 bg-emerald-500/5 shadow-sm">
                  <CardHeader className="pb-2">
                    <CardTitle className="text-sm font-bold text-emerald-500 flex items-center gap-1.5">
                      <CheckCircle2 className="w-4 h-4" /> ถ่ายโอนข้อมูลสำเร็จ
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-2 text-xs">
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">จำนวน Chunk:</span>
                      <span className="font-bold">{transferResult.successfulChunks} / {transferResult.totalChunks}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">ข้อมูลที่ส่ง:</span>
                      <span className="font-bold">{formatBytes(transferResult.totalBytesTransferred)}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">จำนวน Retry:</span>
                      <span className="font-bold">{transferResult.totalRetries} ครั้ง</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">เวลาที่ใช้:</span>
                      <span className="font-bold">{transferResult.durationMs} ms</span>
                    </div>
                  </CardContent>
                </Card>
              )}
            </div>
          </div>

          {/* Chunk Manifest Table */}
          {splitManifest && (
            <Card className="border-border bg-card shadow-sm overflow-hidden">
              <CardHeader className="pb-3 bg-secondary/20 border-b border-border">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-base font-bold flex items-center gap-2">
                    <FileCheck className="w-5 h-5 text-primary" />
                    Chunk Split Manifest ({splitManifest.totalChunks} Chunks)
                  </CardTitle>
                  <span className="text-xs text-muted-foreground font-mono">
                    Original SHA-256: {splitManifest.originalChecksumSha256.substring(0, 16)}...
                  </span>
                </div>
              </CardHeader>
              <div className="overflow-x-auto">
                <table className="w-full text-xs text-left">
                  <thead className="bg-secondary/40 text-muted-foreground uppercase border-b border-border">
                    <tr>
                      <th className="px-4 py-2.5">Part #</th>
                      <th className="px-4 py-2.5">Chunk File Name</th>
                      <th className="px-4 py-2.5">ขนาด</th>
                      <th className="px-4 py-2.5">SHA-256 Checksum</th>
                      <th className="px-4 py-2.5 text-right">สถานะ</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {splitManifest.chunks.map((chunk) => (
                      <tr key={chunk.partNumber} className="hover:bg-secondary/20">
                        <td className="px-4 py-2.5 font-bold">#{chunk.partNumber}</td>
                        <td className="px-4 py-2.5 font-mono text-foreground">{chunk.fileName}</td>
                        <td className="px-4 py-2.5">{formatBytes(chunk.sizeBytes)}</td>
                        <td className="px-4 py-2.5 font-mono text-[11px] text-muted-foreground">
                          {chunk.checksumSha256}
                        </td>
                        <td className="px-4 py-2.5 text-right">
                          <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/10 text-emerald-500 border border-emerald-500/20">
                            READY
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          )}
        </div>
      )}

      {/* Tab 4: Retention Policy Cleanup */}
      {activeTab === 'retention' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <Card className="lg:col-span-2 border-border bg-card shadow-sm">
            <CardHeader className="pb-4">
              <CardTitle className="text-lg font-bold flex items-center gap-2">
                <Trash2 className="w-5 h-5 text-destructive" />
                ตรวจสอบและล้างไฟล์สำรองเก่า (Retention Policy)
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {cleanupError && (
                <div className="p-3 text-sm text-destructive bg-destructive/10 border border-destructive/20 rounded-md flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 flex-shrink-0" />
                  <span>{cleanupError}</span>
                </div>
              )}

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground">
                  ไดเรกทอรีจัดเก็บไฟล์สำรอง (Backup Directory) <span className="text-destructive">*</span>
                </label>
                <div className="flex gap-2">
                  <Input
                    placeholder="เช่น storage/backups"
                    value={cleanupDir}
                    onChange={(e) => setCleanupDir(e.target.value)}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() =>
                      openBrowser(
                        'cleanupDir',
                        'folder',
                        'เลือกโฟลเดอร์สำหรับสแกน Retention Policy',
                        cleanupDir
                      )
                    }
                    className="flex items-center gap-1.5 shrink-0"
                  >
                    <FolderOpen className="w-4 h-4 text-primary" />
                    <span>เลือกโฟลเดอร์</span>
                  </Button>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-foreground">
                    จำนวนวันที่ต้องเก็บไว้ (Retention Days) <span className="text-destructive">*</span>
                  </label>
                  <Input
                    type="number"
                    min="0"
                    placeholder="เช่น 30"
                    value={retentionDays}
                    onChange={(e) => setRetentionDays(Number(e.target.value))}
                  />
                  <p className="text-[11px] text-muted-foreground">
                    ไฟล์ที่เก่ากว่าจำนวนวันนี้จะถูกลบทิ้งอย่างปลอดภัย
                  </p>
                </div>

                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-foreground">
                    รูปแบบไฟล์ที่ตรวจสอบ (File Pattern Glob)
                  </label>
                  <Input
                    placeholder="*.gz หรือ *.tar.gz"
                    value={filePattern}
                    onChange={(e) => setFilePattern(e.target.value)}
                  />
                </div>
              </div>

              <Button
                variant="destructive"
                onClick={() => retentionMutation.mutate()}
                disabled={!cleanupDir.trim() || retentionMutation.isPending}
                className="w-full gap-2 mt-4"
              >
                {retentionMutation.isPending ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Trash2 className="w-4 h-4" />
                )}
                เริ่มสแกนและล้างไฟล์เก่าตาม Retention Policy
              </Button>
            </CardContent>
          </Card>

          {/* Retention Result Card */}
          <Card className="border-border bg-card shadow-sm">
            <CardHeader className="pb-3">
              <CardTitle className="text-base font-bold flex items-center gap-2">
                <Calendar className="w-5 h-5 text-primary" />
                สรุปการล้างไฟล์ (Pruning Summary)
              </CardTitle>
            </CardHeader>
            <CardContent>
              {cleanupResult ? (
                <div className="space-y-3 text-sm">
                  <div className="p-3 bg-secondary/50 border border-border rounded-md text-foreground font-medium text-xs">
                    {cleanupResult.message}
                  </div>
                  <div className="grid grid-cols-2 gap-2 text-xs">
                    <div className="p-2 bg-secondary/30 rounded border border-border">
                      <span className="text-muted-foreground block">สแกนทั้งหมด</span>
                      <span className="font-bold text-foreground">{cleanupResult.scannedFilesCount} ไฟล์</span>
                    </div>
                    <div className="p-2 bg-secondary/30 rounded border border-border">
                      <span className="text-muted-foreground block">ลบออก</span>
                      <span className="font-bold text-destructive">{cleanupResult.deletedFilesCount} ไฟล์</span>
                    </div>
                  </div>
                  <div className="p-2 bg-secondary/30 rounded border border-border text-xs">
                    <span className="text-muted-foreground block">เนื้อที่ที่ได้คืน (Freed Space)</span>
                    <span className="font-bold text-emerald-500">{formatBytes(cleanupResult.freedSpaceBytes)}</span>
                  </div>

                  {cleanupResult.deletedFileNames.length > 0 && (
                    <div>
                      <span className="text-xs text-muted-foreground block mb-1">รายชื่อไฟล์ที่ถูกลบ:</span>
                      <div className="max-h-32 overflow-y-auto space-y-1 p-2 bg-secondary/20 rounded border border-border text-[11px] font-mono">
                        {cleanupResult.deletedFileNames.map((fn, idx) => (
                          <div key={idx} className="text-destructive truncate">
                            - {fn}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              ) : (
                <div className="py-12 text-center text-muted-foreground text-xs">
                  ยังไม่มีการรัน Retention Policy
                  <br />
                  สรุปผลการลบไฟล์จะแสดงที่นี่
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}

      {/* Storage Browser Dialog */}
      <StorageBrowserDialog
        open={browserOpen}
        onOpenChange={setBrowserOpen}
        onSelect={handleStorageSelect}
        title={browserTitle}
        mode={browserMode}
        initialPath={browserInitialPath}
      />
    </div>
  );
};
