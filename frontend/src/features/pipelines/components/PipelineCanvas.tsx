import React, { useState, useCallback, useMemo } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  addEdge,
  useNodesState,
  useEdgesState,
  Connection,
  Edge,
  Node,
  MarkerType,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import {
  Database,
  Archive,
  Send,
  Mail,
  Plus,
  Save,
  Play,
  Clock,
  CheckCircle2,
  AlertTriangle,
} from 'lucide-react';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { DatabaseBackupNode } from './nodes/DatabaseBackupNode';
import { FileBackupNode } from './nodes/FileBackupNode';
import { TransferNode } from './nodes/TransferNode';
import { NotificationNode } from './nodes/NotificationNode';
import { NodeConfigModal } from './NodeConfigModal';
import {
  MisfirePolicy,
  PipelineDetailResponse,
  SavePipelineRequest,
  StepNodeDto,
  TaskType,
} from '../../../types/pipeline';
import { pipelineApi } from '../../../services/pipelineApi';

interface PipelineCanvasProps {
  initialPipeline?: PipelineDetailResponse | null;
  onSaved?: (pipeline: PipelineDetailResponse) => void;
  onTriggered?: (executionId: string) => void;
}

const nodeTypes = {
  DATABASE_BACKUP: DatabaseBackupNode,
  FILE_BACKUP: FileBackupNode,
  SPLIT_TRANSFER: TransferNode,
  EMAIL_ALERT: NotificationNode,
};

