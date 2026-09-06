import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  KeyRound,
  Plus,
  Search,
  RefreshCw,
  Edit,
  Trash2,
  Database,
  Server,
  Lock,
  FileCode,
  ShieldCheck,
  AlertTriangle,
} from 'lucide-react';
import { vaultApi, PageResponse } from '../../services/vaultApi';
import { CredentialResponse, CredentialType } from '../../types/vault';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Badge } from '../../components/ui/Badge';
import { Card, CardContent } from '../../components/ui/Card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '../../components/ui/Dialog';
import { CreateCredentialModal } from './CreateCredentialModal';
import { EditCredentialModal } from './EditCredentialModal';
import { useAuthStore } from '../../stores/authStore';

export const CredentialsPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { user: currentUser } = useAuthStore();
  const isSuperAdmin = currentUser?.roles.includes('ROLE_SUPER_ADMIN');

  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState<CredentialType | ''>('');
  const [page, setPage] = useState(0);
  const pageSize = 10;

  // Modals state
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [selectedCredential, setSelectedCredential] = useState<CredentialResponse | null>(null);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [credentialToDelete, setCredentialToDelete] = useState<CredentialResponse | null>(null);

  const { data, isLoading, isFetching, refetch } = useQuery<PageResponse<CredentialResponse>>({
    queryKey: ['credentials', search, typeFilter, page],
    queryFn: async () => {
      return await vaultApi.getCredentials({
        search: search || undefined,
        type: (typeFilter as CredentialType) || undefined,
        page,
        size: pageSize,
      });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: string) => {
      await vaultApi.deleteCredential(id);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['credentials'] });
      setCredentialToDelete(null);
    },
  });

  const getTypeBadge = (type: CredentialType) => {
    switch (type) {
      case 'DATABASE_POSTGRESQL':
        return (
          <Badge variant="outline" className="bg-blue-500/10 text-blue-500 border-blue-500/20 gap-1">
            <Database className="w-3 h-3" /> PostgreSQL
          </Badge>
        );
      case 'DATABASE_MYSQL':
        return (
          <Badge variant="outline" className="bg-amber-500/10 text-amber-500 border-amber-500/20 gap-1">
            <Database className="w-3 h-3" /> MySQL
          </Badge>
        );
      case 'SFTP':
        return (
          <Badge variant="outline" className="bg-emerald-500/10 text-emerald-500 border-emerald-500/20 gap-1">
            <Server className="w-3 h-3" /> SFTP
          </Badge>
        );
      case 'FTP':
        return (
          <Badge variant="outline" className="bg-cyan-500/10 text-cyan-500 border-cyan-500/20 gap-1">
            <Server className="w-3 h-3" /> FTP
          </Badge>
        );
      default:
        return (
          <Badge variant="outline" className="bg-purple-500/10 text-purple-500 border-purple-500/20 gap-1">
            <Lock className="w-3 h-3" /> Secret
          </Badge>
        );
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <KeyRound className="w-6 h-6 text-primary" />
            <h1 className="text-2xl font-bold tracking-tight text-foreground">
              Credentials Vault
            </h1>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            จัดเก็บ Connection Profiles และรหัสผ่านที่เข้ารหัสด้วย AES-256-GCM ปลอดภัยระดับ Enterprise
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            disabled={isFetching}
            className="gap-2"
          >
            <RefreshCw className={`w-4 h-4 ${isFetching ? 'animate-spin' : ''}`} />
            รีเฟรช
          </Button>

          {isSuperAdmin && (
            <Button
              size="sm"
              onClick={() => setIsCreateOpen(true)}
              className="gap-2"
            >
              <Plus className="w-4 h-4" />
              เพิ่ม Credential
            </Button>
          )}
        </div>
      </div>

      {/* Filter and Search Bar */}
      <Card className="border-border bg-card shadow-sm">
        <CardContent className="p-4 space-y-3">
          <div className="flex flex-col md:flex-row gap-3">
            <div className="relative flex-1">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="ค้นหาตามชื่อ Credential Profile..."
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setPage(0);
                }}
                className="pl-9"
              />
            </div>

            <div className="flex items-center gap-2 overflow-x-auto pb-1 md:pb-0">
              {[
                { id: '', label: 'ทั้งหมด' },
                { id: 'DATABASE_POSTGRESQL', label: 'PostgreSQL' },
                { id: 'DATABASE_MYSQL', label: 'MySQL' },
                { id: 'SFTP', label: 'SFTP' },
                { id: 'FTP', label: 'FTP' },
                { id: 'GENERIC_SECRET', label: 'API Secrets' },
              ].map((filter) => (
                <button
                  key={filter.id}
                  onClick={() => {
                    setTypeFilter(filter.id as CredentialType | '');
                    setPage(0);
                  }}
                  className={`px-3 py-1.5 text-xs font-medium rounded-md whitespace-nowrap transition-all ${
                    typeFilter === filter.id
                      ? 'bg-primary text-primary-foreground shadow-sm'
                      : 'bg-secondary/40 text-muted-foreground hover:text-foreground hover:bg-secondary'
                  }`}
                >
                  {filter.label}
                </button>
              ))}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Data Table */}
      <Card className="border-border bg-card shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="bg-secondary/30 text-xs font-semibold text-muted-foreground uppercase border-b border-border">
              <tr>
                <th className="px-5 py-3">ชื่อ Profile</th>
                <th className="px-5 py-3">ประเภท</th>
                <th className="px-5 py-3">Host & Port</th>
                <th className="px-5 py-3">Database / User</th>
                <th className="px-5 py-3">ความปลอดภัย</th>
                <th className="px-5 py-3 text-right">การจัดการ</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {isLoading ? (
                <tr>
                  <td colSpan={6} className="text-center py-12 text-muted-foreground">
                    <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2 text-primary" />
                    กำลังโหลดข้อมูล Credentials...
                  </td>
                </tr>
              ) : !data?.content || data.content.length === 0 ? (
                <tr>
                  <td colSpan={6} className="text-center py-12 text-muted-foreground">
                    <KeyRound className="w-10 h-10 mx-auto mb-2 opacity-30" />
                    ไม่พบข้อมูล Credential ในระบบ
                  </td>
                </tr>
              ) : (
                data.content.map((item) => (
                  <tr key={item.id} className="hover:bg-secondary/20 transition-colors">
                    <td className="px-5 py-3.5">
                      <div className="font-semibold text-foreground">{item.name}</div>
                      {item.description && (
                        <div className="text-xs text-muted-foreground mt-0.5">
                          {item.description}
                        </div>
                      )}
                    </td>
                    <td className="px-5 py-3.5">{getTypeBadge(item.credentialType)}</td>
                    <td className="px-5 py-3.5 font-mono text-xs text-muted-foreground">
                      {item.host ? `${item.host}:${item.port || '-'}` : '-'}
                    </td>
                    <td className="px-5 py-3.5 text-xs">
                      {item.databaseName && (
                        <span className="font-semibold text-foreground">
                          {item.databaseName}
                        </span>
                      )}
                      {item.username && (
                        <span className="text-muted-foreground block font-mono">
                          {item.username}
                        </span>
                      )}
                      {!item.databaseName && !item.username && '-'}
                    </td>
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-1.5">
                        <Badge
                          variant="secondary"
                          className="bg-emerald-500/10 text-emerald-500 border-emerald-500/20 text-[10px] gap-1"
                        >
                          <ShieldCheck className="w-3 h-3" /> AES-256-GCM
                        </Badge>
                        {item.hasSshKey && (
                          <Badge
                            variant="secondary"
                            className="bg-blue-500/10 text-blue-500 border-blue-500/20 text-[10px] gap-1"
                          >
                            <FileCode className="w-3 h-3" /> SSH Key
                          </Badge>
                        )}
                      </div>
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      {isSuperAdmin ? (
                        <div className="flex items-center justify-end gap-1">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              setSelectedCredential(item);
                              setIsEditOpen(true);
                            }}
                            className="h-8 w-8 p-0"
                            title="แก้ไข Credential"
                          >
                            <Edit className="w-4 h-4 text-muted-foreground hover:text-foreground" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setCredentialToDelete(item)}
                            className="h-8 w-8 p-0 text-destructive hover:bg-destructive/10"
                            title="ลบ Credential"
                          >
                            <Trash2 className="w-4 h-4" />
                          </Button>
                        </div>
                      ) : (
                        <span className="text-xs text-muted-foreground">Read-only</span>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Controls */}
        {data && data.totalPages > 1 && (
          <div className="px-5 py-3 border-t border-border flex items-center justify-between">
            <div className="text-xs text-muted-foreground">
              หน้า {data.number + 1} จาก {data.totalPages} (ทั้งหมด {data.totalElements} รายการ)
            </div>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
              >
                ก่อนหน้า
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))}
                disabled={page >= data.totalPages - 1}
              >
                ถัดไป
              </Button>
            </div>
          </div>
        )}
      </Card>

      {/* Create Modal */}
      <CreateCredentialModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onSuccess={() => {
          queryClient.invalidateQueries({ queryKey: ['credentials'] });
        }}
      />

      {/* Edit Modal */}
      <EditCredentialModal
        credential={selectedCredential}
        isOpen={isEditOpen}
        onClose={() => {
          setIsEditOpen(false);
          setSelectedCredential(null);
        }}
        onSuccess={() => {
          queryClient.invalidateQueries({ queryKey: ['credentials'] });
        }}
      />

      {/* Delete Confirmation Dialog */}
      <Dialog
        open={!!credentialToDelete}
        onOpenChange={(open) => !open && setCredentialToDelete(null)}
      >
        <DialogContent className="max-w-md">
          <DialogHeader>
            <div className="flex items-center gap-2 text-destructive">
              <AlertTriangle className="w-5 h-5" />
              <DialogTitle>ยืนยันการลบ Credential Profile</DialogTitle>
            </div>
            <DialogDescription className="text-sm pt-2">
              คุณแน่ใจหรือไม่ว่าต้องการลบ Credential Profile{' '}
              <strong className="text-foreground">{credentialToDelete?.name}</strong>?
              การกระทำนี้ไม่สามารถเรียกคืนได้
            </DialogDescription>
          </DialogHeader>

          <div className="flex justify-end gap-2 pt-4 border-t border-border">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setCredentialToDelete(null)}
              disabled={deleteMutation.isPending}
            >
              ยกเลิก
            </Button>
            <Button
              variant="destructive"
              size="sm"
              onClick={() => {
                if (credentialToDelete) {
                  deleteMutation.mutate(credentialToDelete.id);
                }
              }}
              disabled={deleteMutation.isPending}
            >
              {deleteMutation.isPending ? 'กำลังลบ...' : 'ยืนยันการลบ'}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
};
