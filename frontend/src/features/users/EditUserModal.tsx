import React, { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { Loader2 } from 'lucide-react';
import { api } from '../../services/api';
import { User } from '../../types/user';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '../../components/ui/Dialog';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';

const editUserSchema = z.object({
  email: z.string().email('รูปแบบอีเมลไม่ถูกต้อง'),
  roles: z.array(z.string()).min(1, 'กรุณาเลือกอย่างน้อย 1 สิทธิ์'),
});

type EditUserFormValues = z.infer<typeof editUserSchema>;

interface EditUserModalProps {
  user: User | null;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const AVAILABLE_ROLES = [
  { id: 'ROLE_SUPER_ADMIN', label: 'Super Admin (สิทธิ์สูงสุด)' },
  { id: 'ROLE_ADMIN', label: 'Admin (จัดการ Task และ Pipeline)' },
  { id: 'ROLE_OPERATOR', label: 'Operator (สั่งรันและดู Log)' },
  { id: 'ROLE_VIEWER', label: 'Viewer (ดูรายงานได้อย่างเดียว)' },
];

export const EditUserModal: React.FC<EditUserModalProps> = ({ user, isOpen, onClose, onSuccess }) => {
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors },
  } = useForm<EditUserFormValues>({
    resolver: zodResolver(editUserSchema),
  });

  useEffect(() => {
    if (user) {
      reset({
        email: user.email,
        roles: user.roles,
      });
    }
  }, [user, reset]);

  const selectedRoles = watch('roles') || [];

  const handleRoleToggle = (roleId: string) => {
    if (selectedRoles.includes(roleId)) {
      setValue('roles', selectedRoles.filter((r) => r !== roleId));
    } else {
      setValue('roles', [...selectedRoles, roleId]);
    }
  };

  const onSubmit = async (values: EditUserFormValues) => {
    if (!user) return;
    setIsLoading(true);
    setErrorMessage(null);

    try {
      await api.put(`/users/${user.id}`, values);
      onSuccess();
      onClose();
    } catch (error: any) {
      setErrorMessage(error.response?.data?.message || 'ไม่สามารถแก้ไขข้อมูลได้');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md bg-slate-900 border-slate-800 text-white">
        <DialogHeader>
          <DialogTitle>แก้ไขข้อมูลผู้ใช้งาน ({user?.username})</DialogTitle>
          <DialogDescription className="text-slate-400">
            ปรับเปลี่ยน Email และสิทธิ์การเข้าถึงของผู้ใช้
          </DialogDescription>
        </DialogHeader>

        {errorMessage && (
          <div className="p-3 text-xs bg-destructive/15 border border-destructive/30 rounded text-rose-300">
            {errorMessage}
          </div>
        )}

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 pt-2">
          <div className="space-y-1">
            <label className="text-xs font-medium text-slate-300">Email</label>
            <Input type="email" {...register('email')} className="bg-slate-950 border-slate-800 text-white" />
            {errors.email && <p className="text-[11px] text-rose-400">{errors.email.message}</p>}
          </div>

          <div className="space-y-2">
            <label className="text-xs font-medium text-slate-300">ระดับสิทธิ์ (Roles)</label>
            <div className="space-y-2">
              {AVAILABLE_ROLES.map((role) => (
                <label
                  key={role.id}
                  className="flex items-center gap-2.5 p-2 rounded-lg bg-slate-950/60 border border-slate-800 hover:border-slate-700 cursor-pointer transition-colors"
                >
                  <input
                    type="checkbox"
                    checked={selectedRoles.includes(role.id)}
                    onChange={() => handleRoleToggle(role.id)}
                    className="rounded border-slate-700 text-primary focus:ring-primary h-4 w-4 bg-slate-900"
                  />
                  <span className="text-xs text-slate-200">{role.label}</span>
                </label>
              ))}
            </div>
            {errors.roles && <p className="text-[11px] text-rose-400">{errors.roles.message}</p>}
          </div>

          <div className="flex justify-end gap-2 pt-4 border-t border-slate-800">
            <Button type="button" variant="outline" onClick={onClose} disabled={isLoading}>
              ยกเลิก
            </Button>
            <Button type="submit" disabled={isLoading} className="bg-primary text-white">
              {isLoading ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : null}
              บันทึกการแก้ไข
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
};
