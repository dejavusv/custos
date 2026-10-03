import React, { useEffect, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import axios from 'axios';
import * as z from 'zod';
import { Plug, Send, Loader2, AlertCircle, CheckCircle2, KeyRound } from 'lucide-react';
import { vaultApi } from '../../../services/vaultApi';
import { externalNotifyApi } from '../../../services/externalNotifyApi';
import { ExternalNotifySendResult, ExternalNotifyTask } from '../../../types/externalNotify';
import { Button } from '../../../components/ui/Button';

export interface ExternalNotifyValues {
  credentialId: string;
  taskId: string;
  message: string;
}

interface ExternalNotifyPanelProps {
  values: ExternalNotifyValues;
  /** field เพิ่มเติม taskTitle/credentialName ใช้แสดงผลบน node เท่านั้น */
  onChange: (field: keyof ExternalNotifyValues | 'taskTitle' | 'credentialName', value: string) => void;
  /** แสดงปุ่ม Call เพื่อส่งข้อความจริงทันที */
  showCall?: boolean;
  /** ใช้ตอนเปิดใน Pipeline: ข้อความที่มี ${...} จะถูก resolve ตอน pipeline รันเท่านั้น จึงปิดปุ่ม Call */
  allowPlaceholders?: boolean;
  enabled?: boolean;
}

const MAX_MESSAGE_CHARS = 5000;

const callSchema = z.object({
  credentialId: z.string().uuid('กรุณาเลือก Credential'),
  taskId: z.string().uuid('กรุณาเลือก Task'),
  message: z
    .string()
    .trim()
    .min(1, 'กรุณาระบุข้อความ')
    .max(MAX_MESSAGE_CHARS, `ข้อความต้องไม่เกิน ${MAX_MESSAGE_CHARS} ตัวอักษร`),
});

const extractErrorMessage = (err: unknown, fallback: string): string => {
  if (axios.isAxiosError<{ message?: string }>(err)) {
    return err.response?.data?.message || fallback;
  }
  return fallback;
};

const selectClass =
  'w-full text-sm p-2.5 rounded-md border border-input bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring disabled:opacity-50';

export const ExternalNotifyPanel: React.FC<ExternalNotifyPanelProps> = ({
  values,
  onChange,
  showCall = false,
  allowPlaceholders = false,
  enabled = true,
}) => {
  const [tasks, setTasks] = useState<ExternalNotifyTask[] | null>(null);
  const [connectError, setConnectError] = useState<string | null>(null);
  const [callError, setCallError] = useState<string | null>(null);
  const [callResult, setCallResult] = useState<ExternalNotifySendResult | null>(null);

  const { data: credentials, isLoading: credentialsLoading } = useQuery({
    queryKey: ['vault-credentials-all'],
    queryFn: async () => await vaultApi.getAllCredentials(),
    enabled,
  });

  const notifyCredentials = credentials?.filter((c) => c.credentialType === 'EXTERNAL_NOTIFY') ?? [];
  const selectedCredential = notifyCredentials.find((c) => c.id === values.credentialId);

  // เปลี่ยน Credential แล้วรายการ Task เดิมใช้ไม่ได้
  useEffect(() => {
    setTasks(null);
    setConnectError(null);
    setCallError(null);
    setCallResult(null);
  }, [values.credentialId]);

  const connectMutation = useMutation({
    mutationFn: () => externalNotifyApi.listTasks(values.credentialId),
    onMutate: () => setConnectError(null),
    onSuccess: (data) => setTasks(data),
    onError: (err: unknown) => {
      setTasks(null);
      setConnectError(extractErrorMessage(err, 'ไม่สามารถเชื่อมต่อ External Notify ได้'));
    },
  });

  const callMutation = useMutation({
    mutationFn: () => externalNotifyApi.send({ ...values, message: values.message.trim() }),
    onMutate: () => {
      setCallError(null);
      setCallResult(null);
    },
    onSuccess: (data) => setCallResult(data),
    onError: (err: unknown) => setCallError(extractErrorMessage(err, 'ส่งข้อความไม่สำเร็จ')),
  });

  const selectedTask = tasks?.find((t) => t.taskId === values.taskId);

  const handleCall = () => {
    const parsed = callSchema.safeParse(values);
    if (!parsed.success) {
      setCallResult(null);
      setCallError(parsed.error.issues[0].message);
      return;
    }
    const target = selectedTask
      ? `${selectedTask.title} (${selectedTask.recipients.length} ราย)`
      : 'Task ที่เลือก';
    if (!window.confirm(`ยืนยันการส่งข้อความไปยังลูกค้าทุกรายใน ${target} ?`)) return;
    callMutation.mutate();
  };

  const hasPlaceholder = allowPlaceholders && values.message.includes('${');

  return (
    <div className="space-y-4">
      {/* 1. Credential (Domain + Token จาก Vault) */}
      <div className="space-y-1.5">
        <label className="text-xs font-semibold text-foreground flex items-center gap-1.5">
          <KeyRound className="w-3.5 h-3.5 text-primary" />
          Domain และ Auth Token จาก Vault <span className="text-destructive">*</span>
        </label>
        <div className="flex flex-col sm:flex-row gap-2">
          <select
            className={selectClass}
            value={values.credentialId}
            onChange={(e) => {
              const cred = notifyCredentials.find((c) => c.id === e.target.value);
              onChange('credentialId', e.target.value);
              onChange('credentialName', cred?.name ?? '');
              onChange('taskId', '');
              onChange('taskTitle', '');
            }}
            disabled={credentialsLoading}
          >
            <option value="">-- เลือก Credential ชนิด External Notify --</option>
            {notifyCredentials.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name} ({c.host})
              </option>
            ))}
          </select>
          <Button
            type="button"
            variant="outline"
            onClick={() => connectMutation.mutate()}
            disabled={!values.credentialId || connectMutation.isPending}
            className="gap-2 shrink-0"
          >
            {connectMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plug className="w-4 h-4" />}
            Connect
          </Button>
        </div>
        {!credentialsLoading && notifyCredentials.length === 0 && (
          <p className="text-[11px] text-muted-foreground">
            ยังไม่มี Credential ชนิด External Notify — ให้ Super Admin สร้างที่หน้า Credentials Vault (ระบุ Domain และ EXTERNAL_NOTIFY_AUTH_TOKEN)
          </p>
        )}
        {selectedCredential && (
          <p className="text-[11px] text-muted-foreground font-mono">Domain: {selectedCredential.host}</p>
        )}
        {connectError && (
          <div className="p-2.5 text-xs text-destructive bg-destructive/10 border border-destructive/20 rounded-md flex items-start gap-2">
            <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
            {connectError}
          </div>
        )}
      </div>

      {/* 2. Task ที่เปิดรับการแจ้งเตือนจากภายนอก */}
      <div className="space-y-1.5">
        <label className="text-xs font-semibold text-foreground">
          Task ที่ต้องการเรียกใช้งาน <span className="text-destructive">*</span>
        </label>
        <select
          className={selectClass}
          value={values.taskId}
          onChange={(e) => {
            onChange('taskId', e.target.value);
            onChange('taskTitle', tasks?.find((t) => t.taskId === e.target.value)?.title ?? '');
          }}
          disabled={!values.credentialId}
        >
          <option value="">
            {tasks === null ? '-- กด Connect เพื่อดึงรายการ Task --' : '-- เลือก Task --'}
          </option>
          {values.taskId && !selectedTask && <option value={values.taskId}>{values.taskId} (ที่บันทึกไว้)</option>}
          {tasks?.map((t) => (
            <option key={t.taskId} value={t.taskId}>
              {t.title}
              {t.chatbotName ? ` — ${t.chatbotName}` : ''} ({t.recipients.length} ราย)
            </option>
          ))}
        </select>
        {tasks !== null && tasks.length === 0 && (
          <p className="text-[11px] text-amber-400">ยังไม่มี Task ที่เปิดรับการแจ้งเตือนจากภายนอก</p>
        )}
        {values.taskId && !selectedTask && tasks === null && (
          <p className="text-[11px] text-muted-foreground">กด Connect เพื่อดูรายละเอียดของ Task ที่บันทึกไว้</p>
        )}
        {selectedTask && (
          <div className="text-[11px] text-muted-foreground space-y-0.5 p-2.5 rounded-md bg-secondary/20 border border-border">
            <p>Chatbot: <span className="text-foreground">{selectedTask.chatbotName || selectedTask.chatbotId}</span></p>
            <p className="break-all">
              ผู้รับ ({selectedTask.recipients.length}):{' '}
              <span className="font-mono text-foreground">{selectedTask.recipients.join(', ') || '-'}</span>
            </p>
          </div>
        )}
      </div>

      {/* 3. Message */}
      <div className="space-y-1.5">
        <label className="text-xs font-semibold text-foreground">
          ข้อความที่จะส่งแจ้งเตือน (Message) <span className="text-destructive">*</span>
        </label>
        <textarea
          rows={4}
          maxLength={MAX_MESSAGE_CHARS}
          value={values.message}
          onChange={(e) => onChange('message', e.target.value)}
          placeholder={
            allowPlaceholders
              ? 'เช่น สำรองข้อมูลสำเร็จ: ${last_output_path}'
              : 'พิมพ์ข้อความที่ต้องการแจ้งเตือนลูกค้า...'
          }
          className="w-full text-sm p-2.5 rounded-md border border-input bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
        />
        {allowPlaceholders && (
          <p className="text-[11px] text-muted-foreground">
            ใช้ตัวแปรจากขั้นตอนก่อนหน้าได้ เช่น {'${last_output_path}'} {'${nodeKey.output_path}'}
            {' '}— บนกิ่ง On Failed ใช้ Error ของ Task ที่ล้มเหลวได้: {'${last_error_message}'} {'${nodeKey.error_message}'}
          </p>
        )}
      </div>

      {/* 4. Call */}
      {showCall && (
        <div className="space-y-2">
          <Button
            type="button"
            onClick={handleCall}
            disabled={callMutation.isPending || hasPlaceholder || !values.taskId || !values.message.trim()}
            className="gap-2"
          >
            {callMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
            Call (ส่งข้อความ)
          </Button>
          {hasPlaceholder && (
            <p className="text-[11px] text-amber-400">
              ข้อความมีตัวแปร ${'{...}'} ซึ่งจะถูกแทนที่ตอน Pipeline รันเท่านั้น จึงไม่สามารถทดสอบส่งจากที่นี่ได้
            </p>
          )}
          {callError && (
            <div className="p-2.5 text-xs text-destructive bg-destructive/10 border border-destructive/20 rounded-md flex items-start gap-2">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              {callError}
            </div>
          )}
          {callResult && (
            <div className="p-2.5 text-xs text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 rounded-md flex items-start gap-2">
              <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5" />
              <div>
                <p>
                  {callResult.message || 'ส่งข้อความสำเร็จ'} — ส่งแล้ว {callResult.sentCount} ราย
                </p>
                {callResult.failedRecipients.length > 0 && (
                  <p className="text-amber-400 break-all">
                    ส่งไม่สำเร็จ: <span className="font-mono">{callResult.failedRecipients.join(', ')}</span>
                  </p>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
