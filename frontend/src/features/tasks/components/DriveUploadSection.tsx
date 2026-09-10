import React, { useState, useRef } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  UploadCloud,
  FileUp,
  FileCheck2,
  AlertTriangle,
  CheckCircle2,
  ExternalLink,
  Download,
  Copy,
  Check,
  History,
  FolderTree,
  Loader2,
  X,
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '../../../components/ui/Card';
import { Button } from '../../../components/ui/Button';
import { Input } from '../../../components/ui/Input';
import { Badge } from '../../../components/ui/Badge';
import { driveApi } from '../../../services/driveApi';
import { DriveUploadResponse } from '../../../types/drive';
import { DriveAuditHistoryModal } from './DriveAuditHistoryModal';

const PRESET_SYSTEM_SOURCES = [
  'INVENTORY_MANAGEMENT',
  'CUSTOMER_PORTAL',
  'BILLING_SERVICE',
  'CUSTOS_BACKUP',
  'HR_PAYROLL',
];

export const DriveUploadSection: React.FC = () => {
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [systemSource, setSystemSource] = useState<string>('INVENTORY_MANAGEMENT');
  const [customSystemSource, setCustomSystemSource] = useState<string>('');
  const [folderId, setFolderId] = useState<string>('');
  const [uploadProgress, setUploadProgress] = useState<number>(0);
  const [uploadResult, setUploadResult] = useState<DriveUploadResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<boolean>(false);
  const [isHistoryOpen, setIsHistoryOpen] = useState<boolean>(false);
  const [isDragOver, setIsDragOver] = useState<boolean>(false);

  const uploadMutation = useMutation({
    mutationFn: async () => {
      if (!selectedFile) throw new Error('กรุณาเลือกไฟล์ที่ต้องการอัปโหลด');
      const resolvedSource = systemSource === 'CUSTOM' ? customSystemSource.trim() : systemSource;
      if (!resolvedSource) throw new Error('กรุณาระบุชื่อระบบต้นทาง (systemSource)');

      setUploadProgress(0);
      return await driveApi.uploadToDrive(
        selectedFile,
        resolvedSource,
        folderId.trim() || undefined,
        (percent) => setUploadProgress(percent)
      );
    },
    onSuccess: (data) => {
      setUploadResult(data);
      setErrorMessage(null);
      queryClient.invalidateQueries({ queryKey: ['drive-upload-history'] });
    },
    onError: (err: any) => {
      const msg = err.response?.data?.message || err.message || 'เกิดข้อผิดพลาดในการอัปโหลด';
      setErrorMessage(msg);
    },
  });

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      setSelectedFile(e.target.files[0]);
      setUploadResult(null);
      setErrorMessage(null);
    }
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragOver(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      setSelectedFile(e.dataTransfer.files[0]);
      setUploadResult(null);
      setErrorMessage(null);
    }
  };

  const handleCopyId = (id: string) => {
    navigator.clipboard.writeText(id);
    setCopiedId(true);
    setTimeout(() => setCopiedId(false), 2000);
  };

  const resetForm = () => {
    setSelectedFile(null);
    setUploadResult(null);
    setErrorMessage(null);
    setUploadProgress(0);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
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
      <Card className="border-slate-800 bg-slate-900 shadow-sm">
        <CardHeader className="flex flex-col sm:flex-row items-start sm:items-center justify-between pb-4 border-b border-slate-800 gap-3">
          <div>
            <CardTitle className="text-lg font-bold text-white flex items-center gap-2">
              <UploadCloud className="w-5 h-5 text-primary" />
              Google Drive Stream Uploader
            </CardTitle>
            <p className="text-xs text-slate-400 mt-1">
              สตรีมไฟล์ขึ้น Google Drive (v3) และบันทึกประวัติการส่งไฟล์ลง Firebase Cloud Firestore แบบ Zero-RAM Buffer
            </p>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setIsHistoryOpen(true)}
            className="gap-2 border-slate-700 bg-slate-800/80 hover:bg-slate-700 text-slate-200"
          >
            <History className="w-4 h-4 text-blue-400" />
            <span>ดูประวัติ Audit Log ใน Firestore</span>
          </Button>
        </CardHeader>

        <CardContent className="pt-6 space-y-6">
          {/* Drag & Drop File Zone */}
          {!selectedFile ? (
            <div
              onDragOver={(e) => {
                e.preventDefault();
                setIsDragOver(true);
              }}
              onDragLeave={() => setIsDragOver(false)}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
              className={`border-2 border-dashed rounded-xl p-8 text-center cursor-pointer transition-colors ${
                isDragOver
                  ? 'border-primary bg-primary/10'
                  : 'border-slate-700 hover:border-slate-600 bg-slate-950/50 hover:bg-slate-950'
              }`}
            >
              <input
                ref={fileInputRef}
                type="file"
                className="hidden"
                onChange={handleFileChange}
              />
              <div className="flex flex-col items-center justify-center gap-3">
                <div className="w-12 h-12 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-primary">
                  <FileUp className="w-6 h-6" />
                </div>
                <div>
                  <p className="text-sm font-semibold text-slate-200">
                    คลิกเพื่อเลือกไฟล์ หรือลากไฟล์มาวางที่นี่
                  </p>
                  <p className="text-xs text-slate-500 mt-1">
                    รองรับเอกสาร PDF, รูปภาพ, Spreadsheets, หรือไฟล์ Archive ทุกประเภท
                  </p>
                </div>
              </div>
            </div>
          ) : (
            <div className="border border-slate-800 rounded-lg p-4 bg-slate-950/60 flex items-center justify-between">
              <div className="flex items-center gap-3 min-w-0">
                <div className="w-10 h-10 rounded-lg bg-primary/10 border border-primary/20 flex items-center justify-center text-primary flex-shrink-0">
                  <FileCheck2 className="w-5 h-5" />
                </div>
                <div className="min-w-0">
                  <p className="text-sm font-medium text-white truncate max-w-md" title={selectedFile.name}>
                    {selectedFile.name}
                  </p>
                  <p className="text-xs text-slate-400">
                    {formatBytes(selectedFile.size)} • {selectedFile.type || 'unknown type'}
                  </p>
                </div>
              </div>
              <Button
                variant="ghost"
                size="sm"
                onClick={resetForm}
                disabled={uploadMutation.isPending}
                className="text-slate-400 hover:text-white"
              >
                <X className="w-4 h-4" />
              </Button>
            </div>
          )}

          {/* Form Settings */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* systemSource */}
            <div className="space-y-1.5">
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                ระบบต้นทาง (systemSource) <span className="text-red-400">*</span>
              </label>
              <select
                value={systemSource}
                onChange={(e) => setSystemSource(e.target.value)}
                disabled={uploadMutation.isPending}
                className="w-full h-10 px-3 rounded-md bg-slate-950 border border-slate-800 text-slate-200 text-sm focus:outline-none focus:ring-1 focus:ring-primary"
              >
                {PRESET_SYSTEM_SOURCES.map((source) => (
                  <option key={source} value={source}>
                    {source}
                  </option>
                ))}
                <option value="CUSTOM">+ กำหนดชื่อระบบเอง (Custom)</option>
              </select>

              {systemSource === 'CUSTOM' && (
                <Input
                  value={customSystemSource}
                  onChange={(e) => setCustomSystemSource(e.target.value)}
                  placeholder="ระบุชื่อระบบ เช่น HR_PAYROLL..."
                  disabled={uploadMutation.isPending}
                  className="mt-2 bg-slate-950 border-slate-800 text-white placeholder:text-slate-600 text-sm"
                />
              )}
            </div>

            {/* folderId */}
            <div className="space-y-1.5">
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center justify-between">
                <span>โฟลเดอร์ปลายทางบน Drive (Folder ID)</span>
                <span className="text-[10px] text-slate-500 font-normal">เว้นว่างไว้หากใช้ Default Folder</span>
              </label>
              <div className="relative">
                <FolderTree className="w-4 h-4 absolute left-3 top-3 text-slate-500" />
                <Input
                  value={folderId}
                  onChange={(e) => setFolderId(e.target.value)}
                  placeholder="1xyz_TargetGoogleDriveFolderId"
                  disabled={uploadMutation.isPending}
                  className="pl-9 bg-slate-950 border-slate-800 text-white placeholder:text-slate-600 font-mono text-sm"
                />
              </div>
            </div>
          </div>

          {/* Upload Progress */}
          {uploadMutation.isPending && (
            <div className="space-y-2 bg-slate-950/70 p-4 rounded-lg border border-slate-800">
              <div className="flex items-center justify-between text-xs text-slate-300">
                <span className="flex items-center gap-2">
                  <Loader2 className="w-3.5 h-3.5 animate-spin text-primary" />
                  กำลังสตรีมข้อมูลไปยัง Google Drive และบันทึก Firestore...
                </span>
                <span className="font-mono font-semibold text-primary">{uploadProgress}%</span>
              </div>
              <div className="w-full h-2 bg-slate-800 rounded-full overflow-hidden">
                <div
                  className="h-full bg-primary transition-all duration-300 rounded-full"
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
            </div>
          )}

          {/* Error Message */}
          {errorMessage && (
            <div className="p-4 rounded-lg bg-red-950/40 border border-red-800/80 text-red-200 flex items-start gap-3">
              <AlertTriangle className="w-5 h-5 text-red-400 flex-shrink-0 mt-0.5" />
              <div>
                <p className="text-sm font-semibold">อัปโหลดไฟล์ล้มเหลว</p>
                <p className="text-xs text-red-300 mt-0.5">{errorMessage}</p>
              </div>
            </div>
          )}

          {/* Success Result Card */}
          {uploadResult && (
            <div className="p-4 rounded-lg bg-emerald-950/30 border border-emerald-800/80 text-emerald-100 space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="w-5 h-5 text-emerald-400" />
                  <span className="text-sm font-bold text-emerald-300">อัปโหลดและบันทึก Audit Log สำเร็จ</span>
                </div>
                <Badge variant="success" className="text-xs">201 CREATED</Badge>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs text-slate-300 bg-slate-950/60 p-3 rounded border border-slate-800/80 font-mono">
                <div>
                  <span className="text-slate-500">File Name: </span>
                  <span className="text-white">{uploadResult.fileName}</span>
                </div>
                <div>
                  <span className="text-slate-500">System Source: </span>
                  <span className="text-blue-400">{uploadResult.systemSource}</span>
                </div>
                <div>
                  <span className="text-slate-500">File Size: </span>
                  <span>{formatBytes(uploadResult.fileSize)}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-slate-500">Drive File ID: </span>
                  <span className="truncate max-w-[130px] text-amber-300">{uploadResult.driveFileId}</span>
                  <button
                    onClick={() => handleCopyId(uploadResult.driveFileId)}
                    className="text-slate-400 hover:text-white"
                    title="คัดลอก File ID"
                  >
                    {copiedId ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                  </button>
                </div>
              </div>

              <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
                <div className="flex items-center gap-2">
                  {uploadResult.webViewLink && (
                    <a
                      href={uploadResult.webViewLink}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-blue-600 hover:bg-blue-500 text-white text-xs font-medium transition-colors shadow-sm"
                    >
                      <ExternalLink className="w-3.5 h-3.5" />
                      <span>เปิดดูใน Google Drive</span>
                    </a>
                  )}
                  {uploadResult.webContentLink && (
                    <a
                      href={uploadResult.webContentLink}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium transition-colors border border-slate-700"
                    >
                      <Download className="w-3.5 h-3.5" />
                      <span>ดาวน์โหลดไฟล์</span>
                    </a>
                  )}
                </div>

                <Button
                  variant="outline"
                  size="sm"
                  onClick={resetForm}
                  className="border-emerald-800/80 text-emerald-300 hover:bg-emerald-950/60 text-xs"
                >
                  อัปโหลดไฟล์ถัดไป
                </Button>
              </div>
            </div>
          )}

          {/* Action Trigger */}
          <div className="pt-2 flex justify-end">
            <Button
              onClick={() => uploadMutation.mutate()}
              disabled={!selectedFile || uploadMutation.isPending}
              className="gap-2 px-6"
            >
              {uploadMutation.isPending ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>กำลังอัปโหลด...</span>
                </>
              ) : (
                <>
                  <UploadCloud className="w-4 h-4" />
                  <span>เริ่มอัปโหลดขึ้น Google Drive</span>
                </>
              )}
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Audit History Modal */}
      <DriveAuditHistoryModal
        open={isHistoryOpen}
        onOpenChange={setIsHistoryOpen}
      />
    </div>
  );
};
