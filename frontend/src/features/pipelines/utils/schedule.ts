// แปลงระหว่างตัวเลือกกำหนดเวลาแบบอ่านง่าย (รายวัน / รายสัปดาห์) กับ Quartz cron (6 ฟิลด์) ที่ Backend เก็บ

export type ScheduleMode = 'MANUAL' | 'DAILY' | 'WEEKLY' | 'CUSTOM';

export type WeekDay = 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN';

export const WEEK_DAYS: { key: WeekDay; label: string }[] = [
  { key: 'MON', label: 'Mon' },
  { key: 'TUE', label: 'Tue' },
  { key: 'WED', label: 'Wed' },
  { key: 'THU', label: 'Thu' },
  { key: 'FRI', label: 'Fri' },
  { key: 'SAT', label: 'Sat' },
  { key: 'SUN', label: 'Sun' },
];

export interface ScheduleState {
  mode: ScheduleMode;
  time: string; // HH:mm
  days: WeekDay[];
  custom: string;
}

export const DEFAULT_TIME = '02:00';

const DAILY_RE = /^0 (\d{1,2}) (\d{1,2}) \* \* \?$/;
const WEEKLY_RE = /^0 (\d{1,2}) (\d{1,2}) \? \* ([A-Z]{3}(?:,[A-Z]{3})*)$/;
const DAY_KEYS = new Set<string>(WEEK_DAYS.map((d) => d.key));

const pad = (n: number) => String(n).padStart(2, '0');

const toTime = (minute: string, hour: string): string | null => {
  const m = Number(minute);
  const h = Number(hour);
  if (m < 0 || m > 59 || h < 0 || h > 23) return null;
  return `${pad(h)}:${pad(m)}`;
};

const sortDays = (days: WeekDay[]): WeekDay[] =>
  WEEK_DAYS.map((d) => d.key).filter((k) => days.includes(k));

export const emptySchedule = (): ScheduleState => ({
  mode: 'MANUAL',
  time: DEFAULT_TIME,
  days: [],
  custom: '',
});

// cron ที่ไม่ตรงรูปแบบรายวัน/รายสัปดาห์ของเรา จะถูกเปิดเป็น CUSTOM เพื่อไม่ให้ค่าเดิมสูญหาย
export const parseCron = (cron: string | null | undefined): ScheduleState => {
  const value = (cron ?? '').trim();
  const base = emptySchedule();
  if (!value) return base;

  const daily = DAILY_RE.exec(value);
  if (daily) {
    const time = toTime(daily[1], daily[2]);
    if (time) return { ...base, mode: 'DAILY', time };
  }

  const weekly = WEEKLY_RE.exec(value);
  if (weekly) {
    const time = toTime(weekly[1], weekly[2]);
    const days = weekly[3].split(',');
    if (time && days.every((d) => DAY_KEYS.has(d)) && new Set(days).size === days.length) {
      return { ...base, mode: 'WEEKLY', time, days: sortDays(days as WeekDay[]) };
    }
  }

  return { ...base, mode: 'CUSTOM', custom: value };
};

const cronTimeParts = (time: string): { hour: number; minute: number } | null => {
  const match = /^(\d{1,2}):(\d{2})$/.exec(time);
  if (!match) return null;
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  return hour > 23 || minute > 59 ? null : { hour, minute };
};

// คืน cron พร้อมข้อความผิดพลาด (ถ้ามี) — error != null แปลว่าห้ามบันทึก
export const buildCron = (state: ScheduleState): { cron: string; error: string | null } => {
  switch (state.mode) {
    case 'MANUAL':
      return { cron: '', error: null };
    case 'DAILY':
    case 'WEEKLY': {
      const t = cronTimeParts(state.time);
      if (!t) return { cron: '', error: 'Please enter a valid time' };
      if (state.mode === 'DAILY') {
        return { cron: `0 ${t.minute} ${t.hour} * * ?`, error: null };
      }
      if (state.days.length === 0) return { cron: '', error: 'Select at least one day of the week' };
      return { cron: `0 ${t.minute} ${t.hour} ? * ${sortDays(state.days).join(',')}`, error: null };
    }
    case 'CUSTOM': {
      const cron = state.custom.trim();
      const fields = cron.split(/\s+/).filter(Boolean).length;
      if (!cron) return { cron: '', error: 'Enter a Quartz cron expression' };
      if (fields < 6 || fields > 7) return { cron, error: 'Quartz cron needs 6 or 7 fields, e.g. 0 0 2 * * ?' };
      return { cron, error: null };
    }
  }
};

// ข้อความสรุปสำหรับแสดงในรายการ Pipeline
export const describeCron = (cron: string | null | undefined): string => {
  const state = parseCron(cron);
  switch (state.mode) {
    case 'MANUAL':
      return 'Manual only';
    case 'DAILY':
      return `Every day at ${state.time}`;
    case 'WEEKLY': {
      const names = WEEK_DAYS.filter((d) => state.days.includes(d.key)).map((d) => d.label);
      return `Every ${names.join(', ')} at ${state.time}`;
    }
    case 'CUSTOM':
      return state.custom;
  }
};
