import React from 'react';
import { Layers, ArrowRightLeft, HardDrive, CheckCircle2 } from 'lucide-react';
import { ProgressUpdateDto } from '../../../types/console';
import { Badge } from '../../../components/ui/Badge';

interface ChunkProgressBarProps {
  progress: ProgressUpdateDto | null;
}

export const ChunkProgressBar: React.FC<ChunkProgressBarProps> = ({ progress }) => {
  if (!progress) {
    return (
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4 flex items-center justify-between text-slate-500 text-sm">
        <div className="flex items-center gap-2">
          <ArrowRightLeft className="w-4 h-4 text-slate-600" />
          <span>No active file chunk transfer in progress</span>
        </div>
        <span className="text-xs bg-slate-800/60 px-2.5 py-1 rounded text-slate-500">Idle</span>
      </div>
    );
  }

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  const percentageClamped = Math.min(100, Math.max(0, progress.percentage));
  const isComplete = percentageClamped >= 100 || progress.status === 'COMPLETED';

  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/70 p-4 space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center">
            {isComplete ? (
              <CheckCircle2 className="w-4 h-4" />
            ) : (
              <ArrowRightLeft className="w-4 h-4 animate-pulse" />
            )}
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-sm font-semibold text-white">
                {progress.stepName || 'File Chunk Transfer'}
              </span>
              <Badge variant={isComplete ? 'success' : 'info'}>
                {progress.status || (isComplete ? 'COMPLETED' : 'IN_PROGRESS')}
              </Badge>
            </div>
            <p className="text-xs text-slate-400 flex items-center gap-2 mt-0.5">
              <span className="flex items-center gap-1">
                <Layers className="w-3 h-3 text-slate-500" />
                Chunk {progress.currentChunk || 1} of {progress.totalChunks || 1}
              </span>
              <span>•</span>
              <span className="flex items-center gap-1">
                <HardDrive className="w-3 h-3 text-slate-500" />
                {formatBytes(progress.bytesTransferred)} / {formatBytes(progress.totalBytes)}
              </span>
            </p>
          </div>
        </div>

        <div className="text-right">
          <div className="text-lg font-bold font-mono text-emerald-400">
            {percentageClamped}%
          </div>
          <span className="text-[11px] text-slate-400">Transfer Progress</span>
        </div>
      </div>

      {/* Progress Track */}
      <div className="w-full bg-slate-950 rounded-full h-3.5 p-0.5 overflow-hidden border border-slate-800/80">
        <div
          className="bg-gradient-to-r from-teal-500 via-emerald-500 to-cyan-400 h-full rounded-full transition-all duration-300 ease-out shadow-[0_0_12px_rgba(16,185,129,0.35)]"
          style={{ width: `${percentageClamped}%` }}
        />
      </div>
    </div>
  );
};
