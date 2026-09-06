import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { 
  Users, 
  UserPlus, 
  Search, 
  RefreshCw, 
  Edit, 
  KeyRound, 
  Trash2, 
  Ban, 
  CheckCircle,
  AlertTriangle
} from 'lucide-react';
import { api } from '../../services/api';
import { User, UserStatus } from '../../types/user';
import { PageableResponse, ApiResponse } from '../../types/api';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Badge } from '../../components/ui/Badge';
import { Card, CardContent } from '../../components/ui/Card';
import { CreateUserModal } from './CreateUserModal';
import { EditUserModal } from './EditUserModal';
import { ResetPasswordModal } from './ResetPasswordModal';
import { useAuthStore } from '../../stores/authStore';

export const UserManagementPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { user: currentUser } = useAuthStore();
  const isSuperAdmin = currentUser?.roles.includes('ROLE_SUPER_ADMIN');

  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [page, setPage] = useState(0);
  const pageSize = 10;

  // Modals state
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isResetOpen, setIsResetOpen] = useState(false);

  const { data, isLoading, isFetching, refetch } = useQuery<PageableResponse<User>>({
    queryKey: ['users', search, statusFilter, page],
    queryFn: async () => {
      const params: any = { page, size: pageSize };
      if (search) params.search = search;
      if (statusFilter) params.status = statusFilter;
      const res = await api.get<ApiResponse<PageableResponse<User>>>('/users', { params });
      return res.data.data!;
    },
  });

  // Toggle status mutation
  const statusMutation = useMutation({
    mutationFn: async ({ id, status }: { id: string; status: UserStatus }) => {
      await api.patch(`/users/${id}/status`, { status });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/users/${id}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });

  const handleDelete = (user: User) => {
    if (confirm(`คุณแน่ใจหรือไม่ที่จะลบผู้ใช้งาน "${user.username}"? การกระทำนี้ไม่สามารถย้อนกลับได้`)) {
      deleteMutation.mutate(user.id);
    }
  };

  const handleToggleStatus = (user: User) => {
    let nextStatus: UserStatus = user.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
    if (user.status === 'LOCKED') {
      nextStatus = 'ACTIVE'; // Unlock
    }
    statusMutation.mutate({ id: user.id, status: nextStatus });
  };

  const renderStatusBadge = (status: UserStatus) => {
    switch (status) {
      case 'ACTIVE':
        return <Badge variant="success">ใช้งานปกติ (Active)</Badge>;
      case 'SUSPENDED':
        return <Badge variant="warning">ระงับชั่วคราว (Suspended)</Badge>;
      case 'LOCKED':
        return <Badge variant="destructive">ถูกระงับสิทธิ์ (Locked)</Badge>;
      default:
        return <Badge variant="secondary">{status}</Badge>;
    }
  };

  const renderRoleBadge = (roleName: string) => {
    switch (roleName) {
      case 'ROLE_SUPER_ADMIN':
        return <Badge key={roleName} className="bg-purple-500/20 text-purple-300 border-purple-500/30">Super Admin</Badge>;
      case 'ROLE_ADMIN':
        return <Badge key={roleName} className="bg-blue-500/20 text-blue-300 border-blue-500/30">Admin</Badge>;
      case 'ROLE_OPERATOR':
        return <Badge key={roleName} className="bg-emerald-500/20 text-emerald-300 border-emerald-500/30">Operator</Badge>;
      case 'ROLE_VIEWER':
        return <Badge key={roleName} className="bg-slate-500/20 text-slate-300 border-slate-500/30">Viewer</Badge>;
      default:
        return <Badge key={roleName} variant="outline">{roleName}</Badge>;
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <Users className="w-6 h-6 text-primary" />
            การจัดการผู้ใช้งาน (User Management)
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            บริหารจัดการบัญชีผู้ใช้ สิทธิ์การเข้าถึง (RBAC) และติดตามประวัติการเข้าใช้งาน
          </p>
        </div>

        {isSuperAdmin && (
          <Button onClick={() => setIsCreateOpen(true)} className="gap-2 bg-primary text-white shadow-md">
            <UserPlus className="w-4 h-4" />
            <span>สร้างผู้ใช้ใหม่</span>
          </Button>
        )}
      </div>

      {/* Filter and Search Bar */}
      <Card className="border-slate-800 bg-slate-900/60 backdrop-blur-md shadow-sm">
        <CardContent className="p-4 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex flex-1 items-center gap-3 w-full sm:w-auto">
            <div className="relative flex-1 max-w-sm">
              <Search className="w-4 h-4 absolute left-3 top-2.5 text-slate-500" />
              <Input
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setPage(0);
                }}
                placeholder="ค้นหาตามชื่อผู้ใช้ หรือ Email..."
                className="pl-9 bg-slate-950 border-slate-800 text-white placeholder:text-slate-600"
              />
            </div>

            <select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value);
                setPage(0);
              }}
              className="h-9 px-3 rounded-md bg-slate-950 border border-slate-800 text-xs text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary"
            >
              <option value="">ทุกสถานะ (All Status)</option>
              <option value="ACTIVE">ACTIVE (ปกติ)</option>
              <option value="SUSPENDED">SUSPENDED (ระงับ)</option>
              <option value="LOCKED">LOCKED (ถูกล็อก)</option>
            </select>
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
        </CardContent>
      </Card>

      {/* Users Table */}
      <Card className="border-slate-800 bg-slate-900/90 shadow-xl overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-sm">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-950/80 text-xs text-slate-400 uppercase tracking-wider">
                <th className="py-3.5 px-4 font-semibold">ผู้ใช้งาน (User)</th>
                <th className="py-3.5 px-4 font-semibold">ระดับสิทธิ์ (Roles)</th>
                <th className="py-3.5 px-4 font-semibold">สถานะ (Status)</th>
                <th className="py-3.5 px-4 font-semibold">ล็อกอินล่าสุด (Last Login)</th>
                <th className="py-3.5 px-4 font-semibold text-right">การจัดการ (Actions)</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {isLoading ? (
                <tr>
                  <td colSpan={5} className="py-8 text-center text-slate-500">
                    <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2 text-primary" />
                    กำลังโหลดข้อมูลผู้ใช้งาน...
                  </td>
                </tr>
              ) : !data?.content || data.content.length === 0 ? (
                <tr>
                  <td colSpan={5} className="py-8 text-center text-slate-500">
                    ไม่พบข้อมูลผู้ใช้งานตามเงื่อนไขที่ค้นหา
                  </td>
                </tr>
              ) : (
                data.content.map((user) => (
                  <tr key={user.id} className="hover:bg-slate-800/40 transition-colors">
                    <td className="py-3.5 px-4">
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-full bg-primary/10 border border-primary/20 flex items-center justify-center text-primary font-bold text-xs">
                          {user.username.substring(0, 2).toUpperCase()}
                        </div>
                        <div>
                          <p className="font-semibold text-white">{user.username}</p>
                          <p className="text-xs text-slate-400">{user.email}</p>
                        </div>
                      </div>
                    </td>
                    <td className="py-3.5 px-4">
                      <div className="flex flex-wrap gap-1.5">
                        {user.roles.map((r) => renderRoleBadge(r))}
                      </div>
                    </td>
                    <td className="py-3.5 px-4">
                      <div className="space-y-1">
                        {renderStatusBadge(user.status)}
                        {user.failedLoginAttempts > 0 && user.status !== 'LOCKED' && (
                          <p className="text-[10px] text-amber-400 flex items-center gap-1 font-mono">
                            <AlertTriangle className="w-3 h-3" />
                            ผิด {user.failedLoginAttempts} ครั้ง
                          </p>
                        )}
                      </div>
                    </td>
                    <td className="py-3.5 px-4 text-xs text-slate-400 font-mono">
                      {user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString('th-TH') : 'ยังไม่เคยเข้าสู่ระบบ'}
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      {isSuperAdmin && (
                        <div className="flex items-center justify-end gap-1">
                          {/* Toggle status button */}
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleToggleStatus(user)}
                            title={user.status === 'ACTIVE' ? 'ระงับบัญชี' : 'เปิดใช้งานบัญชี'}
                            className="h-8 w-8 p-0 text-slate-400 hover:text-white"
                          >
                            {user.status === 'ACTIVE' ? (
                              <Ban className="w-4 h-4 text-amber-400" />
                            ) : (
                              <CheckCircle className="w-4 h-4 text-emerald-400" />
                            )}
                          </Button>

                          {/* Reset password */}
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              setSelectedUser(user);
                              setIsResetOpen(true);
                            }}
                            title="รีเซ็ตรหัสผ่าน"
                            className="h-8 w-8 p-0 text-slate-400 hover:text-amber-400"
                          >
                            <KeyRound className="w-4 h-4" />
                          </Button>

                          {/* Edit user */}
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              setSelectedUser(user);
                              setIsEditOpen(true);
                            }}
                            title="แก้ไขข้อมูล"
                            className="h-8 w-8 p-0 text-slate-400 hover:text-primary"
                          >
                            <Edit className="w-4 h-4" />
                          </Button>

                          {/* Delete user */}
                          {user.username !== 'admin' && (
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => handleDelete(user)}
                              title="ลบผู้ใช้"
                              className="h-8 w-8 p-0 text-slate-400 hover:text-rose-400"
                            >
                              <Trash2 className="w-4 h-4" />
                            </Button>
                          )}
                        </div>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Footer */}
        {data && data.totalPages > 1 && (
          <div className="p-4 border-t border-slate-800 flex items-center justify-between text-xs text-slate-400">
            <div>
              แสดงหน้า <span className="font-semibold text-white">{data.number + 1}</span> จาก{' '}
              <span className="font-semibold text-white">{data.totalPages}</span> (ทั้งหมด {data.totalElements} รายการ)
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={data.first}
                className="h-8 border-slate-800 text-xs text-slate-300"
              >
                ก่อนหน้า
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))}
                disabled={data.last}
                className="h-8 border-slate-800 text-xs text-slate-300"
              >
                ถัดไป
              </Button>
            </div>
          </div>
        )}
      </Card>

      {/* Modals */}
      <CreateUserModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onSuccess={() => queryClient.invalidateQueries({ queryKey: ['users'] })}
      />

      <EditUserModal
        user={selectedUser}
        isOpen={isEditOpen}
        onClose={() => {
          setIsEditOpen(false);
          setSelectedUser(null);
        }}
        onSuccess={() => queryClient.invalidateQueries({ queryKey: ['users'] })}
      />

      <ResetPasswordModal
        user={selectedUser}
        isOpen={isResetOpen}
        onClose={() => {
          setIsResetOpen(false);
          setSelectedUser(null);
        }}
        onSuccess={() => queryClient.invalidateQueries({ queryKey: ['users'] })}
      />
    </div>
  );
};
