import React from 'react';
import { 
  Server, 
  ShieldCheck, 
  Activity, 
  CheckCircle2, 
  Clock, 
  Layers, 
  Users
} from 'lucide-react';
import { useAuthStore } from '../../stores/authStore';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Link } from 'react-router-dom';

export const DashboardPage: React.FC = () => {
  const { user } = useAuthStore();

  const stats = [
    { title: 'System Status', value: 'HEALTHY', sub: 'PostgreSQL & Spring Boot Connected', icon: Activity, color: 'text-emerald-400' },
    { title: 'Security Engine', value: 'ACTIVE', sub: 'BCrypt & JWT Token Rotation', icon: ShieldCheck, color: 'text-sky-400' },
    { title: 'Active User', value: user?.username || 'admin', sub: `Role: ${user?.roles?.[0] || 'SUPER_ADMIN'}`, icon: Users, color: 'text-purple-400' },
    { title: 'Environment', value: 'DEVELOPMENT', sub: 'Docker Containers Active', icon: Server, color: 'text-amber-400' },
  ];

  const phaseProgress = [
    { name: 'Phase 1: Foundation, Security & User Management', status: 'COMPLETED', desc: 'Spring Security, JWT Rotation, Account Lockout, RBAC, User Management UI' },
    { name: 'Phase 2: Task Engines & Credential Vault', status: 'READY_NEXT', desc: 'AES-256-GCM Vault, ProcessBuilder CLI Sandboxing, DB/File Backup, Chunk Splitter' },
    { name: 'Phase 3: Pipeline Orchestration & Scheduler', status: 'PLANNED', desc: 'Quartz Scheduler, DAG Workflow Engine, React Flow Visual Builder' },
    { name: 'Phase 4: Real-time Live Console & AWS SES', status: 'PLANNED', desc: 'WebSocket STOMP Log Streaming, Terminal Widget, SES Email Alerting' },
    { name: 'Phase 5: Testing, Hardening & Resilience', status: 'PLANNED', desc: 'Testcontainers Integration Tests, Fault Injection, Chunk Retry' },
    { name: 'Phase 6: Containerization & Deployment', status: 'PLANNED', desc: 'Multi-stage Docker, Actuator Metrics, Handover Docs' },
  ];

  return (
    <div className="space-y-8">
      {/* Welcome Banner */}
      <div className="p-6 rounded-2xl bg-gradient-to-r from-slate-900 via-slate-900 to-slate-800 border border-slate-800 shadow-lg flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 mb-2">
            <Badge variant="outline" className="border-emerald-500/30 text-emerald-400 bg-emerald-500/10 text-xs">
              System Operational
            </Badge>
            <span className="text-xs text-slate-500">v1.0.0-SNAPSHOT</span>
          </div>
          <h1 className="text-2xl font-bold text-white tracking-tight">
            ยินดีต้อนรับสู่ Custos Platform, {user?.username}
          </h1>
          <p className="text-sm text-slate-400 mt-1 max-w-2xl">
            ระบบ Server Automation & Task Scheduling Platform สำหรับงานสำรองข้อมูลและร้อยเรียง Pipeline ปัจจุบัน Phase 1 พร้อมใช้งานแล้ว
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Link to="/users">
            <Button variant="outline" className="border-slate-700 text-slate-300 hover:text-white">
              จัดการผู้ใช้งาน
            </Button>
          </Link>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {stats.map((stat, idx) => {
          const Icon = stat.icon;
          return (
            <Card key={idx} className="border-slate-800 bg-slate-900/80 shadow-md">
              <CardContent className="p-5 flex items-center justify-between">
                <div>
                  <p className="text-xs font-medium text-slate-400 uppercase tracking-wider">{stat.title}</p>
                  <p className="text-xl font-bold text-white mt-1">{stat.value}</p>
                  <p className="text-[11px] text-slate-500 mt-0.5">{stat.sub}</p>
                </div>
                <div className={`p-3 rounded-xl bg-slate-950 border border-slate-800 ${stat.color}`}>
                  <Icon className="w-5 h-5" />
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      {/* Implementation Roadmap Status */}
      <Card className="border-slate-800 bg-slate-900/90 shadow-xl">
        <CardHeader>
          <CardTitle className="text-lg font-bold text-white flex items-center gap-2">
            <Layers className="w-5 h-5 text-primary" />
            สถานะความคืบหน้าของโครงการ (Development Roadmap)
          </CardTitle>
          <CardDescription className="text-xs text-slate-400">
            ภาพรวมการพัฒนาตาม Phase ที่กำหนดในเอกสาร plan.md
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            {phaseProgress.map((phase, idx) => (
              <div
                key={idx}
                className="p-4 rounded-xl bg-slate-950/60 border border-slate-800/80 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-sm text-white">{phase.name}</span>
                    {phase.status === 'COMPLETED' && (
                      <Badge variant="success" className="text-[10px]">
                        เสร็จสมบูรณ์
                      </Badge>
                    )}
                    {phase.status === 'READY_NEXT' && (
                      <Badge variant="info" className="text-[10px]">
                        พร้อมพัฒนาต่อไป
                      </Badge>
                    )}
                    {phase.status === 'PLANNED' && (
                      <Badge variant="secondary" className="text-[10px]">
                        ตามแผนงาน
                      </Badge>
                    )}
                  </div>
                  <p className="text-xs text-slate-400">{phase.desc}</p>
                </div>

                <div className="shrink-0">
                  {phase.status === 'COMPLETED' ? (
                    <CheckCircle2 className="w-5 h-5 text-emerald-400" />
                  ) : (
                    <Clock className="w-5 h-5 text-slate-600" />
                  )}
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
};
