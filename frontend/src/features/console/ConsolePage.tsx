import React, { useState, useEffect, useCallback } from 'react';
import {
  Terminal as TerminalIcon,
  Square,
  RefreshCw,
} from 'lucide-react';
import { usePipelineWebSocket } from '../../hooks/usePipelineWebSocket';
import { LiveTerminal } from './components/LiveTerminal';
import { ChunkProgressBar } from './components/ChunkProgressBar';
import { ExecutionHistoryTable } from './components/ExecutionHistoryTable';
import { ExecutionDetailsModal } from '../pipelines/components/ExecutionDetailsModal';
import { PipelineExecutionResponse } from '../../types/pipeline';
import { LogMessageDto } from '../../types/console';
import { pipelineApi } from '../../services/pipelineApi';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Card, CardContent } from '../../components/ui/Card';

export const ConsolePage: React.FC = () => {
  const [executions, setExecutions] = useState<PipelineExecutionResponse[]>([]);
  const [selectedExecution, setSelectedExecution] = useState<PipelineExecutionResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [modalExecution, setModalExecution] = useState<PipelineExecutionResponse | null>(null);
  const [isDetailsModalOpen, setIsDetailsModalOpen] = useState(false);

  // Real-time WebSocket hook for logs and chunk progress
  const { isConnected, logs, progress, setLogs, clearLogs, resetProgress } =
    usePipelineWebSocket(selectedExecution?.id);

  const fetchExecutions = useCallback(async () => {
    try {
      setIsLoading(true);
      const data = await pipelineApi.listAllExecutions();
      setExecutions(data);

      // If no execution is selected yet, default to first running or most recent execution
      if (!selectedExecution && data.length > 0) {
        const running = data.find((e) => e.status === 'RUNNING');
        setSelectedExecution(running || data[0]);
      } else if (selectedExecution) {
        // Keep updated state for selected execution
        const updated = data.find((e) => e.id === selectedExecution.id);
        if (updated) {
          setSelectedExecution(updated);
        }
      }
    } catch (err) {
      console.error('Failed to fetch execution history', err);
    } finally {
      setIsLoading(false);
    }
  }, [selectedExecution]);

  useEffect(() => {
    fetchExecutions();
  }, []);

  // When selected execution changes, prefill initial logs from saved stepLogs
  useEffect(() => {
    if (!selectedExecution) {
      clearLogs();
      resetProgress();
      return;
    }

    const initialLogs: LogMessageDto[] = [];
    if (selectedExecution.stepLogs && selectedExecution.stepLogs.length > 0) {
      selectedExecution.stepLogs.forEach((step) => {
        if (step.logsText) {
          const lines = step.logsText.split('\n').filter((l) => l.trim().length > 0);
          lines.forEach((line) => {
            let level: 'INFO' | 'WARN' | 'ERROR' | 'DEBUG' = 'INFO';
            if (line.includes('[WARN]')) level = 'WARN';
            else if (line.includes('[ERROR]') || step.status === 'FAILED') level = 'ERROR';
            else if (line.includes('[DEBUG]')) level = 'DEBUG';

            initialLogs.push({
              executionId: selectedExecution.id,
              stepName: step.stepName,
              level,
              message: line,
              timestamp: step.startTime || new Date().toISOString(),
            });
          });
        }
      });
    }

    setLogs(initialLogs);
    resetProgress();
  }, [selectedExecution?.id]);

  const handleSelectExecution = (exec: PipelineExecutionResponse) => {
    setSelectedExecution(exec);
  };

  const handleViewDetails = (exec: PipelineExecutionResponse) => {
    setModalExecution(exec);
    setIsDetailsModalOpen(true);
  };

  const handleAbort = async () => {
    if (!selectedExecution) return;
    if (!window.confirm(`Are you sure you want to forcibly abort execution ${selectedExecution.id}?`)) {
      return;
    }

    try {
      await pipelineApi.abortExecution(selectedExecution.id);
      await fetchExecutions();
    } catch (err: any) {
      alert(`Failed to abort execution: ${err.message}`);
    }
  };

  const isExecutionRunning = selectedExecution?.status === 'RUNNING' || selectedExecution?.status === 'PENDING';

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <TerminalIcon className="w-6 h-6 text-primary" />
            Live Console & Real-time Stream
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            Real-time terminal execution logging over WebSocket, split chunk transfer progress, and full execution history.
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          <Button
            variant="outline"
            size="sm"
            onClick={fetchExecutions}
            disabled={isLoading}
            className="gap-2 border-slate-800 text-slate-400 hover:text-white"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </Button>
        </div>
      </div>

      {/* Selected Execution Banner */}
      {selectedExecution ? (
        <Card className="border-slate-800 bg-slate-900/80 shadow-md">
          <CardContent className="p-4 flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div className="flex flex-wrap items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-primary/10 border border-primary/20 text-primary flex items-center justify-center font-bold">
                <TerminalIcon className="w-5 h-5" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="text-base font-semibold text-white">
                    {selectedExecution.pipelineName}
                  </h2>
                  <Badge
                    variant={
                      selectedExecution.status === 'SUCCESS'
                        ? 'success'
                        : selectedExecution.status === 'FAILED'
                        ? 'destructive'
                        : selectedExecution.status === 'RUNNING'
                        ? 'warning'
                        : 'outline'
                    }
                  >
                    {selectedExecution.status}
                  </Badge>
                </div>
                <p className="text-xs text-slate-400 flex items-center gap-2 mt-0.5 font-mono">
                  <span>Execution ID: {selectedExecution.id}</span>
                  <span>•</span>
                  <span>Triggered by: {selectedExecution.triggeredBy}</span>
                  <span>•</span>
                  <span>Started: {new Date(selectedExecution.startTime).toLocaleTimeString()}</span>
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2 shrink-0">
              {isExecutionRunning && (
                <Button
                  size="sm"
                  variant="destructive"
                  onClick={handleAbort}
                  className="gap-1.5 text-xs py-1 h-8"
                >
                  <Square className="w-3.5 h-3.5" />
                  <span>Abort Execution</span>
                </Button>
              )}

              <Button
                size="sm"
                variant="outline"
                onClick={() => handleViewDetails(selectedExecution)}
                className="gap-1.5 text-xs py-1 h-8 border-slate-700 text-slate-300 hover:text-white"
              >
                <span>View Full Details</span>
              </Button>
            </div>
          </CardContent>
        </Card>
      ) : (
        <Card className="border-slate-800 bg-slate-900/40 p-6 text-center">
          <CardContent className="space-y-1">
            <p className="text-sm text-slate-300 font-medium">No Execution Selected</p>
            <p className="text-xs text-slate-500">
              Select an execution from the table below to inspect live WebSocket logs and transfer status.
            </p>
          </CardContent>
        </Card>
      )}

      {/* Chunk Progress Bar Component (TASK-702) */}
      <ChunkProgressBar progress={progress} />

      {/* Live Terminal Console Component (TASK-702) */}
      <LiveTerminal
        logs={logs}
        isConnected={isConnected}
        executionId={selectedExecution?.id}
        pipelineName={selectedExecution?.pipelineName}
        onClearLogs={clearLogs}
      />

      {/* Execution History Table (TASK-703) */}
      <div className="space-y-3 pt-4">
        <div>
          <h2 className="text-lg font-semibold text-white">Execution History & Audit</h2>
          <p className="text-xs text-slate-400">
            Browse all past and current pipeline executions. Click "Console" to switch live stream.
          </p>
        </div>

        <ExecutionHistoryTable
          executions={executions}
          isLoading={isLoading}
          selectedExecutionId={selectedExecution?.id}
          onSelectExecution={handleSelectExecution}
          onViewDetails={handleViewDetails}
          onRefresh={fetchExecutions}
        />
      </div>

      {/* Drill-down Details Modal */}
      <ExecutionDetailsModal
        isOpen={isDetailsModalOpen}
        onClose={() => setIsDetailsModalOpen(false)}
        execution={modalExecution}
        onExecutionUpdated={fetchExecutions}
      />
    </div>
  );
};

