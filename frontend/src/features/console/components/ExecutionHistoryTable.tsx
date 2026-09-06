import React, { useState } from 'react';
import {
  CheckCircle2,
  AlertCircle,
  Clock,
  Square,
  Search,
  RefreshCw,
  Terminal,
  Eye,
  Activity,
  Layers,
} from 'lucide-react';
import { PipelineExecutionResponse, ExecutionStatus } from '../../../types/pipeline';
import { Button } from '../../../components/ui/Button';
import { Badge } from '../../../components/ui/Badge';
import { Card, CardContent } from '../../../components/ui/Card';

interface ExecutionHistoryTableProps {
  executions: PipelineExecutionResponse[];
  isLoading: boolean;
  selectedExecutionId?: string | null;
  onSelectExecution: (execution: PipelineExecutionResponse) => void;
  onViewDetails: (execution: PipelineExecutionResponse) => void;
  onRefresh: () => void;
}

export const ExecutionHistoryTable: React.FC<ExecutionHistoryTableProps> = ({
  executions,
  isLoading,
  selectedExecutionId,
  onSelectExecution,
  onViewDetails,
  onRefresh,
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');

  const filteredExecutions = executions.filter((exec) => {
    const matchesSearch =
      !searchTerm ||
      exec.pipelineName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      exec.triggeredBy?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      exec.id.toLowerCase().includes(searchTerm.toLowerCase());

    const matchesStatus = statusFilter === 'ALL' || exec.status === statusFilter;

    return matchesSearch && matchesStatus;
  });

  const getStatusBadge = (status: ExecutionStatus) => {
    switch (status) {
      case 'SUCCESS':
        return (
          <Badge variant="success" className="gap-1">
            <CheckCircle2 className="w-3 h-3" />
            SUCCESS
          </Badge>
        );
      case 'RUNNING':
        return (
          <Badge variant="warning" className="gap-1 animate-pulse">
            <Clock className="w-3 h-3" />
            RUNNING
          </Badge>
        );
      case 'FAILED':
        return (
          <Badge variant="destructive" className="gap-1">
            <AlertCircle className="w-3 h-3" />
            FAILED
          </Badge>
        );
      case 'ABORTED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-md text-xs font-semibold bg-slate-800 text-slate-400 border border-slate-700">
            <Square className="w-3 h-3" />
            ABORTED
          </span>
        );
      default:
        return <Badge variant="outline">{status}</Badge>;
    }
  };

  const formatDuration = (ms?: number) => {
    if (!ms && ms !== 0) return '-';
    if (ms < 1000) return `${ms}ms`;
    const sec = (ms / 1000).toFixed(1);
    return `${sec}s`;
  };

  return (
    <div className="space-y-4">
      {/* Search & Filter Header */}
      <Card className="border-slate-800 bg-slate-900/60 shadow-sm">
        <CardContent className="p-3.5 flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="flex flex-1 items-center gap-3 w-full sm:w-auto">
            {/* Search Input */}
            <div className="relative flex-1 max-w-sm">
              <Search className="w-4 h-4 absolute left-3 top-2.5 text-slate-500" />
              <input
                type="text"
                placeholder="Filter by pipeline name, user, or ID..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full pl-9 pr-3 py-1.5 bg-slate-950 border border-slate-800 rounded-md text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
              />
            </div>

            {/* Status Filter Buttons */}
            <div className="hidden md:flex items-center bg-slate-950 border border-slate-800 rounded-md p-0.5 text-xs">
              {['ALL', 'SUCCESS', 'RUNNING', 'FAILED', 'ABORTED'].map((st) => (
                <button
                  key={st}
                  type="button"
                  onClick={() => setStatusFilter(st)}
                  className={`px-2.5 py-1 rounded transition-colors ${
                    statusFilter === st
                      ? 'bg-primary text-white font-medium shadow-sm'
                      : 'text-slate-400 hover:text-white'
                  }`}
                >
                  {st}
                </button>
              ))}
            </div>
          </div>

          <Button
            variant="outline"
            size="sm"
            onClick={onRefresh}
            disabled={isLoading}
            className="gap-2 border-slate-800 text-slate-400 hover:text-white shrink-0"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </Button>
        </CardContent>
      </Card>

      {/* Execution Table */}
      <Card className="border-slate-800 bg-slate-900/40 overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="bg-slate-900/90 text-xs font-semibold uppercase text-slate-400 border-b border-slate-800">
              <tr>
                <th className="px-4 py-3">Pipeline</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Triggered By</th>
                <th className="px-4 py-3">Start Time</th>
                <th className="px-4 py-3">Duration</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 font-sans">
              {isLoading && executions.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-4 py-10 text-center text-slate-500">
                    <Activity className="w-6 h-6 animate-spin mx-auto mb-2 text-primary" />
                    Loading execution history...
                  </td>
                </tr>
              ) : filteredExecutions.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-4 py-10 text-center text-slate-500">
                    <Layers className="w-8 h-8 opacity-40 mx-auto mb-2" />
                    No pipeline executions found.
                  </td>
                </tr>
              ) : (
                filteredExecutions.map((exec) => {
                  const isSelected = selectedExecutionId === exec.id;
                  return (
                    <tr
                      key={exec.id}
                      className={`hover:bg-slate-800/40 transition-colors ${
                        isSelected ? 'bg-primary/10 border-l-2 border-l-primary' : ''
                      }`}
                    >
                      {/* Pipeline Name */}
                      <td className="px-4 py-3">
                        <div className="font-medium text-white flex items-center gap-2">
                          <span>{exec.pipelineName || 'Pipeline'}</span>
                          {isSelected && (
                            <span className="text-[10px] bg-primary/20 text-primary px-1.5 py-0.5 rounded font-mono font-semibold">
                              ACTIVE CONSOLE
                            </span>
                          )}
                        </div>
                        <div className="text-xs text-slate-500 font-mono">
                          ID: {exec.id.substring(0, 13)}...
                        </div>
                      </td>

                      {/* Status */}
                      <td className="px-4 py-3">{getStatusBadge(exec.status)}</td>

                      {/* Triggered By */}
                      <td className="px-4 py-3">
                        <div className="text-slate-300 font-medium">
                          {exec.triggeredBy || 'system'}
                        </div>
                        <span className="text-[11px] text-slate-500 font-mono">
                          {exec.triggerType}
                        </span>
                      </td>

                      {/* Start Time */}
                      <td className="px-4 py-3 whitespace-nowrap text-xs text-slate-300">
                        {new Date(exec.startTime).toLocaleString()}
                      </td>

                      {/* Duration */}
                      <td className="px-4 py-3 whitespace-nowrap text-xs font-mono text-slate-300">
                        {formatDuration(exec.durationMs)}
                      </td>

                      {/* Actions */}
                      <td className="px-4 py-3 text-right whitespace-nowrap space-x-2">
                        <Button
                          size="sm"
                          variant={isSelected ? 'default' : 'outline'}
                          onClick={() => onSelectExecution(exec)}
                          className={`gap-1.5 text-xs py-1 h-8 ${
                            isSelected
                              ? 'bg-primary text-white shadow-sm'
                              : 'border-slate-700 text-slate-300 hover:text-white hover:bg-slate-800'
                          }`}
                          title="Stream Live Console"
                        >
                          <Terminal className="w-3.5 h-3.5" />
                          <span>Console</span>
                        </Button>

                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => onViewDetails(exec)}
                          className="gap-1.5 text-xs py-1 h-8 border-slate-700 text-slate-400 hover:text-white hover:bg-slate-800"
                          title="View Execution Details"
                        >
                          <Eye className="w-3.5 h-3.5" />
                          <span>Details</span>
                        </Button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};
