import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Search,
  RefreshCw,
  ExternalLink,
  Download,
  Copy,
  Check,
  CheckCircle2,
  AlertTriangle,
  Database,
  Cloud,
} from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '../../../components/ui/Dialog';
import { Button } from '../../../components/ui/Button';
import { Input } from '../../../components/ui/Input';
import { Badge } from '../../../components/ui/Badge';
import { driveApi } from '../../../services/driveApi';
import { FileUploadAuditRecord } from '../../../types/drive';

interface DriveAuditHistoryModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export const DriveAuditHistoryModal: React.FC<DriveAuditHistoryModalProps> = ({
  open,
  onOpenChange,
}) => {
  const [systemSource, setSystemSource] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const { data: logs, isLoading, isFetching, refetch } = useQuery<FileUploadAuditRecord[]>({
    queryKey: ['drive-upload-history', systemSource, statusFilter],
    queryFn: () =>
      driveApi.getDriveUploadHistory({
        systemSource: systemSource || undefined,
        status: statusFilter || undefined,
        limit: 50,
      }),
    enabled: open,
  });

  const handleCopyId = (id: string) => {
    navigator.clipboard.writeText(id);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const formatFileSize = (bytes?: number) => {
    if (!bytes && bytes !== 0) return '-';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  };

  const formatDate = (isoString?: string) => {
    if (!isoString) return '-';
    try {
      const d = new Date(isoString);
      return d.toLocaleString('th-TH', {
        dateStyle: 'short',
        timeStyle: 'medium',
      });
    } catch {
      return isoString;
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl max-h-[85vh] flex flex-col bg-slate-900 border-slate-800 text-slate-100 p-6 overflow-hidden">
        <DialogHeader className="pb-3 border-b border-slate-800 flex flex-row items-center justify-between">
          <div>
            <DialogTitle className="text-lg font-bold flex items-center gap-2 text-white">
              <Cloud className="w-5 h-5 text-blue-400" />
              ประวัติการอัปโหลดไฟล์ (Firebase Firestore Audit Log)
            </DialogTitle>
            <DialogDescription className="text-xs text-slate-400 mt-1">
              บันทึกประวัติการส่งไฟล์ขึ้น Google Drive จาก Collection <code className="text-blue-300">file_upload_history</code> บน Firebase
            </DialogDescription>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            disabled={isFetching}
            className="gap-2 border-slate-700 bg-slate-800/60 hover:bg-slate-800 text-slate-300 hover:text-white"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isFetching ? 'animate-spin' : ''}`} />
            <span>รีเฟรช</span>
          </Button>
        </DialogHeader>

        {/* Filters */}
        <div className="flex flex-wrap items-center gap-3 py-3 border-b border-slate-800/60">
          <div className="relative flex-1 min-w-[200px]">
            <Search className="w-4 h-4 absolute left-3 top-2.5 text-slate-500" />
            <Input
              value={systemSource}
              onChange={(e) => setSystemSource(e.target.value)}
              placeholder="ค้นหาตามชื่อระบบ (systemSource)..."
              className="pl-9 bg-slate-950 border-slate-800 text-white placeholder:text-slate-600 text-sm h-9"
            />
          </div>

          <div className="flex items-center gap-2">
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="h-9 px-3 rounded-md bg-slate-950 border border-slate-800 text-slate-300 text-sm focus:outline-none focus:ring-1 focus:ring-primary"
            >
              <option value="">ทุกสถานะ (All Status)</option>
              <option value="SUCCESS">SUCCESS</option>
              <option value="FAILED">FAILED</option>
            </select>
          </div>
        </div>

        {/* History Table Container */}
        <div className="flex-1 overflow-y-auto mt-2 pr-1 space-y-2">
          {isLoading ? (
            <div className="py-16 text-center text-slate-400 flex flex-col items-center gap-3">
              <RefreshCw className="w-6 h-6 animate-spin text-primary" />
              <p className="text-sm">กำลังโหลดประวัติจาก Firebase Firestore...</p>
            </div>
          ) : !logs || logs.length === 0 ? (
            <div className="py-16 text-center text-slate-500 flex flex-col items-center gap-2">
              <Database className="w-8 h-8 opacity-40" />
              <p className="text-sm font-medium">ไม่พบประวัติการอัปโหลดไฟล์ใน Firestore</p>
              <p className="text-xs text-slate-600">เมื่อมีการอัปโหลดไฟล์ขึ้น Google Drive ประวัติจะถูกบันทึกที่นี่โดยอัตโนมัติ</p>
            </div>
          ) : (
            <div className="border border-slate-800 rounded-lg overflow-hidden">
              <table className="w-full text-left text-xs text-slate-300">
                <thead className="bg-slate-950/80 text-slate-400 uppercase tracking-wider border-b border-slate-800 font-semibold">
                  <tr>
                    <th className="px-3 py-2.5">วันเวลา</th>
                    <th className="px-3 py-2.5">ระบบต้นทาง</th>
                    <th className="px-3 py-2.5">ชื่อไฟล์</th>
                    <th className="px-3 py-2.5">ขนาด</th>
                    <th className="px-3 py-2.5">ผู้อัปโหลด</th>
                    <th className="px-3 py-2.5">สถานะ</th>
                    <th className="px-3 py-2.5 text-right">ลิงก์ & การจัดการ</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 bg-slate-900/40">
                  {logs.map((log) => (
                    <tr key={log.id} className="hover:bg-slate-800/30 transition-colors">
                      <td className="px-3 py-2.5 whitespace-nowrap text-slate-400 font-mono">
                        {formatDate(log.createdAt)}
                      </td>
                      <td className="px-3 py-2.5 whitespace-nowrap">
                        <Badge variant="outline" className="bg-blue-950/40 text-blue-300 border-blue-800/60 font-mono text-[11px]">
                          {log.systemSource}
                        </Badge>
                      </td>
                      <td className="px-3 py-2.5">
                        <div className="max-w-[220px] truncate font-medium text-white" title={log.fileName}>
                          {log.fileName}
                        </div>
                        {log.driveFileId && (
                          <div className="text-[10px] text-slate-500 font-mono truncate max-w-[180px]">
                            ID: {log.driveFileId}
                          </div>
                        )}
                      </td>
                      <td className="px-3 py-2.5 whitespace-nowrap text-slate-400 font-mono">
                        {formatFileSize(log.fileSize)}
                      </td>
                      <td className="px-3 py-2.5 whitespace-nowrap text-slate-400">
                        {log.uploadedBy || '-'}
                      </td>
                      <td className="px-3 py-2.5 whitespace-nowrap">
                        {log.status === 'SUCCESS' ? (
                          <Badge variant="success" className="gap-1 text-[10px]">
                            <CheckCircle2 className="w-3 h-3" />
                            SUCCESS
                          </Badge>
                        ) : (
                          <Badge variant="destructive" className="gap-1 text-[10px]">
                            <AlertTriangle className="w-3 h-3" />
                            FAILED
                          </Badge>
                        )}
                      </td>
                      <td className="px-3 py-2.5 whitespace-nowrap text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          {log.driveFileId && (
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => handleCopyId(log.driveFileId)}
                              title="คัดลอก Drive File ID"
                              className="h-7 w-7 p-0 text-slate-400 hover:text-white"
                            >
                              {copiedId === log.driveFileId ? (
                                <Check className="w-3.5 h-3.5 text-emerald-400" />
                              ) : (
                                <Copy className="w-3.5 h-3.5" />
                              )}
                            </Button>
                          )}
                          {log.webViewLink && (
                            <a
                              href={log.webViewLink}
                              target="_blank"
                              rel="noreferrer"
                              className="inline-flex items-center justify-center h-7 px-2 text-xs rounded bg-blue-950/60 hover:bg-blue-900 border border-blue-800/80 text-blue-300 transition-colors gap-1"
                              title="เปิดดูไฟล์บน Google Drive"
                            >
                              <ExternalLink className="w-3 h-3" />
                              <span>View</span>
                            </a>
                          )}
                          {log.webContentLink && (
                            <a
                              href={log.webContentLink}
                              target="_blank"
                              rel="noreferrer"
                              className="inline-flex items-center justify-center h-7 px-2 text-xs rounded bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors gap-1"
                              title="ดาวน์โหลดไฟล์ตรง"
                            >
                              <Download className="w-3 h-3" />
                              <span>Download</span>
                            </a>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
};
