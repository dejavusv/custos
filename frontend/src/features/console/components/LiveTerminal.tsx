import React, { useState, useEffect, useRef } from 'react';
import {
  Terminal as TerminalIcon,
  Search,
  Copy,
  Trash2,
  Check,
  Radio,
  ArrowDownToLine,
} from 'lucide-react';
import { LogMessageDto, LogLevel } from '../../../types/console';

interface LiveTerminalProps {
  logs: LogMessageDto[];
  isConnected: boolean;
  executionId?: string | null;
  pipelineName?: string;
  onClearLogs?: () => void;
}

export const LiveTerminal: React.FC<LiveTerminalProps> = ({
  logs,
  isConnected,
  executionId,
  pipelineName,
  onClearLogs,
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedLevel, setSelectedLevel] = useState<string>('ALL');
  const [autoScroll, setAutoScroll] = useState(true);
  const [copied, setCopied] = useState(false);
  const terminalEndRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (autoScroll && terminalEndRef.current) {
      terminalEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs, autoScroll]);

  const filteredLogs = logs.filter((log) => {
    const matchesSearch =
      !searchTerm ||
      log.message.toLowerCase().includes(searchTerm.toLowerCase()) ||
      log.stepName.toLowerCase().includes(searchTerm.toLowerCase());

    const matchesLevel = selectedLevel === 'ALL' || log.level === selectedLevel;

    return matchesSearch && matchesLevel;
  });

  const handleCopyLogs = () => {
    const text = filteredLogs
      .map(
        (l) =>
          `[${new Date(l.timestamp).toLocaleTimeString()}] [${l.level}] [${l.stepName}]: ${l.message}`
      )
      .join('\n');
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const getLevelColor = (level: LogLevel) => {
    switch (level) {
      case 'INFO':
        return 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20';
      case 'WARN':
        return 'text-amber-400 bg-amber-500/10 border-amber-500/20';
      case 'ERROR':
        return 'text-rose-400 bg-rose-500/10 border-rose-500/20 font-semibold';
      case 'DEBUG':
        return 'text-purple-400 bg-purple-500/10 border-purple-500/20';
      default:
        return 'text-slate-400 bg-slate-800 border-slate-700';
    }
  };

  return (
    <div className="rounded-xl border border-slate-800 bg-[#0c1017] shadow-2xl overflow-hidden flex flex-col h-[520px]">
      {/* Terminal Title Bar */}
      <div className="flex flex-wrap items-center justify-between px-4 py-2.5 bg-slate-900/90 border-b border-slate-800/80 gap-3">
        {/* Left: Window Dots & Title */}
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-1.5">
            <span className="w-3 h-3 rounded-full bg-rose-500/80 inline-block" />
            <span className="w-3 h-3 rounded-full bg-amber-500/80 inline-block" />
            <span className="w-3 h-3 rounded-full bg-emerald-500/80 inline-block" />
          </div>

          <div className="flex items-center gap-2 text-xs font-mono text-slate-300">
            <TerminalIcon className="w-3.5 h-3.5 text-primary" />
            <span className="font-semibold text-white">
              {pipelineName ? `${pipelineName}` : 'custos@console'}
            </span>
            {executionId && (
              <span className="text-slate-500 hidden sm:inline">
                ({executionId.substring(0, 8)}...)
              </span>
            )}
          </div>
        </div>

        {/* Right: Live Connection Indicator & Action Buttons */}
        <div className="flex items-center gap-2">
          <div
            className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border ${
              isConnected
                ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                : 'bg-slate-800 text-slate-400 border-slate-700'
            }`}
          >
            <Radio className={`w-3 h-3 ${isConnected ? 'animate-pulse text-emerald-400' : 'text-slate-500'}`} />
            <span>{isConnected ? 'LIVE STREAM' : 'OFFLINE'}</span>
          </div>

          <button
            type="button"
            onClick={handleCopyLogs}
            disabled={filteredLogs.length === 0}
            className="p-1.5 text-slate-400 hover:text-white bg-slate-800/80 hover:bg-slate-800 border border-slate-700/60 rounded-md transition-colors disabled:opacity-40"
            title="Copy Logs"
          >
            {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
          </button>

          {onClearLogs && (
            <button
              type="button"
              onClick={onClearLogs}
              disabled={logs.length === 0}
              className="p-1.5 text-slate-400 hover:text-rose-400 bg-slate-800/80 hover:bg-slate-800 border border-slate-700/60 rounded-md transition-colors disabled:opacity-40"
              title="Clear Terminal Logs"
            >
              <Trash2 className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </div>

      {/* Terminal Toolbar: Search, Level Filter, Auto-scroll */}
      <div className="flex flex-wrap items-center justify-between px-4 py-2 bg-slate-900/40 border-b border-slate-800/60 gap-2.5 text-xs">
        {/* Search */}
        <div className="relative flex-1 min-w-[200px] max-w-sm">
          <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-500" />
          <input
            type="text"
            placeholder="Search terminal logs..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-8 pr-3 py-1 bg-slate-950/80 border border-slate-800 rounded-md text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
          />
        </div>

        {/* Level Filters & Auto-scroll */}
        <div className="flex items-center gap-2">
          <div className="flex items-center bg-slate-950/80 border border-slate-800 rounded-md p-0.5">
            {['ALL', 'INFO', 'WARN', 'ERROR', 'DEBUG'].map((level) => (
              <button
                key={level}
                type="button"
                onClick={() => setSelectedLevel(level)}
                className={`px-2 py-0.5 text-[11px] font-medium rounded transition-colors ${
                  selectedLevel === level
                    ? 'bg-primary text-white shadow-sm'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                {level}
              </button>
            ))}
          </div>

          <label className="flex items-center gap-1.5 cursor-pointer text-slate-400 hover:text-white select-none ml-1">
            <input
              type="checkbox"
              checked={autoScroll}
              onChange={(e) => setAutoScroll(e.target.checked)}
              className="rounded border-slate-700 bg-slate-950 text-primary focus:ring-primary w-3.5 h-3.5"
            />
            <span className="flex items-center gap-1 text-[11px]">
              <ArrowDownToLine className="w-3 h-3" />
              Auto-scroll
            </span>
          </label>
        </div>
      </div>

      {/* Terminal Output Area */}
      <div className="flex-1 p-4 font-mono text-xs overflow-y-auto space-y-1 select-text scrollbar-thin scrollbar-thumb-slate-800 scrollbar-track-transparent">
        {filteredLogs.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center text-slate-600 space-y-2">
            <TerminalIcon className="w-8 h-8 opacity-40" />
            <p className="font-mono text-xs">
              {logs.length === 0
                ? isConnected
                  ? 'Connected to live console. Awaiting pipeline step logs...'
                  : 'Select an execution or trigger a pipeline to inspect live stream.'
                : 'No logs match the current search or level filter.'}
            </p>
          </div>
        ) : (
          filteredLogs.map((log, index) => (
            <div
              key={`${log.timestamp}-${index}`}
              className="flex items-start gap-2 hover:bg-slate-900/50 px-1 py-0.5 rounded transition-colors"
            >
              {/* Timestamp */}
              <span className="text-slate-500 whitespace-nowrap text-[11px]">
                {new Date(log.timestamp).toLocaleTimeString()}
              </span>

              {/* Log Level Badge */}
              <span
                className={`px-1.5 py-0.2 rounded text-[10px] font-bold border whitespace-nowrap ${getLevelColor(
                  log.level
                )}`}
              >
                {log.level}
              </span>

              {/* Step identifier */}
              {log.stepName && (
                <span className="text-sky-400/90 whitespace-nowrap text-[11px]">
                  [{log.stepName}]
                </span>
              )}

              {/* Message text */}
              <span className="text-slate-200 break-all leading-relaxed flex-1">
                {log.message}
              </span>
            </div>
          ))
        )}
        <div ref={terminalEndRef} />
      </div>
    </div>
  );
};
