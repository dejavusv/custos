import React from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Workflow, History, FileText, RefreshCw } from 'lucide-react';
import { useAuthStore } from '../../stores/authStore';
import { api } from '../../services/api';
import { dashboardApi } from '../../services/dashboardApi';
import { ApiResponse, PageableResponse } from '../../types/api';
import { AuditLog } from '../../types/user';
import { DashboardSummary } from '../../types/dashboard';
import { formatDateTime, formatDuration } from '../../lib/format';
import { ExecutionStatusBadge } from '../../components/ExecutionStatusBadge';
import { Card, CardHeader, CardTitle, CardDescription } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';

const REFRESH_INTERVAL_MS = 15000;
const AUDIT_LIMIT = 10;

const renderActionBadge = (act: string) => {
  if (act.includes('SUCCESS') || act.includes('CREATED') || act.includes('CREATE')) {
    return <Badge variant="success">{act}</Badge>;
  }
  if (act.includes('FAILED') || act.includes('LOCKED') || act.includes('BLOCKED') || act.includes('DELETE')) {
    return <Badge variant="destructive">{act}</Badge>;
  }
  if (act.includes('UPDATED') || act.includes('UPDATE') || act.includes('RESET')) {
    return <Badge variant="warning">{act}</Badge>;
  }
  return <Badge variant="outline">{act}</Badge>;
};

const TableLoading: React.FC<{ colSpan: number; text: string }> = ({ colSpan, text }) => (
  <tr>
    <td colSpan={colSpan} className="py-8 text-center text-slate-500">
      <RefreshCw className="w-5 h-5 animate-spin mx-auto mb-2 text-primary" />
      {text}
    </td>
  </tr>
);

const TableEmpty: React.FC<{ colSpan: number; text: string }> = ({ colSpan, text }) => (
  <tr>
    <td colSpan={colSpan} className="py-8 text-center text-slate-500">
      {text}
    </td>
  </tr>
);

