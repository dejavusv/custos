import React, { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  X,
  Save,
  FolderOpen,
  KeyRound,
  Database,
  Archive,
  Send,
  Mail,
  ChevronDown,
  ChevronUp,
  Sparkles,
} from 'lucide-react';
import { Button } from '../../../components/ui/Button';
import { Input } from '../../../components/ui/Input';
import { TaskType } from '../../../types/pipeline';
import { vaultApi } from '../../../services/vaultApi';
import { StorageBrowserDialog } from '../../../components/storage/StorageBrowserDialog';

interface NodeConfigModalProps {
  isOpen: boolean;
  onClose: () => void;
  nodeKey: string;
  nodeLabel: string;
  nodeType: TaskType;
  configJson: string;
  onSave: (updatedLabel: string, updatedConfigJson: string) => void;
}

export const NodeConfigModal: React.FC<NodeConfigModalProps> = ({
  isOpen,
  onClose,
  nodeKey,
  nodeLabel,
  nodeType,
  configJson,
  onSave,
}) => {
  const [label, setLabel] = useState(nodeLabel);
  const [formFields, setFormFields] = useState<Record<string, any>>({});
  const [showDirectConnection, setShowDirectConnection] = useState(false);

  // Storage Browser Dialog State
  const [browserOpen, setBrowserOpen] = useState(false);
  const [browserMode, setBrowserMode] = useState<'folder' | 'file' | 'both'>('folder');
  const [browserTitle, setBrowserTitle] = useState('เลือกไฟล์หรือโฟลเดอร์จาก Storage บน Server');
  const [browserTargetField, setBrowserTargetField] = useState<string | null>(null);
  const [browserInitialPath, setBrowserInitialPath] = useState<string | undefined>(undefined);

  // Load Vault Credentials
  const { data: credentials } = useQuery({
    queryKey: ['vault-credentials-all'],
    queryFn: async () => await vaultApi.getAllCredentials(),
    enabled: isOpen,
  });

  const dbCredentials =
    credentials?.filter(
      (c) => c.credentialType === 'DATABASE_POSTGRESQL' || c.credentialType === 'DATABASE_MYSQL'
    ) || [];

  const transferCredentials =
    credentials?.filter((c) => c.credentialType === 'SFTP' || c.credentialType === 'FTP') || [];

  useEffect(() => {
    setLabel(nodeLabel);
    try {
      const parsed = JSON.parse(configJson || '{}');
      const mapped: Record<string, any> = {};
      for (const [k, v] of Object.entries(parsed)) {
        mapped[k] = v ?? '';
      }
      if (nodeType === 'DATABASE_BACKUP' && !mapped.compressionFormat) {
        mapped.compressionFormat = 'GZIP';
      }
      if (nodeType === 'FILE_BACKUP' && !mapped.compressionFormat) {
        mapped.compressionFormat = 'TAR_GZ';
      }
      if (nodeType === 'FILE_BACKUP' && mapped.exclusionPatterns === undefined) {
        mapped.exclusionPatterns = 'node_modules/**, *.log, temp/**, .git/**';
      }
      if (nodeType === 'SPLIT_TRANSFER') {
        if (!mapped.chunkSizeMb && !mapped.chunkSizeBytes) {
          mapped.chunkSizeMb = '50';
        } else if (!mapped.chunkSizeMb && mapped.chunkSizeBytes) {
          mapped.chunkSizeMb = String(Math.round(Number(mapped.chunkSizeBytes) / (1024 * 1024)));
        }
        if (!mapped.maxRetries) mapped.maxRetries = '3';
        if (!mapped.remoteDirectory) mapped.remoteDirectory = '/upload';
        if (!mapped.sourceFilePath) mapped.sourceFilePath = '${last_output_path}';
      }

      setFormFields(mapped);

      if (mapped.host && !mapped.credentialId) {
        setShowDirectConnection(true);
      } else {
        setShowDirectConnection(false);
      }
    } catch {
      setFormFields({});
    }
  }, [nodeLabel, configJson, isOpen, nodeType]);

  if (!isOpen) return null;

  const handleFieldChange = (key: string, value: any) => {
    setFormFields((prev) => ({ ...prev, [key]: value }));
  };

  const handleDbCredentialSelect = (credId: string) => {
    handleFieldChange('credentialId', credId);
    const found = dbCredentials.find((c) => c.id === credId);
    if (found) {
      handleFieldChange('credentialName', found.name);
      if (found.databaseName && !formFields.databaseName) {
        handleFieldChange('databaseName', found.databaseName);
      }
      if (found.host) handleFieldChange('host', found.host);
      if (found.port) handleFieldChange('port', String(found.port));
      if (found.username) handleFieldChange('username', found.username);
    } else {
      handleFieldChange('credentialName', '');
    }
  };

  const handleTransferCredentialSelect = (credId: string) => {
    handleFieldChange('credentialId', credId);
    const found = transferCredentials.find((c) => c.id === credId);
    if (found) {
      handleFieldChange('credentialName', found.name);
      handleFieldChange('protocol', found.credentialType);
      if (found.host) handleFieldChange('host', found.host);
      if (found.port) handleFieldChange('port', String(found.port));
      if (found.username) handleFieldChange('username', found.username);
    } else {
      handleFieldChange('credentialName', '');
    }
  };

  const openBrowser = (
    field: string,
    mode: 'folder' | 'file' | 'both' = 'folder',
    title?: string,
    initialVal?: string
  ) => {
    setBrowserTargetField(field);
    setBrowserMode(mode);
    setBrowserTitle(
      title ||
        (mode === 'file'
          ? 'เลือกไฟล์จาก Storage บน Server'
          : 'เลือกโฟลเดอร์จาก Storage บน Server')
    );
    setBrowserInitialPath(initialVal || undefined);
    setBrowserOpen(true);
  };

  const handleStorageSelect = (selectedPath: string) => {
    if (!browserTargetField) return;
    handleFieldChange(browserTargetField, selectedPath);
    setBrowserOpen(false);
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    const payload = { ...formFields };
    if (payload.chunkSizeMb) {
      payload.chunkSizeBytes = Number(payload.chunkSizeMb) * 1024 * 1024;
    }
    onSave(label, JSON.stringify(payload));
    onClose();
  };

  return (
    <>
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 animate-in fade-in duration-150">
        <div className="bg-slate-900 border border-slate-800 w-full max-w-2xl rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
          {/* Header */}
          <div className="px-6 py-4 border-b border-slate-800 flex items-center justify-between bg-slate-900/90">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-primary/10 border border-primary/20 text-primary flex items-center justify-center">
                {nodeType === 'DATABASE_BACKUP' && <Database className="w-5 h-5 text-blue-400" />}
                {nodeType === 'FILE_BACKUP' && <Archive className="w-5 h-5 text-amber-400" />}
                {nodeType === 'SPLIT_TRANSFER' && <Send className="w-5 h-5 text-purple-400" />}
                {nodeType === 'EMAIL_ALERT' && <Mail className="w-5 h-5 text-emerald-400" />}
              </div>
              <div>
                <h2 className="text-base font-bold text-white tracking-tight">
                  กำหนดค่าขั้นตอน: {label || nodeLabel}
                </h2>
                <p className="text-xs text-slate-400 font-mono mt-0.5">
                  Node Key: {nodeKey} • Type: {nodeType}
                </p>
              </div>
            </div>
            <button
              onClick={onClose}
              className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Form Body */}
          <form onSubmit={handleSave} className="p-6 space-y-4 overflow-y-auto flex-1 text-sm">
            {/* Display Name */}
            <div className="space-y-1.5">
              <label className="block text-xs font-semibold text-slate-300">
                ชื่อขั้นตอนที่แสดง (Step Display Name) <span className="text-red-400">*</span>
              </label>
              <Input
                type="text"
                value={label}
                onChange={(e) => setLabel(e.target.value)}
                placeholder="เช่น Dump DB รายวัน, SFTP ส่งต่อสำรอง"
                required
                className="bg-slate-950 border-slate-800 text-white focus:border-primary"
              />
            </div>

            {/* NODE TYPE 1: DATABASE BACKUP */}
            {nodeType === 'DATABASE_BACKUP' && (
              <div className="space-y-4 pt-2 border-t border-slate-800/80">
                {/* Vault Credential Selection */}
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300 flex items-center justify-between">
                    <span className="flex items-center gap-1.5">
                      <KeyRound className="w-3.5 h-3.5 text-primary" />
                      เลือก Connection Profile จาก Vault
                    </span>
                    <span className="text-[11px] text-slate-400 font-normal">
                      (แนะนำเพื่อความปลอดภัยสูงสุด)
                    </span>
                  </label>
                  <select
                    value={formFields.credentialId || ''}
                    onChange={(e) => handleDbCredentialSelect(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-lg border border-slate-800 bg-slate-950 text-white focus:outline-none focus:border-primary"
                  >
                    <option value="">-- กำหนดค่าเชื่อมต่อเอง หรือระบุผ่าน Host/Port --</option>
                    {dbCredentials.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name} ({c.credentialType === 'DATABASE_POSTGRESQL' ? 'PostgreSQL' : 'MySQL'}) - {c.host}:{c.port}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {/* Database Name */}
                  <div className="space-y-1.5">
                    <label className="text-xs font-semibold text-slate-300">
                      ชื่อฐานข้อมูล (Database Name) <span className="text-red-400">*</span>
                    </label>
                    <Input
                      type="text"
                      placeholder="เช่น production_db หรือ ${context.db}"
                      value={formFields.databaseName || ''}
                      onChange={(e) => handleFieldChange('databaseName', e.target.value)}
                      required
                      className="bg-slate-950 border-slate-800 text-white font-mono text-xs focus:border-primary"
                    />
                  </div>

                  {/* Compression Format */}
                  <div className="space-y-1.5">
                    <label className="text-xs font-semibold text-slate-300">
                      รูปแบบการบีบอัด (Compression)
                    </label>
                    <select
                      value={formFields.compressionFormat || 'GZIP'}
                      onChange={(e) => handleFieldChange('compressionFormat', e.target.value)}
                      className="w-full px-3 py-2 text-xs rounded-lg border border-slate-800 bg-slate-950 text-white focus:outline-none focus:border-primary"
                    >
                      <option value="GZIP">GZIP (.sql.gz) - สตรีม Zero-RAM</option>
                      <option value="ZSTD">Zstandard (.sql.zst) - ความเร็วสูง</option>
                      <option value="NONE">Plain SQL (.sql) - ไม่บีบอัด</option>
                    </select>
                  </div>
                </div>

                {/* Specific Tables */}
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">
                    เฉพาะตารางที่กำหนด (คั่นด้วยจุลภาค หรือเว้นว่างเพื่อสำรองทั้งฐานข้อมูล)
                  </label>
                  <Input
                    type="text"
                    placeholder="เช่น users, audit_logs, transactions"
                    value={formFields.tables || ''}
                    onChange={(e) => handleFieldChange('tables', e.target.value)}
                    className="bg-slate-950 border-slate-800 text-white font-mono text-xs focus:border-primary"
                  />
                </div>

                {/* Destination Directory with Storage Browser */}
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300 flex items-center justify-between">
                    <span>โฟลเดอร์ปลายทางสำหรับจัดเก็บไฟล์ (Destination Directory)</span>
                    <span className="text-[11px] text-slate-400 font-normal">
                      (ดีฟอลต์: storage/backups)
                    </span>
                  </label>
                  <div className="flex gap-2">
                    <Input
                      type="text"
                      placeholder="เช่น storage/backups"
                      value={formFields.destinationDir || ''}
                      onChange={(e) => handleFieldChange('destinationDir', e.target.value)}
                      className="bg-slate-950 border-slate-800 text-white font-mono text-xs focus:border-primary"
                    />
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() =>
                        openBrowser(
                          'destinationDir',
                          'folder',
                          'เลือกโฟลเดอร์ปลายทางสำหรับจัดเก็บ Database Backup',
                          formFields.destinationDir
                        )
                      }
                      className="gap-1.5 shrink-0 text-xs border-slate-700 bg-slate-800 hover:bg-slate-700 text-slate-200"
                    >
                      <FolderOpen className="w-3.5 h-3.5 text-primary" />
                      <span>เลือกโฟลเดอร์</span>
                    </Button>
                  </div>
                </div>

                {/* Custom File Name */}
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">
                    ชื่อไฟล์แบบกำหนดเอง (Custom File Name - ไม่บังคับ)
                  </label>
                  <Input
                    type="text"
                    placeholder="เช่น custom_db_dump.sql.gz (เว้นว่างเพื่อใช้ชื่ออัตโนมัติ)"
                    value={formFields.customFileName || ''}
                    onChange={(e) => handleFieldChange('customFileName', e.target.value)}
                    className="bg-slate-950 border-slate-800 text-white font-mono text-xs focus:border-primary"
                  />
                </div>

                {/* Direct Connection Toggle (Fallback) */}
                <div className="pt-2">
                  <button
                    type="button"
                    onClick={() => setShowDirectConnection(!showDirectConnection)}
                    className="text-xs text-slate-400 hover:text-slate-200 flex items-center gap-1.5 font-medium"
                  >
                    {showDirectConnection ? (
                      <ChevronUp className="w-3.5 h-3.5" />
                    ) : (
                      <ChevronDown className="w-3.5 h-3.5" />
                    )}
                    {showDirectConnection
                      ? 'ซ่อนการตั้งค่า Direct Host/Port'
                      : 'ระบุการเชื่อมต่อ Host / Port / User แบบกำหนดเอง'}
                  </button>

                  {showDirectConnection && (
                    <div className="mt-2.5 p-3.5 rounded-xl border border-slate-800 bg-slate-950/60 space-y-3">
                      <div className="grid grid-cols-3 gap-3">
                        <div className="col-span-2 space-y-1">
                          <label className="text-[11px] text-slate-400">Host</label>
                          <Input
                            type="text"
                            placeholder="localhost"
                            value={formFields.host || ''}
                            onChange={(e) => handleFieldChange('host', e.target.value)}
                            className="bg-slate-900 border-slate-800 text-xs text-white"
                          />
                        </div>
                        <div className="space-y-1">
                          <label className="text-[11px] text-slate-400">Port</label>
                          <Input
                            type="number"
                            placeholder="5432"
                            value={formFields.port || ''}
                            onChange={(e) => handleFieldChange('port', e.target.value)}
                            className="bg-slate-900 border-slate-800 text-xs text-white"
                          />
                        </div>
                      </div>
                      <div className="grid grid-cols-2 gap-3">
                        <div className="space-y-1">
                          <label className="text-[11px] text-slate-400">Username</label>
                          <Input
                            type="text"
                            placeholder="postgres"
                            value={formFields.username || ''}
                            onChange={(e) => handleFieldChange('username', e.target.value)}
                            className="bg-slate-900 border-slate-800 text-xs text-white"
                          />
                        </div>
                        <div className="space-y-1">
                          <label className="text-[11px] text-slate-400">Password</label>
                          <Input
                            type="password"
                            placeholder="••••••••"
                            value={formFields.password || ''}
                            onChange={(e) => handleFieldChange('password', e.target.value)}
                            className="bg-slate-900 border-slate-800 text-xs text-white"
                          />
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* NODE TYPE 2: FILE BACKUP */}
            {nodeType === 'FILE_BACKUP' && (
              <div className="space-y-4 pt-2 border-t border-slate-800/80">
                {/* Source Path with Storage Browser */}
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">
                    พาธโฟลเดอร์หรือไฟล์ต้นทาง (Source Path) <span className="text-red-400">*</span>
                  </label>
                  <div className="flex gap-2">
                    <Input
                      type="text"
                      placeholder="เช่น storage/data หรือ /var/www/uploads"
                      value={formFields.sourcePath || ''}
                      onChange={(e) => handleFieldChange('sourcePath', e.target.value)}
                      required
                      className="bg-slate-950 border-slate-800 text-white font-mono text-xs focus:border-primary"
                    />
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() =>
                        openBrowser(
                          'sourcePath',
                          'both',
                          'เลือกไฟล์หรือโฟลเดอร์ต้นทาง (Source Path)',
                          formFields.sourcePath
                        )
                      }
                      className="gap-1.5 shrink-0 text-xs border-slate-700 bg-slate-800 hover:bg-slate-700 text-slate-200"
                    >
                      <FolderOpen className="w-3.5 h-3.5 text-primary" />
                      <span>เลือกจาก Server</span>
                    </Button>
                  </div>
                </div>

                {/* Compression Format */}
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">
                    รูปแบบการบีบอัด (Archive Format)
                  </label>
                  <div className="grid grid-cols-3 gap-2">
                    <button
                      type="button"
                      onClick={() => handleFieldChange('compressionFormat', 'TAR_GZ')}
                      className={`py-2 px-2.5 text-xs font-medium rounded-lg border text-center transition-all ${
                        formFields.compressionFormat === 'TAR_GZ'
                          ? 'bg-primary text-primary-foreground border-primary shadow-sm'
                          : 'border-slate-800 bg-slate-950 hover:bg-slate-800 text-slate-300'
                      }`}
                    >
                      .tar.gz (Gzip)
                    </button>
                    <button
                      type="button"
                      onClick={() => handleFieldChange('compressionFormat', 'ZIP')}
                      className={`py-2 px-2.5 text-xs font-medium rounded-lg border text-center transition-all ${
                        formFields.compressionFormat === 'ZIP'
                          ? 'bg-primary text-primary-foreground border-primary shadow-sm'
                          : 'border-slate-800 bg-slate-950 hover:bg-slate-800 text-slate-300'
                      }`}
                    >
                      .zip (Zip)
                    </button>
                    <button
                      type="button"
                      onClick={() => handleFieldChange('compressionFormat', 'TAR_ZST')}
                      className={`py-2 px-2.5 text-xs font-medium rounded-lg border text-center transition-all ${
                        formFields.compressionFormat === 'TAR_ZST'
                          ? 'bg-primary text-primary-foreground border-primary shadow-sm'
                          : 'border-slate-800 bg-slate-950 hover:bg-slate-800 text-slate-300'
                      }`}
                    >
                      .tar.zst (Zstandard)
                    </button>
                  </div>
                </div>

                {/* Exclusion Patterns */}
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">
                    Exclusion Patterns (ระบุ Glob patterns คั่นด้วยจุลภาคเพื่อยกเว้นไฟล์ที่ไม่ต้องการ)
                  </label>
                  <Input
                    type="text"
                    placeholder="node_modules/**, *.log, temp/**, .git/**"
                    value={formFields.exclusionPatterns || ''}
                    onChange={(e) => handleFieldChange('exclusionPatterns', e.target.value)}
                    className="bg-slate-950 border-slate-800 text-white font-mono text-xs focus:border-primary"
                  />
                </div>

                {/* Destination Directory with Storage Browser */}
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300 flex items-center justify-between">
                    <span>โฟลเดอร์ปลายทางสำหรับจัดเก็บไฟล์ (Destination Directory)</span>
                    <span className="text-[11px] text-slate-400 font-normal">
                      (ดีฟอลต์: storage/backups)
                    </span>
                  </label>
                  <div className="flex gap-2">
                    <Input
                      type="text"
                      placeholder="เช่น storage/backups"
                      value={formFields.destinationDir || ''}
                      onChange={(e) => handleFieldChange('destinationDir', e.target.value)}
                      className="bg-slate-950 border-slate-800 text-white font-mono text-xs focus:border-primary"
                    />
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() =>
                        openBrowser(
                          'destinationDir',
                          'folder',
                          'เลือกโฟลเดอร์ปลายทางสำหรับจัดเก็บ Archive',
                          formFields.destinationDir
                        )
                      }
                      className="gap-1.5 shrink-0 text-xs border-slate-700 bg-slate-800 hover:bg-slate-700 text-slate-200"
                    >
                      <FolderOpen className="w-3.5 h-3.5 text-primary" />
                      <span>เลือกโฟลเดอร์</span>
                    </Button>
                  </div>
                </div>

                {/* Custom Archive Filename */}
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">
                    ชื่อไฟล์ Archive กำหนดเอง (Custom Archive Filename - ไม่บังคับ)
                  </label>
                  <Input
                    type="text"
                    placeholder="เช่น web-assets.tar.gz"
                    value={formFields.customFileName || ''}
                    onChange={(e) => handleFieldChange('customFileName', e.target.value)}
                    className="bg-slate-950 border-slate-800 text-white font-mono text-xs focus:border-primary"
                  />
                </div>
              </div>
            )}

            {/* NODE TYPE 3: SPLIT TRANSFER */}
            {nodeType === 'SPLIT_TRANSFER' && (
              <div className="space-y-4 pt-2 border-t border-slate-800/80">
                {/* Source File Path with Quick-insert Dynamic Variable */}
                <div className="space-y-1.5">
                  <div className="flex items-center justify-between">
                    <label className="text-xs font-semibold text-slate-300">
                      ไฟล์ต้นทางที่จะส่ง (Source File Path) <span className="text-red-400">*</span>
                    </label>
                    <button
                      type="button"
                      onClick={() => handleFieldChange('sourceFilePath', '${last_output_path}')}
                      className="inline-flex items-center gap-1 text-[11px] text-primary hover:underline font-mono"
                    >
                      <Sparkles className="w-3 h-3" /> ใช้ {'${last_output_path}'}
                    </button>
                  </div>
                  <div className="flex gap-2">
                    <Input
                      type="text"
                      placeholder="เช่น ${last_output_path} หรือ storage/backups/db.sql.gz"
                      value={formFields.sourceFilePath || ''}
                      onChange={(e) => handleFieldChange('sourceFilePath', e.target.value)}
                      required
                      className="bg-slate-950 border-slate-800 text-white font-mono text-xs focus:border-primary"
                    />
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() =>
                        openBrowser(
                          'sourceFilePath',
                          'file',
                          'เลือกไฟล์ต้นทางที่จะตัดแบ่งส่วนและส่งต่อ (SFTP/FTP)',
                          formFields.sourceFilePath
                        )
                      }
                      className="gap-1.5 shrink-0 text-xs border-slate-700 bg-slate-800 hover:bg-slate-700 text-slate-200"
                    >
                      <FolderOpen className="w-3.5 h-3.5 text-primary" />
                      <span>เลือกไฟล์</span>
                    </Button>
                  </div>
                  <p className="text-[11px] text-slate-400">
                    💡 หากใช้ <code className="text-primary font-mono">${'{last_output_path}'}</code>{' '}
                    ระบบจะดึงไฟล์ที่สำรองได้จากขั้นตอนก่อนหน้ามาส่งต่อโดยอัตโนมัติ
                  </p>
                </div>

                {/* Vault Transfer Credential Selection */}
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300 flex items-center justify-between">
                    <span className="flex items-center gap-1.5">
                      <KeyRound className="w-3.5 h-3.5 text-primary" />
                      เลือกเซิร์ฟเวอร์ปลายทางจาก Vault (SFTP / FTP)
                    </span>
                  </label>
                  <select
                    value={formFields.credentialId || ''}
                    onChange={(e) => handleTransferCredentialSelect(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-lg border border-slate-800 bg-slate-950 text-white focus:outline-none focus:border-primary"
                  >
                    <option value="">-- กรุณาเลือก SFTP/FTP Credential จาก Vault --</option>
                    {transferCredentials.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name} ({c.credentialType}) - {c.host}:{c.port}
                      </option>
                    ))}
                  </select>
                </div>

                {/* Remote Directory */}
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">
                    โฟลเดอร์ปลายทางบนเซิร์ฟเวอร์รีโมต (Remote Destination Directory)
                  </label>
                  <Input
                    type="text"
                    placeholder="/upload หรือ /backups/prod"
                    value={formFields.remoteDirectory || ''}
                    onChange={(e) => handleFieldChange('remoteDirectory', e.target.value)}
                    className="bg-slate-950 border-slate-800 text-white font-mono text-xs focus:border-primary"
                  />
                </div>

                {/* Chunk Size MB and Max Retries */}
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <label className="text-xs font-semibold text-slate-300">
                      ขนาดชิ้นไฟล์ต่อ Chunk (MB)
                    </label>
                    <Input
                      type="number"
                      placeholder="50"
                      min="1"
                      max="1024"
                      value={formFields.chunkSizeMb || '50'}
                      onChange={(e) => handleFieldChange('chunkSizeMb', e.target.value)}
                      className="bg-slate-950 border-slate-800 text-white font-mono text-xs focus:border-primary"
                    />
                    <span className="text-[10px] text-slate-500">
                      (ระบบจะคำนวณ Checksum SHA-256 รายชิ้น)
                    </span>
                  </div>

                  <div className="space-y-1.5">
                    <label className="text-xs font-semibold text-slate-300">
                      จำนวนครั้งที่ลองส่งซ้ำ (Max Retries)
                    </label>
                    <Input
                      type="number"
                      placeholder="3"
                      min="1"
                      max="10"
                      value={formFields.maxRetries || '3'}
                      onChange={(e) => handleFieldChange('maxRetries', e.target.value)}
                      className="bg-slate-950 border-slate-800 text-white font-mono text-xs focus:border-primary"
                    />
                    <span className="text-[10px] text-slate-500">
                      (ส่งเฉพาะ chunk ที่ล้มเหลว)
                    </span>
                  </div>
                </div>
              </div>
            )}

            {/* NODE TYPE 4: EMAIL ALERT */}
            {nodeType === 'EMAIL_ALERT' && (
              <div className="space-y-4 pt-2 border-t border-slate-800/80">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">
                    อีเมลผู้รับการแจ้งเตือน (Recipient Email) <span className="text-red-400">*</span>
                  </label>
                  <Input
                    type="email"
                    placeholder="devops@company.com"
                    value={formFields.recipient || ''}
                    onChange={(e) => handleFieldChange('recipient', e.target.value)}
                    required
                    className="bg-slate-950 border-slate-800 text-white text-xs focus:border-primary"
                  />
                </div>

                <div className="space-y-1.5">
                  <div className="flex items-center justify-between">
                    <label className="text-xs font-semibold text-slate-300">
                      หัวข้ออีเมล (Email Subject) <span className="text-red-400">*</span>
                    </label>
                    <button
                      type="button"
                      onClick={() =>
                        handleFieldChange(
                          'subject',
                          'Custos Pipeline Execution Report: ${last_output_path}'
                        )
                      }
                      className="text-[11px] text-primary hover:underline font-mono"
                    >
                      + ใส่ตัวแปรสรุป
                    </button>
                  </div>
                  <Input
                    type="text"
                    placeholder="เช่น Custos Pipeline Status: Backup Completed"
                    value={formFields.subject || ''}
                    onChange={(e) => handleFieldChange('subject', e.target.value)}
                    required
                    className="bg-slate-950 border-slate-800 text-white text-xs focus:border-primary"
                  />
                  <p className="text-[11px] text-slate-400">
                    ระบบจะส่งรายงานผลการทำงานพร้อมขนาดไฟล์ (MB) และ Checksum ผ่าน **Amazon SES**
                  </p>
                </div>
              </div>
            )}

            {/* Footer Actions */}
            <div className="pt-4 border-t border-slate-800 flex items-center justify-end gap-3">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={onClose}
                className="text-xs text-slate-300 hover:text-white"
              >
                ยกเลิก
              </Button>
              <Button type="submit" size="sm" className="gap-2 text-xs font-semibold">
                <Save className="w-3.5 h-3.5" />
                บันทึกการตั้งค่าโหนด
              </Button>
            </div>
          </form>
        </div>
      </div>

      {/* Storage Browser Dialog */}
      <StorageBrowserDialog
        open={browserOpen}
        onOpenChange={setBrowserOpen}
        onSelect={handleStorageSelect}
        title={browserTitle}
        mode={browserMode}
        initialPath={browserInitialPath}
      />
    </>
  );
};
