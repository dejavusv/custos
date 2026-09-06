import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { Loader2 } from 'lucide-react';
import { api } from '../../services/api';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '../../components/ui/Dialog';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';

const createUserSchema = z.object({
  username: z.string().min(3, 'Username ต้องมีอย่างน้อย 3 ตัวอักษร'),
  email: z.string().email('รูปแบบอีเมลไม่ถูกต้อง'),
  password: z.string().min(8, 'รหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร'),
  roles: z.array(z.string()).min(1, 'กรุณาเลือกอย่างน้อย 1 สิทธิ์'),
});

type CreateUserFormValues = z.infer<typeof createUserSchema>;

interface CreateUserModalProps {
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

export const CreateUserModal: React.FC<CreateUserModalProps> = ({ isOpen, onClose, onSuccess }) => {
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors },
  } = useForm<CreateUserFormValues>({
    resolver: zodResolver(createUserSchema),
    defaultValues: {
      username: '',
      email: '',
      password: '',
      roles: ['ROLE_OPERATOR'],
    },
  });

  const selectedRoles = watch('roles') || [];

  const handleRoleToggle = (roleId: string) => {
    if (selectedRoles.includes(roleId)) {
      setValue('roles', selectedRoles.filter((r) => r !== roleId));
    } else {
      setValue('roles', [...selectedRoles, roleId]);
    }
  };

  const onSubmit = async (values: CreateUserFormValues) => {
    setIsLoading(true);
    setErrorMessage(null);

    try {
      await api.post('/users', values);
      reset();
      onSuccess();
      onClose();
    } catch (error: any) {
      setErrorMessage(error.response?.data?.message || 'ไม่สามารถสร้างผู้ใช้งานได้');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md bg-slate-900 border-slate-800 text-white">
        <DialogHeader>
          <DialogTitle>สร้างผู้ใช้งานใหม่ (Add New User)</DialogTitle>
          <DialogDescription className="text-slate-400">
            กำหนดข้อมูลบัญชีผู้ใช้งาน และเลือกระดับสิทธิ์ในการเข้าถึงระบบ
          </DialogDescription>
        </DialogHeader>

        {errorMessage && (
          <div className="p-3 text-xs bg-destructive/15 border border-destructive/30 rounded text-rose-300">
            {errorMessage}
          </div>
        )}

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 pt-2">
          <div className="space-y-1">
            <label className="text-xs font-medium text-slate-300">Username</label>
            <Input {...register('username')} placeholder="operator_john" className="bg-slate-950 border-slate-800 text-white" />
            {errors.username && <p className="text-[11px] text-rose-400">{errors.username.message}</p>}
          </div>

          <div className="space-y-1">
            <label className="text-xs font-medium text-slate-300">Email</label>
            <Input type="email" {...register('email')} placeholder="john@example.com" className="bg-slate-950 border-slate-800 text-white" />
            {errors.email && <p className="text-[11px] text-rose-400">{errors.email.message}</p>}
          </div>

          <div className="space-y-1">
            <label className="text-xs font-medium text-slate-300">Password</label>
            <Input type="password" {...register('password')} placeholder="อย่างน้อย 8 ตัวอักษร" className="bg-slate-950 border-slate-800 text-white" />
            {errors.password && <p className="text-[11px] text-rose-400">{errors.password.message}</p>}
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
              บันทึกผู้ใช้
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
};
