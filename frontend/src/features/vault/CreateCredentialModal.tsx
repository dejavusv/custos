import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { Loader2, Eye, EyeOff } from 'lucide-react';
import { vaultApi } from '../../services/vaultApi';
import { CredentialType } from '../../types/vault';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '../../components/ui/Dialog';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';

const createCredentialSchema = z.object({
  name: z.string().min(2, 'Name ต้องมีอย่างน้อย 2 ตัวอักษร'),
  description: z.string().optional(),
  credentialType: z.enum([
    'DATABASE_MYSQL',
    'DATABASE_POSTGRESQL',
    'SFTP',
    'FTP',
    'GENERIC_SECRET',
  ]),
  host: z.string().optional(),
  port: z.coerce.number().optional(),
  username: z.string().optional(),
  databaseName: z.string().optional(),
  secretPassword: z.string().optional(),
  sshPrivateKey: z.string().optional(),
  sshPassphrase: z.string().optional(),
  sslMode: z.string().optional(),
});

type CreateCredentialFormValues = z.infer<typeof createCredentialSchema>;

interface CreateCredentialModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const CREDENTIAL_TYPES: { id: CredentialType; label: string; defaultPort?: number }[] = [
  { id: 'DATABASE_POSTGRESQL', label: 'PostgreSQL Database', defaultPort: 5432 },
  { id: 'DATABASE_MYSQL', label: 'MySQL / MariaDB', defaultPort: 3306 },
  { id: 'SFTP', label: 'SFTP (SSH File Transfer)', defaultPort: 22 },
  { id: 'FTP', label: 'FTP / FTPS', defaultPort: 21 },
  { id: 'GENERIC_SECRET', label: 'Generic API Secret / Token' },
];

export const CreateCredentialModal: React.FC<CreateCredentialModalProps> = ({
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
    watch,
    setValue,
    formState: { errors },
  } = useForm<CreateCredentialFormValues>({
    resolver: zodResolver(createCredentialSchema),
    defaultValues: {
      name: '',
      description: '',
      credentialType: 'DATABASE_POSTGRESQL',
      port: 5432,
      username: '',
      host: '',
      databaseName: '',
      secretPassword: '',
      sshPrivateKey: '',
      sshPassphrase: '',
      sslMode: 'prefer',
    },
  });

  const selectedType = watch('credentialType');

  const handleTypeChange = (type: CredentialType) => {
    setValue('credentialType', type);
    const found = CREDENTIAL_TYPES.find((t) => t.id === type);
    if (found?.defaultPort) {
      setValue('port', found.defaultPort);
    }
  };

  const onSubmit = async (values: CreateCredentialFormValues) => {
    setIsLoading(true);
    setErrorMessage(null);

    try {
      let extraMetadataStr: string | undefined = undefined;
      if (values.sslMode) {
        extraMetadataStr = JSON.stringify({ sslMode: values.sslMode });
      }

      await vaultApi.createCredential({
        name: values.name,
        description: values.description,
        credentialType: values.credentialType,
        host: values.host || undefined,
        port: values.port || undefined,
        username: values.username || undefined,
        databaseName: values.databaseName || undefined,
        secretPassword: values.secretPassword || undefined,
        sshPrivateKey: values.sshPrivateKey || undefined,
        sshPassphrase: values.sshPassphrase || undefined,
        extraMetadata: extraMetadataStr,
      });

      reset();
      onSuccess();
      onClose();
    } catch (err: any) {
      const msg =
        err.response?.data?.message || 'เกิดข้อผิดพลาดในการบันทึก Credential Profile';
      setErrorMessage(msg);
    } finally {
      setIsLoading(false);
    }
  };

  const isDatabase =
    selectedType === 'DATABASE_POSTGRESQL' || selectedType === 'DATABASE_MYSQL';
  const isSftp = selectedType === 'SFTP';

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="text-xl font-bold">
            เพิ่ม Credential Profile ใน Vault
          </DialogTitle>
          <DialogDescription className="text-sm text-muted-foreground">
            ข้อมูลรหัสผ่านและ Private Key จะถูกเข้ารหัสด้วย AES-256-GCM อัตโนมัติก่อนบันทึก
          </DialogDescription>
        </DialogHeader>

        {errorMessage && (
          <div className="p-3 text-sm text-destructive bg-destructive/10 border border-destructive/20 rounded-md">
            {errorMessage}
          </div>
        )}

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 pt-2">
          {/* Credential Type Selector */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              ประเภทการเชื่อมต่อ (Credential Type) <span className="text-destructive">*</span>
            </label>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
              {CREDENTIAL_TYPES.map((t) => (
                <button
                  type="button"
                  key={t.id}
                  onClick={() => handleTypeChange(t.id)}
                  className={`px-3 py-2 text-xs font-medium rounded-lg border text-left transition-all ${
                    selectedType === t.id
                      ? 'bg-primary text-primary-foreground border-primary shadow-sm'
                      : 'border-border bg-card hover:bg-secondary/50 text-foreground'
                  }`}
                >
                  {t.label}
                </button>
              ))}
            </div>
          </div>

          {/* Name & Description */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-foreground">
                ชื่อ Profile <span className="text-destructive">*</span>
              </label>
              <Input
                placeholder="เช่น Prod-Postgres-Primary"
                {...register('name')}
                disabled={isLoading}
              />
              {errors.name && (
                <p className="text-xs text-destructive">{errors.name.message}</p>
              )}
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-foreground">คำอธิบาย</label>
              <Input
                placeholder="เช่น เซิร์ฟเวอร์สำรองข้อมูลหลัก"
                {...register('description')}
                disabled={isLoading}
              />
            </div>
          </div>

          {/* Host & Port */}
          {selectedType !== 'GENERIC_SECRET' && (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <div className="md:col-span-2 space-y-1.5">
                <label className="text-xs font-semibold text-foreground">
                  Host / IP Address
                </label>
                <Input
                  placeholder="เช่น 192.168.1.100 หรือ db.example.com"
                  {...register('host')}
                  disabled={isLoading}
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground">Port</label>
                <Input
                  type="number"
                  placeholder="Port"
                  {...register('port')}
                  disabled={isLoading}
                />
              </div>
            </div>
          )}

          {/* Username & Database Name */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {selectedType !== 'GENERIC_SECRET' && (
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground">Username</label>
                <Input
                  placeholder="เช่น postgres / dbadmin / sftpuser"
                  {...register('username')}
                  disabled={isLoading}
                />
              </div>
            )}

            {isDatabase && (
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground">
                  Database Name
                </label>
                <Input
                  placeholder="เช่น production_db"
                  {...register('databaseName')}
                  disabled={isLoading}
                />
              </div>
            )}
          </div>

          {/* Password with Eye Toggle */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-foreground">
              {selectedType === 'GENERIC_SECRET'
                ? 'Secret Token / Key'
                : 'Password (เข้ารหัส AES-256-GCM)'}
            </label>
            <div className="relative">
              <Input
                type={showPassword ? 'text' : 'password'}
                placeholder="กรอกรหัสผ่านเพื่อเข้ารหัสจัดเก็บ"
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
                  SSH Private Key (PEM format - ตัวเลือกเสริม)
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
                  placeholder="Passphrase สำหรับถอดรหัส Private Key"
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
              บันทึกลง Vault
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
};
