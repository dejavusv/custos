import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { FileText, Search, RefreshCw } from 'lucide-react';
import { api } from '../../services/api';
import { AuditLog } from '../../types/user';
import { PageableResponse, ApiResponse } from '../../types/api';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Badge } from '../../components/ui/Badge';
import { Card, CardContent } from '../../components/ui/Card';

export const AuditLogsPage: React.FC = () => {
  const [username, setUsername] = useState('');
  const [action, setAction] = useState('');
  const [page, setPage] = useState(0);

  const { data, isLoading, isFetching, refetch } = useQuery<PageableResponse<AuditLog>>({
    queryKey: ['audit-logs', username, action, page],
    queryFn: async () => {
      const params: any = { page, size: 20 };
      if (username) params.username = username;
      if (action) params.action = action;
      const res = await api.get<ApiResponse<PageableResponse<AuditLog>>>('/audit-logs', { params });
      return res.data.data!;
    },
  });

  const renderActionBadge = (act: string) => {
    if (act.includes('SUCCESS') || act.includes('CREATED')) {
      return <Badge variant="success">{act}</Badge>;
    }
    if (act.includes('FAILED') || act.includes('LOCKED') || act.includes('BLOCKED')) {
      return <Badge variant="destructive">{act}</Badge>;
    }
    if (act.includes('UPDATED') || act.includes('RESET')) {
      return <Badge variant="warning">{act}</Badge>;
    }
    return <Badge variant="outline">{act}</Badge>;
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <FileText className="w-6 h-6 text-primary" />
            บันทึกกิจกรรมระบบ (Audit Logs)
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            ประวัติการเข้าสู่ระบบ การจัดการผู้ใช้ และการกระทำความปลอดภัยทั้งหมด
          </p>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={() => refetch()}
          disabled={isFetching}
          className="gap-2 border-slate-800 text-slate-400 hover:text-white"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${isFetching ? 'animate-spin' : ''}`} />
          <span>รีเฟรช</span>
        </Button>
      </div>

      {/* Filter */}
      <Card className="border-slate-800 bg-slate-900/60 shadow-sm">
        <CardContent className="p-4 flex flex-col sm:flex-row items-center gap-3">
          <div className="relative flex-1 max-w-sm">
            <Search className="w-4 h-4 absolute left-3 top-2.5 text-slate-500" />
            <Input
              value={username}
              onChange={(e) => {
                setUsername(e.target.value);
                setPage(0);
              }}
              placeholder="ค้นหาตาม Username..."
              className="pl-9 bg-slate-950 border-slate-800 text-white placeholder:text-slate-600"
            />
          </div>

          <div className="relative flex-1 max-w-sm">
            <Input
              value={action}
              onChange={(e) => {
                setAction(e.target.value);
                setPage(0);
              }}
              placeholder="กรองตาม Action (เช่น LOGIN, USER_CREATED)..."
              className="bg-slate-950 border-slate-800 text-white placeholder:text-slate-600"
            />
          </div>
        </CardContent>
      </Card>

      {/* Logs Table */}
      <Card className="border-slate-800 bg-slate-900/90 shadow-xl overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-sm">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-950/80 text-xs text-slate-400 uppercase tracking-wider">
                <th className="py-3.5 px-4 font-semibold">เวลา (Timestamp)</th>
                <th className="py-3.5 px-4 font-semibold">ผู้ใช้งาน (User)</th>
                <th className="py-3.5 px-4 font-semibold">กิจกรรม (Action)</th>
                <th className="py-3.5 px-4 font-semibold">IP Address</th>
                <th className="py-3.5 px-4 font-semibold">รายละเอียด (Details)</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 font-mono text-xs">
              {isLoading ? (
                <tr>
                  <td colSpan={5} className="py-8 text-center text-slate-500 font-sans">
                    <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2 text-primary" />
                    กำลังโหลดประวัติกิจกรรม...
                  </td>
                </tr>
              ) : !data?.content || data.content.length === 0 ? (
                <tr>
                  <td colSpan={5} className="py-8 text-center text-slate-500 font-sans">
                    ยังไม่มีข้อมูลประวัติกิจกรรมตามเงื่อนไข
                  </td>
                </tr>
              ) : (
                data.content.map((log) => (
                  <tr key={log.id} className="hover:bg-slate-800/40 transition-colors">
                    <td className="py-3 px-4 text-slate-400">
                      {new Date(log.createdAt).toLocaleString('th-TH')}
                    </td>
                    <td className="py-3 px-4 text-white font-semibold font-sans">
                      {log.username || 'ANONYMOUS'}
                    </td>
                    <td className="py-3 px-4 font-sans">
                      {renderActionBadge(log.action)}
                    </td>
                    <td className="py-3 px-4 text-slate-400">
                      {log.ipAddress || '-'}
                    </td>
                    <td className="py-3 px-4 text-slate-300 font-sans">
                      {log.details || '-'}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};
