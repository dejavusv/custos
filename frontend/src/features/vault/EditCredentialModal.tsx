import React, { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { Loader2, Eye, EyeOff } from 'lucide-react';
import { vaultApi } from '../../services/vaultApi';
import { CredentialResponse } from '../../types/vault';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '../../components/ui/Dialog';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';

const editCredentialSchema = z.object({
  name: z.string().min(2, 'Name ต้องมีอย่างน้อย 2 ตัวอักษร'),
  description: z.string().optional(),
  host: z.string().optional(),
  port: z.coerce.number().optional(),
  username: z.string().optional(),
  databaseName: z.string().optional(),
  secretPassword: z.string().optional(),
  sshPrivateKey: z.string().optional(),
  sshPassphrase: z.string().optional(),
});

type EditCredentialFormValues = z.infer<typeof editCredentialSchema>;

interface EditCredentialModalProps {
  credential: CredentialResponse | null;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export const EditCredentialModal: React.FC<EditCredentialModalProps> = ({
  credential,
  isOpen,
  onClose,
  onSuccess,
}) => {
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<EditCredentialFormValues>({
    resolver: zodResolver(editCredentialSchema),
  });

  useEffect(() => {
    if (credential) {
      reset({
        name: credential.name,
        description: credential.description || '',
        host: credential.host || '',
        port: credential.port,
        username: credential.username || '',
        databaseName: credential.databaseName || '',
        secretPassword: '', // Blank means keep existing password
        sshPrivateKey: '',
        sshPassphrase: '',
      });
      setErrorMessage(null);
    }
  }, [credential, reset]);

  if (!credential) return null;

  const onSubmit = async (values: EditCredentialFormValues) => {
    setIsLoading(true);
    setErrorMessage(null);

    try {
      await vaultApi.updateCredential(credential.id, {
        name: values.name,
        description: values.description,
        host: values.host || undefined,
        port: values.port || undefined,
        username: values.username || undefined,
        databaseName: values.databaseName || undefined,
        secretPassword: values.secretPassword || undefined,
        sshPrivateKey: values.sshPrivateKey || undefined,
        sshPassphrase: values.sshPassphrase || undefined,
      });

      onSuccess();
      onClose();
    } catch (err: any) {
      const msg =
        err.response?.data?.message || 'เกิดข้อผิดพลาดในการอัปเดต Credential Profile';
      setErrorMessage(msg);
    } finally {
      setIsLoading(false);
    }
  };

  const isDatabase =
    credential.credentialType === 'DATABASE_POSTGRESQL' ||
    credential.credentialType === 'DATABASE_MYSQL';
  const isSftp = credential.credentialType === 'SFTP';

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="text-xl font-bold">
            แก้ไข Credential Profile: {credential.name}
          </DialogTitle>
          <DialogDescription className="text-sm text-muted-foreground">
            หากไม่ต้องการเปลี่ยนรหัสผ่าน ให้เว้นว่างช่อง Password ไว้ ระบบจะใช้รหัสผ่านเดิม
          </DialogDescription>
        </DialogHeader>

        {errorMessage && (
          <div className="p-3 text-sm text-destructive bg-destructive/10 border border-destructive/20 rounded-md">
            {errorMessage}
          </div>
        )}

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 pt-2">
          {/* Name & Description */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-foreground">
                ชื่อ Profile <span className="text-destructive">*</span>
              </label>
              <Input {...register('name')} disabled={isLoading} />
              {errors.name && (
                <p className="text-xs text-destructive">{errors.name.message}</p>
              )}
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-foreground">คำอธิบาย</label>
              <Input {...register('description')} disabled={isLoading} />
            </div>
          </div>

          {/* Host & Port */}
          {credential.credentialType !== 'GENERIC_SECRET' && (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <div className="md:col-span-2 space-y-1.5">
                <label className="text-xs font-semibold text-foreground">
                  Host / IP Address
                </label>
                <Input {...register('host')} disabled={isLoading} />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground">Port</label>
                <Input type="number" {...register('port')} disabled={isLoading} />
              </div>
            </div>
          )}

          {/* Username & Database Name */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {credential.credentialType !== 'GENERIC_SECRET' && (
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground">Username</label>
                <Input {...register('username')} disabled={isLoading} />
              </div>
            )}

            {isDatabase && (
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground">
                  Database Name
                </label>
                <Input {...register('databaseName')} disabled={isLoading} />
              </div>
            )}
          </div>

          {/* Password with Eye Toggle */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-foreground">
              Password (เว้นว่างไว้เพื่อคงรหัสผ่านเดิม)
            </label>
            <div className="relative">
              <Input
                type={showPassword ? 'text' : 'password'}
                placeholder="******** (คงรหัสผ่านเดิม)"
                className="pr-10"
                {...register('secretPassword')}
                disabled={isLoading}
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </div>

          {/* SFTP Key Configuration */}
          {isSftp && (
            <div className="space-y-3 p-3 border border-border rounded-lg bg-secondary/10">
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground">
                  SSH Private Key (เว้นว่างไว้หากไม่ต้องการเปลี่ยน)
                </label>
                <textarea
                  rows={4}
                  placeholder="-----BEGIN RSA PRIVATE KEY-----&#10;...&#10;-----END RSA PRIVATE KEY-----"
                  className="w-full text-xs font-mono p-2.5 rounded-md border border-input bg-background focus:outline-none focus:ring-2 focus:ring-ring"
                  {...register('sshPrivateKey')}
                  disabled={isLoading}
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground">
                  Key Passphrase (ถ้ามี)
                </label>
                <Input
                  type="password"
                  placeholder="Passphrase"
                  {...register('sshPassphrase')}
                  disabled={isLoading}
                />
              </div>
            </div>
          )}

          <div className="flex justify-end gap-2 pt-4 border-t border-border">
            <Button
              type="button"
              variant="outline"
              onClick={onClose}
              disabled={isLoading}
            >
              ยกเลิก
            </Button>
            <Button type="submit" disabled={isLoading} className="gap-2">
              {isLoading && <Loader2 className="w-4 h-4 animate-spin" />}
              บันทึกการแก้ไข
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
};