export const DashboardPage: React.FC = () => {
  const { user } = useAuthStore();
  const isAdmin = !!user?.roles?.some((r) => r === 'ROLE_SUPER_ADMIN' || r === 'ROLE_ADMIN');

  const summaryQuery = useQuery<DashboardSummary>({
    queryKey: ['dashboard-summary'],
    queryFn: dashboardApi.getSummary,
    refetchInterval: REFRESH_INTERVAL_MS,
  });

  // Audit Logs เป็น API เฉพาะ ADMIN / SUPER_ADMIN
  const auditQuery = useQuery<PageableResponse<AuditLog>>({
    queryKey: ['dashboard-audit-logs'],
    enabled: isAdmin,
    refetchInterval: REFRESH_INTERVAL_MS,
    queryFn: async () => {
      const res = await api.get<ApiResponse<PageableResponse<AuditLog>>>('/audit-logs', {
        params: { page: 0, size: AUDIT_LIMIT },
      });
      return res.data.data!;
    },
  });

  const summary = summaryQuery.data;
  const isFetching = summaryQuery.isFetching || auditQuery.isFetching;

  const handleRefresh = () => {
    summaryQuery.refetch();
    if (isAdmin) auditQuery.refetch();
  };

  return (
    <div className="space-y-6">
      {/* Welcome Banner */}
      <div className="p-6 rounded-2xl bg-gradient-to-r from-slate-900 via-slate-900 to-slate-800 border border-slate-800 shadow-lg flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <span className="text-xs text-slate-500">v1.0.0-SNAPSHOT</span>
          <h1 className="text-xl sm:text-2xl font-bold text-white tracking-tight mt-1">
            ยินดีต้อนรับสู่ Custos Platform, {user?.username}
          </h1>
          <p className="text-sm text-slate-400 mt-1 max-w-2xl">
            ระบบ Server Automation & Task Scheduling Platform สำหรับงานสำรองข้อมูลและร้อยเรียง Pipeline
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            size="sm"
            onClick={handleRefresh}
            disabled={isFetching}
            className="gap-2 border-slate-700 text-slate-300 hover:text-white"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isFetching ? 'animate-spin' : ''}`} />
            <span>รีเฟรช</span>
          </Button>
          {isAdmin && (
            <Link to="/users">
              <Button variant="outline" className="border-slate-700 text-slate-300 hover:text-white">
                จัดการผู้ใช้งาน
              </Button>
            </Link>
          )}
        </div>
      </div>

      {/* Pipelines Overview */}
      <Card className="border-slate-800 bg-slate-900/80 shadow-md overflow-hidden">
        <CardHeader className="flex flex-col sm:flex-row items-start justify-between gap-4">
          <div>
            <CardTitle className="text-lg text-white flex items-center gap-2">
              <Workflow className="w-5 h-5 text-primary" />
              Pipelines ในระบบ
            </CardTitle>
            <CardDescription className="text-slate-400">
              วันเวลาที่ทำงานล่าสุดและผลการรันของแต่ละ Pipeline
            </CardDescription>
          </div>
          <div className="flex items-center gap-6 text-right shrink-0">
            <div>
              <p className="text-xs uppercase tracking-wider text-slate-400">ทั้งหมด</p>
              <p className="text-2xl font-bold text-white">{summary?.totalPipelines ?? '-'}</p>
            </div>
            <div>
              <p className="text-xs uppercase tracking-wider text-slate-400">เปิดใช้งาน</p>
              <p className="text-2xl font-bold text-emerald-400">{summary?.activePipelines ?? '-'}</p>
            </div>
          </div>
        </CardHeader>
        <div className="md:overflow-x-auto">
          <table className="responsive-table w-full text-left border-collapse text-sm">
            <thead>
              <tr className="border-y border-slate-800 bg-slate-950/80 text-xs text-slate-400 uppercase tracking-wider">
                <th className="py-3 px-4 font-semibold">Pipeline</th>
                <th className="py-3 px-4 font-semibold">สถานะ</th>
                <th className="py-3 px-4 font-semibold">ทำงานล่าสุด</th>
                <th className="py-3 px-4 font-semibold">ผลการรัน</th>
                <th className="py-3 px-4 font-semibold">ระยะเวลา</th>
                <th className="py-3 px-4 font-semibold">รอบถัดไป</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {summaryQuery.isLoading ? (
                <TableLoading colSpan={6} text="กำลังโหลดข้อมูล Pipelines..." />
              ) : summaryQuery.isError ? (
                <TableEmpty colSpan={6} text="ไม่สามารถโหลดข้อมูลได้" />
              ) : !summary || summary.pipelines.length === 0 ? (
                <TableEmpty colSpan={6} text="ยังไม่มี Pipeline ในระบบ" />
              ) : (
                summary.pipelines.map((p) => (
                  <tr key={p.id} className="hover:bg-slate-800/40 transition-colors">
                    <td data-label="Pipeline" className="py-3 px-4">
                      <div>
                        <p className="text-white font-semibold">{p.name}</p>
                        <p className="text-xs text-slate-500 font-mono">{p.cronExpression || 'Manual'}</p>
                      </div>
                    </td>
                    <td data-label="สถานะ" className="py-3 px-4">
                      {p.active ? (
                        <Badge variant="success">Active</Badge>
                      ) : (
                        <Badge variant="secondary">Paused</Badge>
                      )}
                    </td>
                    <td data-label="ทำงานล่าสุด" className="py-3 px-4 text-slate-300">
                      {p.lastRunAt ? formatDateTime(p.lastRunAt) : <span className="text-slate-500">ยังไม่เคยรัน</span>}
                    </td>
                    <td data-label="ผลการรัน" className="py-3 px-4">
                      {p.lastStatus ? <ExecutionStatusBadge status={p.lastStatus} /> : <span className="text-slate-600">-</span>}
                    </td>
                    <td data-label="ระยะเวลา" className="py-3 px-4 text-slate-400 font-mono text-xs">{formatDuration(p.lastDurationMs)}</td>
                    <td data-label="รอบถัดไป" className="py-3 px-4 text-slate-400">{formatDateTime(p.nextFireTime)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>

      {/* Execution History */}
      <Card className="border-slate-800 bg-slate-900/80 shadow-md overflow-hidden">
        <CardHeader className="flex flex-col sm:flex-row items-start justify-between gap-4">
          <div>
            <CardTitle className="text-lg text-white flex items-center gap-2">
              <History className="w-5 h-5 text-primary" />
              Execution History ล่าสุด
            </CardTitle>
            <CardDescription className="text-slate-400">การรัน Pipeline ล่าสุด {AUDIT_LIMIT} รายการ</CardDescription>
          </div>
          <Link to="/console" className="text-xs text-primary hover:underline shrink-0">
            ดูทั้งหมด
          </Link>
        </CardHeader>
        <div className="md:overflow-x-auto">
          <table className="responsive-table w-full text-left border-collapse text-sm">
            <thead>
              <tr className="border-y border-slate-800 bg-slate-950/80 text-xs text-slate-400 uppercase tracking-wider">
                <th className="py-3 px-4 font-semibold">Pipeline</th>
                <th className="py-3 px-4 font-semibold">สถานะ</th>
                <th className="py-3 px-4 font-semibold">Triggered By</th>
                <th className="py-3 px-4 font-semibold">เวลาเริ่ม</th>
                <th className="py-3 px-4 font-semibold">ระยะเวลา</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {summaryQuery.isLoading ? (
                <TableLoading colSpan={5} text="กำลังโหลดประวัติการรัน..." />
              ) : summaryQuery.isError ? (
                <TableEmpty colSpan={5} text="ไม่สามารถโหลดข้อมูลได้" />
              ) : !summary || summary.recentExecutions.length === 0 ? (
                <TableEmpty colSpan={5} text="ยังไม่มีประวัติการรัน Pipeline" />
              ) : (
                summary.recentExecutions.map((exec) => (
                  <tr key={exec.id} className="hover:bg-slate-800/40 transition-colors">
                    <td data-label="Pipeline" className="py-3 px-4 text-white font-semibold">{exec.pipelineName}</td>
                    <td data-label="สถานะ" className="py-3 px-4">
                      <ExecutionStatusBadge status={exec.status} />
                    </td>
                    <td data-label="Triggered By" className="py-3 px-4 text-slate-400">
                      {exec.triggeredBy}
                      <span className="ml-2 text-[10px] text-slate-600 uppercase">{exec.triggerType}</span>
                    </td>
                    <td data-label="เวลาเริ่ม" className="py-3 px-4 text-slate-300">{formatDateTime(exec.startTime)}</td>
                    <td data-label="ระยะเวลา" className="py-3 px-4 text-slate-400 font-mono text-xs">{formatDuration(exec.durationMs)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>

      {/* Audit Logs (ADMIN only) */}
      {isAdmin && (
        <Card className="border-slate-800 bg-slate-900/80 shadow-md overflow-hidden">
          <CardHeader className="flex flex-col sm:flex-row items-start justify-between gap-4">
            <div>
              <CardTitle className="text-lg text-white flex items-center gap-2">
                <FileText className="w-5 h-5 text-primary" />
                Audit ล่าสุด
              </CardTitle>
              <CardDescription className="text-slate-400">
                บันทึกกิจกรรมระบบล่าสุด {AUDIT_LIMIT} รายการ
              </CardDescription>
            </div>
            <Link to="/audit-logs" className="text-xs text-primary hover:underline shrink-0">
              ดูทั้งหมด
            </Link>
          </CardHeader>
          <div className="md:overflow-x-auto">
            <table className="responsive-table w-full text-left border-collapse text-sm">
              <thead>
                <tr className="border-y border-slate-800 bg-slate-950/80 text-xs text-slate-400 uppercase tracking-wider">
                  <th className="py-3 px-4 font-semibold">เวลา</th>
                  <th className="py-3 px-4 font-semibold">ผู้ใช้งาน</th>
                  <th className="py-3 px-4 font-semibold">กิจกรรม</th>
                  <th className="py-3 px-4 font-semibold">Resource</th>
                  <th className="py-3 px-4 font-semibold">IP Address</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {auditQuery.isLoading ? (
                  <TableLoading colSpan={5} text="กำลังโหลดประวัติกิจกรรม..." />
                ) : auditQuery.isError ? (
                  <TableEmpty colSpan={5} text="ไม่สามารถโหลดข้อมูลได้" />
                ) : !auditQuery.data || auditQuery.data.content.length === 0 ? (
                  <TableEmpty colSpan={5} text="ยังไม่มีข้อมูลประวัติกิจกรรม" />
                ) : (
                  auditQuery.data.content.map((log) => (
                    <tr key={log.id} className="hover:bg-slate-800/40 transition-colors">
                      <td data-label="เวลา" className="py-3 px-4 text-slate-400">{formatDateTime(log.createdAt)}</td>
                      <td data-label="ผู้ใช้งาน" className="py-3 px-4 text-white font-semibold">{log.username || 'ANONYMOUS'}</td>
                      <td data-label="กิจกรรม" className="py-3 px-4">{renderActionBadge(log.action)}</td>
                      <td data-label="Resource" className="py-3 px-4 text-slate-300">{log.targetResource || '-'}</td>
                      <td data-label="IP Address" className="py-3 px-4 text-slate-400 font-mono text-xs">{log.ipAddress || '-'}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
};
