import React, { useState } from 'react';
import { Clock } from 'lucide-react';
import {
  ScheduleMode,
  ScheduleState,
  WEEK_DAYS,
  WeekDay,
  buildCron,
  parseCron,
} from '../utils/schedule';

interface SchedulePickerProps {
  // cron (Quartz) ปัจจุบันของ Pipeline — ใช้เป็นค่าเริ่มต้นตอนเปิดเท่านั้น
  value: string | null | undefined;
  // error != null แปลว่าค่าที่เลือกยังไม่ครบ/ไม่ถูกต้อง (ใช้บล็อกการบันทึก)
  onChange: (cron: string, error: string | null) => void;
}

const MODE_LABEL: Record<ScheduleMode, string> = {
  MANUAL: 'Manual only',
  DAILY: 'Daily',
  WEEKLY: 'Weekly',
  CUSTOM: 'Custom cron',
};

export const SchedulePicker: React.FC<SchedulePickerProps> = ({ value, onChange }) => {
  const [state, setState] = useState<ScheduleState>(() => parseCron(value));
  const [error, setError] = useState<string | null>(null);

  const update = (patch: Partial<ScheduleState>) => {
    const next = { ...state, ...patch };
    const result = buildCron(next);
    setState(next);
    setError(result.error);
    onChange(result.cron, result.error);
  };

  const changeMode = (mode: ScheduleMode) => {
    // เปลี่ยนไป Custom: เริ่มจาก cron ที่สร้างไว้ตอนนี้ เพื่อให้แก้ต่อได้
    const custom = mode === 'CUSTOM' && !state.custom ? buildCron(state).cron : state.custom;
    update({ mode, custom });
  };

  const toggleDay = (day: WeekDay) => {
    const days = state.days.includes(day) ? state.days.filter((d) => d !== day) : [...state.days, day];
    update({ days });
  };

  return (
    <div className="space-y-1.5">
      <div className="flex items-center gap-2 flex-wrap bg-slate-950 px-3 py-1.5 rounded-lg border border-slate-800 text-xs">
        <Clock className="w-3.5 h-3.5 text-primary" />
        <label className="text-slate-400">Schedule:</label>
        <select
          value={state.mode}
          onChange={(e) => changeMode(e.target.value as ScheduleMode)}
          className="bg-transparent text-white text-xs focus:outline-none"
        >
          {(Object.keys(MODE_LABEL) as ScheduleMode[]).map((mode) => (
            <option key={mode} value={mode} className="bg-slate-900">
              {MODE_LABEL[mode]}
            </option>
          ))}
        </select>

        {(state.mode === 'DAILY' || state.mode === 'WEEKLY') && (
          <>
            {state.mode === 'WEEKLY' && (
              <div className="flex items-center gap-1" role="group" aria-label="Days of the week">
                {WEEK_DAYS.map((d) => {
                  const on = state.days.includes(d.key);
                  return (
                    <button
                      key={d.key}
                      type="button"
                      onClick={() => toggleDay(d.key)}
                      aria-pressed={on}
                      className={`px-2 py-1 rounded-md border text-[11px] font-medium transition-colors ${
                        on
                          ? 'bg-primary/20 border-primary/50 text-primary'
                          : 'bg-slate-900 border-slate-700 text-slate-400 hover:text-slate-200'
                      }`}
                    >
                      {d.label}
                    </button>
                  );
                })}
              </div>
            )}
            <span className="text-slate-400">at</span>
            <input
              type="time"
              value={state.time}
              onChange={(e) => update({ time: e.target.value })}
              className="bg-transparent text-white text-xs focus:outline-none [color-scheme:dark]"
            />
          </>
        )}

        {state.mode === 'CUSTOM' && (
          <input
            type="text"
            value={state.custom}
            onChange={(e) => update({ custom: e.target.value })}
            className="bg-transparent font-mono text-white text-xs w-40 focus:outline-none"
            placeholder="0 0 2 * * ?"
          />
        )}
      </div>
      {error && <p className="text-[11px] text-red-400 px-1">{error}</p>}
    </div>
  );
};
