import React, { useState, useEffect } from 'react';
import {
  Layers,
  Workflow,
  Clock,
  History,
  Play,
  Pause,
  Trash2,
  Edit3,
  CheckCircle2,
  AlertCircle,
  Square,
  RefreshCw,
  Plus,
} from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { PipelineCanvas } from './components/PipelineCanvas';
import { ExecutionDetailsModal } from './components/ExecutionDetailsModal';
import {
  PipelineDetailResponse,
  PipelineExecutionResponse,
} from '../../types/pipeline';
import { pipelineApi } from '../../services/pipelineApi';

export const PipelinesPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'builder' | 'pipelines' | 'history'>('builder');
  const [pipelines, setPipelines] = useState<PipelineDetailResponse[]>([]);
  const [selectedPipeline, setSelectedPipeline] = useState<PipelineDetailResponse | null>(null);
  const [executions, setExecutions] = useState<PipelineExecutionResponse[]>([]);
  const [selectedExecution, setSelectedExecution] = useState<PipelineExecutionResponse | null>(null);
  const [isDetailsModalOpen, setIsDetailsModalOpen] = useState(false);
  const [loading, setLoading] = useState(false);

  const fetchPipelines = async () => {
    try {
      setLoading(true);
      const data = await pipelineApi.listPipelines();
      setPipelines(data);
      if (data.length > 0 && !selectedPipeline) {
        setSelectedPipeline(data[0]);
      }
    } catch (err) {
      console.error('Failed to load pipelines', err);
    } finally {
      setLoading(false);
    }
  };

  const fetchHistory = async () => {
    try {
      setLoading(true);
      if (selectedPipeline) {
        const data = await pipelineApi.listExecutions(selectedPipeline.id);
        setExecutions(data);
      } else if (pipelines.length > 0) {
        const data = await pipelineApi.listExecutions(pipelines[0].id);
        setExecutions(data);
      }
    } catch (err) {
      console.error('Failed to load execution history', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPipelines();
  }, []);

  useEffect(() => {
    if (activeTab === 'history') {
      fetchHistory();
    }
  }, [activeTab, selectedPipeline]);

  const handleCreateNew = () => {
    setSelectedPipeline(null);
    setActiveTab('builder');
  };

  const handleEditPipeline = (pipe: PipelineDetailResponse) => {
    setSelectedPipeline(pipe);
    setActiveTab('builder');
  };

  const handleDeletePipeline = async (id: string) => {
    if (!window.confirm('Are you sure you want to delete this pipeline?')) return;
    try {
      await pipelineApi.deletePipeline(id);
      await fetchPipelines();
      if (selectedPipeline?.id === id) {
        setSelectedPipeline(null);
      }
    } catch (err: any) {
      alert(`Failed to delete pipeline: ${err.message}`);
    }
  };

  const handleTogglePause = async (pipe: PipelineDetailResponse) => {
    try {
      if (pipe.isActive) {
        await pipelineApi.pausePipeline(pipe.id);
      } else {
        await pipelineApi.resumePipeline(pipe.id);
      }
      await fetchPipelines();
    } catch (err: any) {
      alert(`Failed to toggle pipeline schedule: ${err.message}`);
    }
  };

  const handleTriggerNow = async (id: string) => {
    try {
      const res = await pipelineApi.triggerPipeline(id);
      alert(`Pipeline triggered successfully! Execution ID: ${res.id}`);
      setActiveTab('history');
      await fetchHistory();
    } catch (err: any) {
      alert(`Failed to trigger pipeline: ${err.message}`);
    }
  };

  const handleViewExecution = async (execId: string) => {
    try {
      const details = await pipelineApi.getExecution(execId);
      setSelectedExecution(details);
      setIsDetailsModalOpen(true);
    } catch (err: any) {
      alert(`Failed to fetch execution details: ${err.message}`);
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'SUCCESS':
        return (
          <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            <CheckCircle2 className="w-3 h-3" />
            SUCCESS
          </span>
        );
      case 'RUNNING':
        return (
          <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium bg-amber-500/10 text-amber-400 border border-amber-500/20 animate-pulse">
            <Clock className="w-3 h-3" />
            RUNNING
          </span>
        );
      case 'FAILED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium bg-red-500/10 text-red-400 border border-red-500/20">
            <AlertCircle className="w-3 h-3" />
            FAILED
          </span>
        );
      case 'ABORTED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium bg-slate-500/10 text-slate-400 border border-slate-500/20">
            <Square className="w-3 h-3" />
            ABORTED
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-slate-500/10 text-slate-400">
            {status}
          </span>
        );
    }
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <Layers className="w-6 h-6 text-primary" />
            Pipeline Orchestration & Scheduler
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            การร้อยเรียงขั้นตอนงาน (DAG Visual Workflow Builder), การเชื่อมโยง Success/Failure, และการตั้งเวลา Quartz Cron
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          <Button
            onClick={handleCreateNew}
            className="gap-2 bg-primary hover:bg-primary/90 text-xs font-semibold shadow-lg shadow-primary/25"
          >
            <Plus className="w-4 h-4" />
            New Pipeline
          </Button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-slate-800 gap-2">
        <button
          onClick={() => setActiveTab('builder')}
          className={`flex items-center gap-2 pb-3 px-3 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'builder'
              ? 'border-primary text-primary'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <Workflow className="w-4 h-4" />
          Visual Workflow Builder
        </button>

        <button
          onClick={() => {
            setActiveTab('pipelines');
            fetchPipelines();
          }}
          className={`flex items-center gap-2 pb-3 px-3 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'pipelines'
              ? 'border-primary text-primary'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <Clock className="w-4 h-4" />
          Configured Schedules ({pipelines.length})
        </button>

        <button
          onClick={() => setActiveTab('history')}
          className={`flex items-center gap-2 pb-3 px-3 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'history'
              ? 'border-primary text-primary'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <History className="w-4 h-4" />
          Execution History
        </button>
      </div>

      {/* Tab 1: Visual Builder */}
      {activeTab === 'builder' && (
        <div className="space-y-4">
          <PipelineCanvas
            key={selectedPipeline?.id || 'new'}
            initialPipeline={selectedPipeline}
            onSaved={(saved) => {
              setSelectedPipeline(saved);
              fetchPipelines();
            }}
            onTriggered={() => {
              setActiveTab('history');
              fetchHistory();
            }}
          />
        </div>
      )}

      {/* Tab 2: Configured Schedules List */}
      {activeTab === 'pipelines' && (
        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <h2 className="text-sm font-semibold text-slate-300">
              Active & Configured Pipelines
            </h2>
            <Button
              variant="outline"
              size="sm"
              onClick={fetchPipelines}
              disabled={loading}
              className="gap-1.5 text-xs text-slate-300"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
              Refresh
            </Button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {pipelines.map((pipe) => (
              <Card
                key={pipe.id}
                className="border-slate-800 bg-slate-900/70 hover:border-slate-700 transition-all p-5 flex flex-col justify-between shadow-xl"
              >
                <div className="space-y-3">
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <h3 className="text-sm font-bold text-white tracking-tight">
                        {pipe.name}
                      </h3>
                      <p className="text-xs text-slate-400 mt-0.5 line-clamp-2">
                        {pipe.description || 'No description provided'}
                      </p>
                    </div>
                    <span
                      className={`px-2 py-0.5 rounded text-[10px] font-semibold uppercase ${
                        pipe.isActive
                          ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                          : 'bg-slate-800 text-slate-400 border border-slate-700'
                      }`}
                    >
                      {pipe.isActive ? 'Active' : 'Paused'}
                    </span>
                  </div>

                  <div className="space-y-1.5 pt-2 border-t border-slate-800 text-xs">
                    <div className="flex justify-between text-slate-400">
                      <span>Cron:</span>
                      <span className="font-mono text-slate-200">{pipe.cronExpression || 'Manual Only'}</span>
                    </div>
                    <div className="flex justify-between text-slate-400">
                      <span>Timezone:</span>
                      <span className="text-slate-300">{pipe.timezone}</span>
                    </div>
                    <div className="flex justify-between text-slate-400">
                      <span>Next Run:</span>
                      <span className="text-primary font-mono text-[11px]">
                        {pipe.nextFireTime ? new Date(pipe.nextFireTime).toLocaleString() : 'N/A'}
                      </span>
                    </div>
                    <div className="flex justify-between text-slate-400">
                      <span>Steps:</span>
                      <span className="text-slate-200">{pipe.nodes?.length || 0} nodes</span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center justify-between pt-4 mt-4 border-t border-slate-800/80 gap-2">
                  <div className="flex items-center gap-1.5">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleTriggerNow(pipe.id)}
                      className="h-8 px-2.5 text-xs border-emerald-500/30 text-emerald-400 hover:bg-emerald-500/10"
                      title="Trigger Now"
                    >
                      <Play className="w-3.5 h-3.5" />
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleTogglePause(pipe)}
                      className="h-8 px-2.5 text-xs text-slate-400 hover:text-white"
                      title={pipe.isActive ? 'Pause Schedule' : 'Resume Schedule'}
                    >
                      <Pause className="w-3.5 h-3.5" />
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleEditPipeline(pipe)}
                      className="h-8 px-2.5 text-xs text-slate-400 hover:text-white"
                      title="Edit in Visual Builder"
                    >
                      <Edit3 className="w-3.5 h-3.5" />
                    </Button>
                  </div>

                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => handleDeletePipeline(pipe.id)}
                    className="h-8 px-2.5 text-xs border-red-500/20 text-red-400 hover:bg-red-500/10"
                    title="Delete Pipeline"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </Button>
                </div>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* Tab 3: Execution History */}
      {activeTab === 'history' && (
        <Card className="border-slate-800 bg-slate-900/80 shadow-xl overflow-hidden">
          <div className="p-4 border-b border-slate-800 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <h2 className="text-sm font-semibold text-white">Execution History</h2>
              {pipelines.length > 0 && (
                <select
                  value={selectedPipeline?.id || ''}
                  onChange={(e) => {
                    const found = pipelines.find((p) => p.id === e.target.value);
                    if (found) setSelectedPipeline(found);
                  }}
                  className="bg-slate-950 border border-slate-800 rounded-lg px-2.5 py-1 text-xs text-white focus:outline-none"
                >
                  {pipelines.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
              )}
            </div>

            <Button
              variant="outline"
              size="sm"
              onClick={fetchHistory}
              disabled={loading}
              className="gap-1.5 text-xs text-slate-300"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
              Refresh
            </Button>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs text-slate-300">
              <thead className="bg-slate-950 text-slate-400 uppercase text-[10px] tracking-wider border-b border-slate-800">
                <tr>
                  <th className="px-5 py-3">Pipeline</th>
                  <th className="px-5 py-3">Status</th>
                  <th className="px-5 py-3">Triggered By</th>
                  <th className="px-5 py-3">Started At</th>
                  <th className="px-5 py-3">Duration</th>
                  <th className="px-5 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {executions.length > 0 ? (
                  executions.map((exec) => (
                    <tr key={exec.id} className="hover:bg-slate-800/30 transition-colors">
                      <td className="px-5 py-3.5 font-medium text-white">
                        {exec.pipelineName}
                      </td>
                      <td className="px-5 py-3.5">{getStatusBadge(exec.status)}</td>
                      <td className="px-5 py-3.5 text-slate-400">
                        {exec.triggeredBy} ({exec.triggerType})
                      </td>
                      <td className="px-5 py-3.5 font-mono text-slate-400">
                        {new Date(exec.startTime).toLocaleString()}
                      </td>
                      <td className="px-5 py-3.5 font-mono text-slate-300">
                        {exec.durationMs != null ? `${(exec.durationMs / 1000).toFixed(2)}s` : 'Running...'}
                      </td>
                      <td className="px-5 py-3.5 text-right">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleViewExecution(exec.id)}
                          className="text-xs text-slate-300 hover:text-white"
                        >
                          View Logs
                        </Button>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={6} className="text-center py-10 text-slate-500">
                      No executions recorded for this pipeline yet.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* Execution Details Modal */}
      <ExecutionDetailsModal
        isOpen={isDetailsModalOpen}
        onClose={() => setIsDetailsModalOpen(false)}
        execution={selectedExecution}
        onExecutionUpdated={fetchHistory}
      />
    </div>
  );
};