export const PipelineCanvas: React.FC<PipelineCanvasProps> = ({
  initialPipeline,
  onSaved,
  onTriggered,
}) => {
  const [pipelineName, setPipelineName] = useState(initialPipeline?.name || 'Automated Backup & Transfer Flow');
  const [description, setDescription] = useState(initialPipeline?.description || 'Daily database dump, tar compression, and SFTP transfer');
  const [cronExpression, setCronExpression] = useState(initialPipeline?.cronExpression || '0 0 2 * * ?');
  const [timezone, setTimezone] = useState(initialPipeline?.timezone || 'UTC');
  const [misfirePolicy, setMisfirePolicy] = useState<MisfirePolicy>(initialPipeline?.misfirePolicy || 'SMART_POLICY');
  const [isActive, setIsActive] = useState(initialPipeline?.isActive ?? true);
  const [isSaving, setIsSaving] = useState(false);
  const [statusMessage, setStatusMessage] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

  // Modal State
  const [editingNode, setEditingNode] = useState<{
    id: string;
    nodeKey: string;
    nodeLabel: string;
    nodeType: TaskType;
    configJson: string;
  } | null>(null);

  // Initial nodes setup
  const initialNodes: Node[] = useMemo(() => {
    if (initialPipeline && initialPipeline.nodes && initialPipeline.nodes.length > 0) {
      return initialPipeline.nodes.map((n) => ({
        id: n.id || n.nodeKey,
        type: n.nodeType,
        position: { x: n.positionX || 150, y: n.positionY || 100 },
        data: {
          label: n.nodeLabel,
          nodeKey: n.nodeKey,
          ...JSON.parse(n.configOverrideJson || '{}'),
          onEdit: () => {
            setEditingNode({
              id: n.id || n.nodeKey,
              nodeKey: n.nodeKey,
              nodeLabel: n.nodeLabel,
              nodeType: n.nodeType,
              configJson: n.configOverrideJson || '{}',
            });
          },
        },
      }));
    }

    // Default template nodes
    return [
      {
        id: 'node-db-dump',
        type: 'DATABASE_BACKUP',
        position: { x: 100, y: 80 },
        data: {
          label: 'Dump PostgreSQL DB',
          nodeKey: 'db_dump',
          databaseName: 'production_db',
          host: 'localhost',
        },
      },
      {
        id: 'node-transfer',
        type: 'SPLIT_TRANSFER',
        position: { x: 100, y: 260 },
        data: {
          label: 'SFTP Chunk Transfer',
          nodeKey: 'sftp_upload',
          protocol: 'SFTP',
          remoteDirectory: '/backups/db',
        },
      },
      {
        id: 'node-notify',
        type: 'EMAIL_ALERT',
        position: { x: 100, y: 440 },
        data: {
          label: 'SES Success Email',
          nodeKey: 'alert_success',
          recipient: 'devops@company.com',
        },
      },
    ];
  }, [initialPipeline]);

  // Initial edges setup
  const initialEdges: Edge[] = useMemo(() => {
    if (initialPipeline && initialPipeline.nodes) {
      const edges: Edge[] = [];
      for (const n of initialPipeline.nodes) {
        const sourceId = n.id || n.nodeKey;
        if (n.onSuccessNodeId) {
          edges.push({
            id: `edge-${sourceId}-${n.onSuccessNodeId}-success`,
            source: sourceId,
            target: n.onSuccessNodeId,
            label: 'On Success',
            style: { stroke: '#10b981', strokeWidth: 2 },
            markerEnd: { type: MarkerType.ArrowClosed, color: '#10b981' },
          });
        }
        if (n.onFailureNodeId) {
          edges.push({
            id: `edge-${sourceId}-${n.onFailureNodeId}-failure`,
            source: sourceId,
            target: n.onFailureNodeId,
            label: 'On Failure',
            style: { stroke: '#ef4444', strokeWidth: 2, strokeDasharray: '4 4' },
            markerEnd: { type: MarkerType.ArrowClosed, color: '#ef4444' },
          });
        }
      }
      return edges;
    }

    return [
      {
        id: 'edge-1-2',
        source: 'node-db-dump',
        target: 'node-transfer',
        label: 'On Success',
        style: { stroke: '#10b981', strokeWidth: 2 },
        markerEnd: { type: MarkerType.ArrowClosed, color: '#10b981' },
      },
      {
        id: 'edge-2-3',
        source: 'node-transfer',
        target: 'node-notify',
        label: 'On Success',
        style: { stroke: '#10b981', strokeWidth: 2 },
        markerEnd: { type: MarkerType.ArrowClosed, color: '#10b981' },
      },
    ];
  }, [initialPipeline]);

  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);

  // Attach onEdit listeners to nodes
  const nodesWithHandlers = useMemo(() => {
    return nodes.map((n) => ({
      ...n,
      data: {
        ...n.data,
        onEdit: () => {
          setEditingNode({
            id: n.id,
            nodeKey: (n.data.nodeKey as string) || n.id,
            nodeLabel: (n.data.label as string) || 'Step',
            nodeType: (n.type as TaskType) || 'DATABASE_BACKUP',
            configJson: JSON.stringify(n.data),
          });
        },
      },
    }));
  }, [nodes]);

  const onConnect = useCallback(
    (connection: Connection) => {
      const isFailure = window.confirm(
        'Connect as "On Success" branch (green)?\n\nClick OK for "On Success", or Cancel for "On Failure" (red)!'
      );

      const newEdge: Edge = {
        ...connection,
        id: `edge-${connection.source}-${connection.target}-${isFailure ? 'success' : 'failure'}-${Date.now()}`,
        label: isFailure ? 'On Success' : 'On Failure',
        style: {
          stroke: isFailure ? '#10b981' : '#ef4444',
          strokeWidth: 2,
          strokeDasharray: isFailure ? undefined : '4 4',
        },
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: isFailure ? '#10b981' : '#ef4444',
        },
      };
      setEdges((eds) => addEdge(newEdge, eds));
    },
    [setEdges]
  );

  const addStepNode = (type: TaskType) => {
    const key = `step_${Date.now().toString().slice(-4)}`;
    const labelMap: Record<TaskType, string> = {
      DATABASE_BACKUP: 'Database Dump',
      FILE_BACKUP: 'Directory Archive',
      SPLIT_TRANSFER: 'Split & SFTP Upload',
      EMAIL_ALERT: 'SES Notification',
    };

    const newNode: Node = {
      id: `node-${Date.now()}`,
      type,
      position: { x: 180 + Math.random() * 40, y: 120 + nodes.length * 90 },
      data: {
        label: labelMap[type],
        nodeKey: key,
      },
    };

    setNodes((nds) => [...nds, newNode]);
  };

  const handleUpdateNodeConfig = (updatedLabel: string, updatedConfigJson: string) => {
    if (!editingNode) return;
    try {
      const parsedConfig = JSON.parse(updatedConfigJson);
      setNodes((nds) =>
        nds.map((n) => {
          if (n.id === editingNode.id) {
            return {
              ...n,
              data: {
                ...n.data,
                ...parsedConfig,
                label: updatedLabel,
              },
            };
          }
          return n;
        })
      );
    } catch (err) {
      console.error('Failed to parse updated config', err);
    }
  };

  const handleSavePipeline = async () => {
    try {
      setIsSaving(true);
      setStatusMessage(null);

      // Map nodes and edges into StepNodeDto[]
      const stepDtos: StepNodeDto[] = nodes.map((node, index) => {
        // Find outgoing edges
        const successEdge = edges.find(
          (e) => e.source === node.id && (e.label === 'On Success' || !e.label)
        );
        const failureEdge = edges.find(
          (e) => e.source === node.id && e.label === 'On Failure'
        );

        // Extract pure config without internal callbacks
        const dataCopy = { ...node.data };
        delete dataCopy.onEdit;

        return {
          id: node.id.includes('-') && node.id.length === 36 ? node.id : undefined,
          nodeKey: (node.data.nodeKey as string) || `step_${index + 1}`,
          nodeLabel: (node.data.label as string) || `Step ${index + 1}`,
          nodeType: (node.type as TaskType) || 'DATABASE_BACKUP',
          stepOrder: index + 1,
          positionX: node.position.x,
          positionY: node.position.y,
          configOverrideJson: JSON.stringify(dataCopy),
          onSuccessNodeId: successEdge?.target,
          onFailureNodeId: failureEdge?.target,
        };
      });

      const payload: SavePipelineRequest = {
        id: initialPipeline?.id,
        name: pipelineName,
        description,
        cronExpression,
        timezone,
        misfirePolicy,
        isActive,
        nodes: stepDtos,
      };

      let result: PipelineDetailResponse;
      if (initialPipeline?.id) {
        result = await pipelineApi.updatePipeline(initialPipeline.id, payload);
      } else {
        result = await pipelineApi.createPipeline(payload);
      }

      setStatusMessage({ text: 'Pipeline workflow and Quartz schedule saved successfully!', type: 'success' });
      if (onSaved) {
        onSaved(result);
      }
    } catch (err: any) {
      const errorMsg = err.response?.data?.message || err.message || 'Failed to save pipeline';
      setStatusMessage({ text: `Error: ${errorMsg}`, type: 'error' });
    } finally {
      setIsSaving(false);
    }
  };

  const handleTriggerPipeline = async () => {
    if (!initialPipeline?.id) {
      alert('Please save the pipeline first before triggering a run.');
      return;
    }

    try {
      const res = await pipelineApi.triggerPipeline(initialPipeline.id);
      setStatusMessage({ text: `Pipeline triggered! Execution ID: ${res.id}`, type: 'success' });
      if (onTriggered) {
        onTriggered(res.id);
      }
    } catch (err: any) {
      const errorMsg = err.response?.data?.message || err.message || 'Failed to trigger pipeline';
      setStatusMessage({ text: `Trigger failed: ${errorMsg}`, type: 'error' });
    }
  };

  return (
    <div className="space-y-4">
      {/* Configuration Header Card */}
      <Card className="border-slate-800 bg-slate-900/90 p-5 shadow-xl">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          <div className="space-y-1.5 flex-1">
            <input
              type="text"
              value={pipelineName}
              onChange={(e) => setPipelineName(e.target.value)}
              className="text-lg font-bold text-white bg-transparent border-b border-transparent hover:border-slate-700 focus:border-primary focus:outline-none px-1 w-full max-w-lg transition-colors"
              placeholder="Pipeline Name"
            />
            <input
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="text-xs text-slate-400 bg-transparent border-b border-transparent hover:border-slate-700 focus:border-primary focus:outline-none px-1 w-full max-w-xl transition-colors"
              placeholder="Pipeline description..."
            />
          </div>

          <div className="flex items-center gap-3 flex-wrap">
            <div className="flex items-center gap-2 bg-slate-950 px-3 py-1.5 rounded-lg border border-slate-800 text-xs">
              <Clock className="w-3.5 h-3.5 text-primary" />
              <span className="text-slate-400">Cron:</span>
              <input
                type="text"
                value={cronExpression}
                onChange={(e) => setCronExpression(e.target.value)}
                className="bg-transparent font-mono text-white text-xs w-28 focus:outline-none"
                placeholder="0 0 2 * * ?"
              />
            </div>

            <div className="flex items-center gap-2 bg-slate-950 px-3 py-1.5 rounded-lg border border-slate-800 text-xs">
              <label className="text-slate-400">Timezone:</label>
              <select
                value={timezone}
                onChange={(e) => setTimezone(e.target.value)}
                className="bg-transparent text-white text-xs focus:outline-none"
              >
                <option value="UTC" className="bg-slate-900">UTC</option>
                <option value="Asia/Bangkok" className="bg-slate-900">Asia/Bangkok (+07:00)</option>
                <option value="America/New_York" className="bg-slate-900">America/New_York</option>
              </select>
            </div>

            <div className="flex items-center gap-2 bg-slate-950 px-3 py-1.5 rounded-lg border border-slate-800 text-xs">
              <label className="text-slate-400">Misfire:</label>
              <select
                value={misfirePolicy}
                onChange={(e) => setMisfirePolicy(e.target.value as MisfirePolicy)}
                className="bg-transparent text-white text-xs focus:outline-none"
              >
                <option value="SMART_POLICY" className="bg-slate-900">Smart Policy</option>
                <option value="FIRE_NOW" className="bg-slate-900">Fire Now</option>
                <option value="IGNORE" className="bg-slate-900">Ignore</option>
                <option value="DO_NOTHING" className="bg-slate-900">Do Nothing</option>
              </select>
            </div>

            <label className="flex items-center gap-2 text-xs text-slate-300 cursor-pointer bg-slate-950 px-3 py-2 rounded-lg border border-slate-800">
              <input
                type="checkbox"
                checked={isActive}
                onChange={(e) => setIsActive(e.target.checked)}
                className="rounded border-slate-700 bg-slate-900 text-primary focus:ring-0"
              />
              <span>Active</span>
            </label>

            <Button
              onClick={handleSavePipeline}
              disabled={isSaving}
              className="gap-2 bg-primary hover:bg-primary/90 text-xs font-semibold shadow-lg shadow-primary/20"
            >
              <Save className="w-3.5 h-3.5" />
              {isSaving ? 'Saving...' : 'Save Pipeline'}
            </Button>

            {initialPipeline?.id && (
              <Button
                onClick={handleTriggerPipeline}
                variant="outline"
                className="gap-2 border-emerald-500/30 text-emerald-400 hover:bg-emerald-500/10 text-xs font-semibold"
              >
                <Play className="w-3.5 h-3.5" />
                Trigger Run
              </Button>
            )}
          </div>
        </div>

        {statusMessage && (
          <div
            className={`mt-4 p-3 rounded-lg border text-xs flex items-center gap-2 ${
              statusMessage.type === 'success'
                ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400'
                : 'bg-red-500/10 border-red-500/20 text-red-400'
            }`}
          >
            {statusMessage.type === 'success' ? (
              <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
            ) : (
              <AlertTriangle className="w-4 h-4 flex-shrink-0" />
            )}
            <span>{statusMessage.text}</span>
          </div>
        )}
      </Card>

      {/* React Flow Visual Canvas */}
      <div className="h-[600px] w-full rounded-2xl border border-slate-800 bg-slate-950 relative overflow-hidden shadow-2xl">
        {/* Step Palette Toolbar */}
        <div className="absolute top-4 left-4 z-10 flex items-center gap-2 bg-slate-900/90 backdrop-blur-md p-2 rounded-xl border border-slate-800 shadow-xl">
          <span className="text-xs font-semibold text-slate-400 px-2 flex items-center gap-1.5">
            <Plus className="w-3.5 h-3.5 text-primary" /> Add Step:
          </span>
          <button
            onClick={() => addStepNode('DATABASE_BACKUP')}
            className="flex items-center gap-1.5 text-xs px-2.5 py-1.5 rounded-lg bg-blue-500/10 border border-blue-500/20 text-blue-400 hover:bg-blue-500/20 transition-all font-medium"
          >
            <Database className="w-3.5 h-3.5" />
            DB Backup
          </button>
          <button
            onClick={() => addStepNode('FILE_BACKUP')}
            className="flex items-center gap-1.5 text-xs px-2.5 py-1.5 rounded-lg bg-amber-500/10 border border-amber-500/20 text-amber-400 hover:bg-amber-500/20 transition-all font-medium"
          >
            <Archive className="w-3.5 h-3.5" />
            File Backup
          </button>
          <button
            onClick={() => addStepNode('SPLIT_TRANSFER')}
            className="flex items-center gap-1.5 text-xs px-2.5 py-1.5 rounded-lg bg-purple-500/10 border border-purple-500/20 text-purple-400 hover:bg-purple-500/20 transition-all font-medium"
          >
            <Send className="w-3.5 h-3.5" />
            Split & Transfer
          </button>
          <button
            onClick={() => addStepNode('EMAIL_ALERT')}
            className="flex items-center gap-1.5 text-xs px-2.5 py-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 hover:bg-emerald-500/20 transition-all font-medium"
          >
            <Mail className="w-3.5 h-3.5" />
            Email Alert
          </button>
        </div>

        {/* Legend */}
        <div className="absolute bottom-4 left-4 z-10 flex items-center gap-4 bg-slate-900/90 backdrop-blur-md px-3 py-2 rounded-xl border border-slate-800 shadow-xl text-[11px] text-slate-400">
          <div className="flex items-center gap-1.5">
            <span className="w-3 h-0.5 bg-emerald-400 rounded-full inline-block" />
            <span>On Success</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-3 h-0.5 border-b-2 border-dashed border-red-400 inline-block" />
            <span>On Failure (Rollback/Alert)</span>
          </div>
        </div>

        <ReactFlow
          nodes={nodesWithHandlers}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          nodeTypes={nodeTypes}
          fitView
          fitViewOptions={{ padding: 0.3 }}
          className="bg-slate-950"
        >
          <Background color="#334155" gap={20} size={1} />
          <Controls className="!bg-slate-900 !border-slate-800 !rounded-xl !shadow-xl [&>button]:!bg-slate-800 [&>button]:!border-slate-700 [&>button]:!text-slate-200" />
          <MiniMap
            className="!bg-slate-900/90 !border-slate-800 !rounded-xl overflow-hidden shadow-xl"
            nodeColor="#3b82f6"
            maskColor="rgba(15, 23, 42, 0.7)"
          />
        </ReactFlow>
      </div>

      {/* Node Config Modal */}
      {editingNode && (
        <NodeConfigModal
          isOpen={!!editingNode}
          onClose={() => setEditingNode(null)}
          nodeKey={editingNode.nodeKey}
          nodeLabel={editingNode.nodeLabel}
          nodeType={editingNode.nodeType}
          configJson={editingNode.configJson}
          onSave={handleUpdateNodeConfig}
        />
      )}
    </div>
  );
};
