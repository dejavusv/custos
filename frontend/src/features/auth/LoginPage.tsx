import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { useNavigate } from 'react-router-dom';
import { ShieldAlert, Lock, User, AlertCircle, Loader2 } from 'lucide-react';
import { api } from '../../services/api';
import { useAuthStore } from '../../stores/authStore';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/Card';

const loginSchema = z.object({
  username: z.string().min(1, 'กรุณาระบุชื่อผู้ใช้หรืออีเมล'),
  password: z.string().min(1, 'กรุณาระบุรหัสผ่าน'),
});

type LoginFormValues = z.infer<typeof loginSchema>;

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const { login } = useAuthStore();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      username: 'admin',
      password: '',
    },
  });

  const onSubmit = async (values: LoginFormValues) => {
    setIsLoading(true);
    setErrorMessage(null);

    try {
      const response = await api.post('/auth/login', values);
      login(response.data.data);
      navigate('/dashboard');
    } catch (error: any) {
      const msg = error.response?.data?.message || 'การเข้าสู่ระบบล้มเหลว กรุณาตรวจสอบชื่อผู้ใช้และรหัสผ่าน';
      setErrorMessage(msg);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen w-full flex items-center justify-center bg-slate-950 px-4 relative overflow-hidden">
      {/* Background glow effects */}
      <div className="absolute top-1/4 -left-20 w-96 h-96 bg-primary/10 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-1/4 -right-20 w-96 h-96 bg-sky-500/10 rounded-full blur-3xl pointer-events-none" />

      <div className="w-full max-w-md z-10">
        <div className="flex flex-col items-center mb-8 text-center">
          <div className="w-14 h-14 rounded-2xl bg-primary/10 border border-primary/20 flex items-center justify-center text-primary shadow-lg mb-4">
            <ShieldAlert className="w-8 h-8 text-primary" />
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-white">Custos Platform</h1>
          <p className="text-sm text-slate-400 mt-1">
            Server Automation & Task Scheduling Platform
          </p>
        </div>

        <Card className="border-slate-800 bg-slate-900/90 shadow-2xl backdrop-blur-xl">
          <CardHeader className="space-y-1 pb-4">
            <CardTitle className="text-lg font-semibold text-white">เข้าสู่ระบบ</CardTitle>
            <CardDescription className="text-xs text-slate-400">
              ระบุ Username/Email และ Password เพื่อเข้าใช้งานระบบ
            </CardDescription>
          </CardHeader>

          <CardContent>
            {errorMessage && (
              <div className="mb-4 p-3 rounded-lg bg-destructive/15 border border-destructive/30 flex items-start gap-2.5 text-xs text-rose-300">
                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5 text-rose-400" />
                <span className="leading-relaxed">{errorMessage}</span>
              </div>
            )}

            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-slate-300">ชื่อผู้ใช้หรืออีเมล</label>
                <div className="relative">
                  <User className="w-4 h-4 absolute left-3 top-2.5 text-slate-500" />
                  <Input
                    {...register('username')}
                    placeholder="admin หรือ username@email.com"
                    className="pl-9 bg-slate-950/60 border-slate-800 text-white placeholder:text-slate-600 focus-visible:ring-primary"
                  />
                </div>
                {errors.username && (
                  <p className="text-[11px] text-rose-400">{errors.username.message}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-medium text-slate-300">รหัสผ่าน</label>
                <div className="relative">
                  <Lock className="w-4 h-4 absolute left-3 top-2.5 text-slate-500" />
                  <Input
                    type="password"
                    {...register('password')}
                    placeholder="••••••••••••"
                    className="pl-9 bg-slate-950/60 border-slate-800 text-white placeholder:text-slate-600 focus-visible:ring-primary"
                  />
                </div>
                {errors.password && (
                  <p className="text-[11px] text-rose-400">{errors.password.message}</p>
                )}
              </div>

              <Button
                type="submit"
                disabled={isLoading}
                className="w-full mt-2 bg-primary hover:bg-primary/90 text-primary-foreground font-semibold shadow-md"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    กำลังตรวจสอบ...
                  </>
                ) : (
                  'เข้าสู่ระบบ (Sign In)'
                )}
              </Button>
            </form>

            <div className="mt-6 pt-4 border-t border-slate-800/80 text-center">
              <p className="text-[11px] text-slate-500 font-mono">
                Default Super Admin: <span className="text-slate-400 font-semibold">admin</span> / <span className="text-slate-400 font-semibold">AdminPassword@123</span>
              </p>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};
