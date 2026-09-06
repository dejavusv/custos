import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { Loader2, KeyRound } from 'lucide-react';
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

const resetPasswordSchema = z.object({
  newPassword: z.string().min(8, 'รหัสผ่านใหม่ต้องมีอย่างน้อย 8 ตัวอักษร'),
});

type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>;

interface ResetPasswordModalProps {
  user: User | null;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export const ResetPasswordModal: React.FC<ResetPasswordModalProps> = ({ user, isOpen, onClose, onSuccess }) => {
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordSchema),
  });

  const onSubmit = async (values: ResetPasswordFormValues) => {
    if (!user) return;
    setIsLoading(true);
    setErrorMessage(null);

    try {
      await api.post(`/users/${user.id}/reset-password`, values);
      reset();
      onSuccess();
      onClose();
    } catch (error: any) {
      setErrorMessage(error.response?.data?.message || 'ไม่สามารถรีเซ็ตรหัสผ่านได้');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md bg-slate-900 border-slate-800 text-white">
        <DialogHeader>
          <div className="w-10 h-10 rounded-full bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-amber-400 mb-2">
            <KeyRound className="w-5 h-5" />
          </div>
          <DialogTitle>รีเซ็ตรหัสผ่าน ({user?.username})</DialogTitle>
          <DialogDescription className="text-slate-400">
            กำหนดรหัสผ่านใหม่สำหรับผู้ใช้งานนี้ หากบัญชีติดสถานะ Lockout จะถูกปลดล็อกโดยอัตโนมัติ
          </DialogDescription>
        </DialogHeader>

        {errorMessage && (
          <div className="p-3 text-xs bg-destructive/15 border border-destructive/30 rounded text-rose-300">
            {errorMessage}
          </div>
        )}

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 pt-2">
          <div className="space-y-1">
            <label className="text-xs font-medium text-slate-300">รหัสผ่านใหม่</label>
            <Input
              type="password"
              {...register('newPassword')}
              placeholder="ความยาวอย่างน้อย 8 ตัวอักษร"
              className="bg-slate-950 border-slate-800 text-white"
            />
            {errors.newPassword && (
              <p className="text-[11px] text-rose-400">{errors.newPassword.message}</p>
            )}
          </div>

          <div className="flex justify-end gap-2 pt-4 border-t border-slate-800">
            <Button type="button" variant="outline" onClick={onClose} disabled={isLoading}>
              ยกเลิก
            </Button>
            <Button type="submit" disabled={isLoading} className="bg-amber-600 hover:bg-amber-500 text-white">
              {isLoading ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : null}
              ยืนยันรีเซ็ตรหัสผ่าน
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
};
