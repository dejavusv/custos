import React from 'react';
import {
  X,
  CheckCircle2,
  AlertCircle,
  Clock,
  Square,
  FileCheck,
  Hash,
  Terminal,
  User,
} from 'lucide-react';
import { Button } from '../../../components/ui/Button';
import { PipelineExecutionResponse } from '../../../types/pipeline';
import { pipelineApi } from '../../../services/pipelineApi';

interface ExecutionDetailsModalProps {
  isOpen: boolean;
  onClose: () => void;
  execution: PipelineExecutionResponse | null;
  onExecutionUpdated?: () => void;
}

export const ExecutionDetailsModal: React.FC<ExecutionDetailsModalProps> = ({
  isOpen,
  onClose,
  execution,
  onExecutionUpdated,
}) => {
  if (!isOpen || !execution) return null;

  const handleAbort = async () => {
    if (!window.confirm('Are you sure you want to forcibly abort this execution?')) {
      return;
    }
    try {
      await pipelineApi.abortExecution(execution.id);
      if (onExecutionUpdated) {
        onExecutionUpdated();
      }
      onClose();
    } catch (err: any) {
      alert(`Failed to abort execution: ${err.message}`);
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'SUCCESS':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            <CheckCircle2 className="w-3.5 h-3.5" />
            SUCCESS
          </span>
        );
      case 'RUNNING':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-amber-500/10 text-amber-400 border border-amber-500/20 animate-pulse">
            <Clock className="w-3.5 h-3.5" />
            RUNNING
          </span>
        );
      case 'FAILED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-red-500/10 text-red-400 border border-red-500/20">
            <AlertCircle className="w-3.5 h-3.5" />
            FAILED
          </span>
        );
      case 'ABORTED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-slate-500/10 text-slate-400 border border-slate-500/20">
            <Square className="w-3.5 h-3.5" />
            ABORTED
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-slate-500/10 text-slate-400 border border-slate-500/20">
            {status}
          </span>
        );
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 animate-in fade-in duration-150">
      <div className="bg-slate-900 border border-slate-800 w-full max-w-3xl rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[85vh]">
        {/* Header */}
        <div className="px-6 py-4 border-b border-slate-800 flex items-center justify-between bg-slate-900/90">
          <div>
            <div className="flex items-center gap-3">
              <h2 className="text-base font-bold text-white">
                Execution: {execution.pipelineName}
              </h2>
              {getStatusBadge(execution.status)}
            </div>
            <p className="text-xs font-mono text-slate-400 mt-0.5">
              ID: {execution.id}
            </p>
          </div>
          <div className="flex items-center gap-2">
            {(execution.status === 'RUNNING' || execution.status === 'PENDING') && (
              <Button
                variant="outline"
                size="sm"
                onClick={handleAbort}
                className="border-red-500/30 text-red-400 hover:bg-red-500/10 text-xs"
              >
                <Square className="w-3.5 h-3.5 mr-1" />
                Abort Run
              </Button>
            )}
            <button
              onClick={onClose}
              className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Execution Summary Stats */}
        <div className="grid grid-cols-4 gap-4 p-4 border-b border-slate-800/80 bg-slate-950/40 text-xs">
          <div>
            <span className="text-slate-500 block">Triggered By</span>
            <span className="text-slate-200 font-medium flex items-center gap-1 mt-0.5">
              <User className="w-3.5 h-3.5 text-primary" />
              {execution.triggeredBy} ({execution.triggerType})
            </span>
          </div>
          <div>
            <span className="text-slate-500 block">Started At</span>
            <span className="text-slate-200 font-mono mt-0.5 block">
              {new Date(execution.startTime).toLocaleTimeString()}
            </span>
          </div>
          <div>
            <span className="text-slate-500 block">Duration</span>
            <span className="text-slate-200 font-medium mt-0.5 block">
              {execution.durationMs != null ? `${(execution.durationMs / 1000).toFixed(2)}s` : 'In progress...'}
            </span>
          </div>
          <div>
            <span className="text-slate-500 block">Total Steps</span>
            <span className="text-slate-200 font-medium mt-0.5 block">
              {execution.stepLogs?.length || 0} executed
            </span>
          </div>
        </div>

        {execution.errorMessage && (
          <div className="p-4 mx-6 mt-4 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-xs flex items-start gap-2.5">
            <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
            <div>
              <div className="font-semibold">Execution Failure Notice:</div>
              <div className="mt-0.5 font-mono">{execution.errorMessage}</div>
            </div>
          </div>
        )}

        {/* Step by step logs */}
        <div className="p-6 space-y-4 overflow-y-auto flex-1">
          <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
            Step Execution Details & Artifacts
          </h3>

          <div className="space-y-3">
            {execution.stepLogs && execution.stepLogs.length > 0 ? (
              execution.stepLogs.map((log, idx) => (
                <div
                  key={log.id || idx}
                  className="p-4 rounded-xl border border-slate-800 bg-slate-950/60 space-y-2 text-xs"
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="w-5 h-5 rounded-full bg-slate-800 text-slate-400 text-[10px] font-mono flex items-center justify-center">
                        {idx + 1}
                      </span>
                      <span className="font-semibold text-white">
                        {log.stepName}
                      </span>
                    </div>
                    <div className="flex items-center gap-2">
                      {getStatusBadge(log.status)}
                      <span className="text-slate-500 font-mono">
                        {log.durationMs != null ? `${log.durationMs}ms` : ''}
                      </span>
                    </div>
                  </div>

                  {/* Artifact Badges */}
                  {(log.outputPath || log.checksumSha256 || log.fileSizeBytes) && (
                    <div className="flex flex-wrap gap-2 pt-1">
                      {log.outputPath && (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-slate-900 border border-slate-800 text-[11px] text-slate-300 font-mono">
                          <FileCheck className="w-3 h-3 text-emerald-400" />
                          {log.outputPath}
                        </span>
                      )}
                      {log.checksumSha256 && (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-slate-900 border border-slate-800 text-[11px] text-slate-300 font-mono">
                          <Hash className="w-3 h-3 text-blue-400" />
                          {log.checksumSha256.slice(0, 16)}...
                        </span>
                      )}
                      {log.fileSizeBytes != null && log.fileSizeBytes > 0 && (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-slate-900 border border-slate-800 text-[11px] text-slate-400 font-mono">
                          {(log.fileSizeBytes / (1024 * 1024)).toFixed(2)} MB
                        </span>
                      )}
                    </div>
                  )}

                  {/* Log text */}
                  {log.logsText && (
                    <div className="mt-2 p-2.5 rounded-lg bg-slate-900 border border-slate-800/80 font-mono text-[11px] text-slate-400 whitespace-pre-wrap flex items-start gap-1.5">
                      <Terminal className="w-3.5 h-3.5 text-slate-500 mt-0.5 flex-shrink-0" />
                      <div>{log.logsText}</div>
                    </div>
                  )}

                  {log.errorMessage && (
                    <div className="mt-1 p-2 rounded-lg bg-red-500/10 border border-red-500/20 font-mono text-[11px] text-red-400">
                      Error: {log.errorMessage}
                    </div>
                  )}
                </div>
              ))
            ) : (
              <div className="text-center py-6 text-slate-500 text-xs">
                No step execution logs recorded yet.
              </div>
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="px-6 py-3 border-t border-slate-800 bg-slate-900/80 flex items-center justify-end">
          <Button variant="outline" size="sm" onClick={onClose}>
            Close
          </Button>
        </div>
      </div>
    </div>
  );
};
