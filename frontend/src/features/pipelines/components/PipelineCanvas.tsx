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
  CheckCircle2,
  AlertTriangle,
  HardDriveUpload,
  MessageSquare,
  Square,
  XCircle,
} from 'lucide-react';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '../../../components/ui/Dialog';
import { StartNode } from './nodes/StartNode';
import { StopNode } from './nodes/StopNode';
import { DatabaseBackupNode } from './nodes/DatabaseBackupNode';
import { FileBackupNode } from './nodes/FileBackupNode';
import { TransferNode } from './nodes/TransferNode';
import { NotificationNode } from './nodes/NotificationNode';
import { GoogleDriveNode } from './nodes/GoogleDriveNode';
import { LineNotifyNode } from './nodes/LineNotifyNode';
import { NodeConfigModal } from './NodeConfigModal';
import { SchedulePicker } from './SchedulePicker';
import { MoveNodesDialog } from './MoveNodesDialog';
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
  // Pipeline ทั้งหมด ใช้เป็นรายการปลายทางของการย้าย Task
  pipelines?: PipelineDetailResponse[];
  onSaved?: (pipeline: PipelineDetailResponse) => void;
  onMoved?: (source: PipelineDetailResponse, target: PipelineDetailResponse) => void;
  onTriggered?: (executionId: string) => void;
}

const nodeTypes = {
  DATABASE_BACKUP: DatabaseBackupNode,
  FILE_BACKUP: FileBackupNode,
  SPLIT_TRANSFER: TransferNode,
  EMAIL_ALERT: NotificationNode,
  GOOGLE_DRIVE_UPLOAD: GoogleDriveNode,
  LINE_NOTIFY: LineNotifyNode,
  START: StartNode,
  STOP: StopNode,
};

type EdgeKind = 'success' | 'failure';

const EDGE_LABEL: Record<EdgeKind, string> = {
  success: 'On Success',
  failure: 'On Failure',
};

// ตัวแปรที่ Backend เติมให้เมื่อ Task ล้มเหลว (ใช้ได้บนกิ่ง On Failure)
const errorMessagePlaceholder = (nodeKey: string) => '${' + nodeKey + '.error_message}';

const buildEdge = (connection: Connection, kind: EdgeKind): Edge => {
  const color = kind === 'success' ? '#10b981' : '#ef4444';
  return {
    ...connection,
    id: `edge-${connection.source}-${connection.target}-${kind}-${Date.now()}`,
    label: EDGE_LABEL[kind],
    style: {
      stroke: color,
      strokeWidth: 2,
      strokeDasharray: kind === 'failure' ? '4 4' : undefined,
    },
    markerEnd: { type: MarkerType.ArrowClosed, color },
  };
};

const LEGACY_START_ID = 'node-start';

// Node ต้นทางของ Pipeline เดิม: Node แรก (ตาม stepOrder) ที่ไม่มีเส้นใดชี้เข้า
const findRootNode = (nodes: StepNodeDto[]): StepNodeDto | undefined => {
  const targets = new Set<string>();
  for (const n of nodes) {
    if (n.onSuccessNodeId) targets.add(n.onSuccessNodeId);
    if (n.onFailureNodeId) targets.add(n.onFailureNodeId);
  }
  return nodes.find((n) => !targets.has(n.id || n.nodeKey)) ?? nodes[0];
};

export const PipelineCanvas: React.FC<PipelineCanvasProps> = ({
  initialPipeline,
  pipelines = [],
  onSaved,
  onMoved,
  onTriggered,
}) => {
  const [pipelineName, setPipelineName] = useState(initialPipeline?.name ?? '');
  const [nameTouched, setNameTouched] = useState(false);
  const [description, setDescription] = useState(initialPipeline?.description || 'Daily database dump, tar compression, and SFTP transfer');
  // Pipeline ที่มีอยู่แล้วใช้ค่าเดิม (รวมถึง Manual = ว่าง) ส่วน Pipeline ใหม่เริ่มที่ทุกวัน 02:00
  const [cronExpression, setCronExpression] = useState(initialPipeline ? initialPipeline.cronExpression ?? '' : '0 0 2 * * ?');
  const [scheduleError, setScheduleError] = useState<string | null>(null);
  const [createdId, setCreatedId] = useState<string | null>(null);
  const [timezone, setTimezone] = useState(initialPipeline?.timezone || 'UTC');
  const [misfirePolicy, setMisfirePolicy] = useState<MisfirePolicy>(initialPipeline?.misfirePolicy || 'SMART_POLICY');
  const [isActive, setIsActive] = useState(initialPipeline?.isActive ?? true);
  const [isSaving, setIsSaving] = useState(false);
  const [statusMessage, setStatusMessage] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

  // เส้นเชื่อมที่รอผู้ใช้เลือกชนิด (On Success / On Failed)
  const [pendingConnection, setPendingConnection] = useState<Connection | null>(null);

  // หน้าต่างย้าย Task ไป Pipeline อื่น
  const [moveDialog, setMoveDialog] = useState<{ nodeIds: string[]; startExcluded: boolean } | null>(null);
  const [isMoving, setIsMoving] = useState(false);
  const [moveError, setMoveError] = useState<string | null>(null);

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
      const loaded: Node[] = initialPipeline.nodes.map((n) => ({
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

      // Pipeline เดิมที่ยังไม่มี Start: เติมให้อัตโนมัติ เหนือ Node ต้นทาง (บันทึกเมื่อผู้ใช้กด Save)
      if (!initialPipeline.nodes.some((n) => n.nodeType === 'START')) {
        const rootNode = findRootNode(initialPipeline.nodes);
        if (rootNode) {
          loaded.unshift({
            id: LEGACY_START_ID,
            type: 'START',
            position: { x: rootNode.positionX || 150, y: (rootNode.positionY || 100) - 120 },
            data: { label: 'Start', nodeKey: 'start' },
          });
        }
      }
      return loaded;
    }

    // Default template nodes
    return [
      {
        id: 'node-start',
        type: 'START',
        position: { x: 100, y: -40 },
        data: { label: 'Start', nodeKey: 'start' },
      },
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
      {
        id: 'node-stop',
        type: 'STOP',
        position: { x: 100, y: 600 },
        data: { label: 'Stop', nodeKey: 'stop' },
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
      if (!initialPipeline.nodes.some((n) => n.nodeType === 'START')) {
        const rootNode = findRootNode(initialPipeline.nodes);
        if (rootNode) {
          edges.unshift(
            buildEdge({ source: LEGACY_START_ID, target: rootNode.id || rootNode.nodeKey, sourceHandle: null, targetHandle: null }, 'success')
          );
        }
      }
      return edges;
    }

    return [
      {
        id: 'edge-0-1',
        source: 'node-start',
        target: 'node-db-dump',
        label: 'On Success',
        style: { stroke: '#10b981', strokeWidth: 2 },
        markerEnd: { type: MarkerType.ArrowClosed, color: '#10b981' },
      },
      {
        id: 'edge-3-4',
        source: 'node-notify',
        target: 'node-stop',
        label: 'On Success',
        style: { stroke: '#10b981', strokeWidth: 2 },
        markerEnd: { type: MarkerType.ArrowClosed, color: '#10b981' },
      },
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

  // ลบ Node พร้อมเส้นเชื่อมทั้งหมดที่เข้า/ออกจาก Node นั้น (มีผลเมื่อกด Save Pipeline)
  const handleDeleteNode = useCallback(
    (nodeId: string, nodeLabel: string) => {
      if (!window.confirm(`Delete step "${nodeLabel}"?\n\nChanges take effect after you click Save Pipeline.`)) return;
      setNodes((nds) => nds.filter((n) => n.id !== nodeId));
      setEdges((eds) => eds.filter((e) => e.source !== nodeId && e.target !== nodeId));
    },
    [setNodes, setEdges]
  );

  // เปิดหน้าต่างย้าย Task: ถ้า Node ที่กดอยู่ในกลุ่มที่เลือกไว้ (Ctrl+คลิก) ย้ายทั้งกลุ่ม ไม่เช่นนั้นย้ายเฉพาะ Node นั้น
  // Start ย้ายไม่ได้ จึงตัดออกจากกลุ่มเสมอ
  const handleOpenMove = useCallback(
    (nodeId: string) => {
      const selectedIds = nodes.filter((n) => n.selected).map((n) => n.id);
      const requested = selectedIds.includes(nodeId) ? selectedIds : [nodeId];
      const movable = requested.filter((id) => nodes.find((n) => n.id === id)?.type !== 'START');
      setMoveError(null);
      setMoveDialog({ nodeIds: movable, startExcluded: movable.length !== requested.length });
    },
    [nodes]
  );

  // Attach onEdit / onMove / onDelete listeners to nodes
  const nodesWithHandlers = useMemo(() => {
    return nodes.map((n) => ({
      ...n,
      data: {
        ...n.data,
        onDelete: () => handleDeleteNode(n.id, (n.data.label as string) || n.id),
        onMove: n.type === 'START' ? undefined : () => handleOpenMove(n.id),
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
  }, [nodes, handleDeleteNode, handleOpenMove]);

  // เพิ่มเส้นเชื่อม — แต่ละ Node มีได้เส้นละชนิด (On Success / On Failure) ชนิดละ 1 เส้น จึงแทนที่เส้นชนิดเดิมของ Node ต้นทาง
  const applyEdge = useCallback(
    (connection: Connection, kind: EdgeKind) => {
      setEdges((eds) =>
        addEdge(
          buildEdge(connection, kind),
          eds.filter((e) => !(e.source === connection.source && e.label === EDGE_LABEL[kind]))
        )
      );
    },
    [setEdges]
  );

  const onConnect = useCallback(
    (connection: Connection) => {
      const source = nodes.find((n) => n.id === connection.source);
      if (source?.type === 'START') {
        applyEdge(connection, 'success');
        return;
      }
      setPendingConnection(connection);
    },
    [nodes, applyEdge]
  );

  const isValidConnection = useCallback(
    (conn: Connection | Edge) => {
      if (conn.source === conn.target) return false;
      const source = nodes.find((n) => n.id === conn.source);
      const target = nodes.find((n) => n.id === conn.target);
      if (!source || !target) return false;
      return target.type !== 'START' && source.type !== 'STOP';
    },
    [nodes]
  );

  const handleConfirmEdge = (kind: EdgeKind) => {
    if (!pendingConnection) return;
    const connection = pendingConnection;
    applyEdge(connection, kind);

    // On Failed: ตั้งค่าเริ่มต้นให้ LINE Notify ใช้ Error Message ของ Task ก่อนหน้า (ถ้ายังไม่ได้กรอกข้อความ)
    if (kind === 'failure') {
      const source = nodes.find((n) => n.id === connection.source);
      const target = nodes.find((n) => n.id === connection.target);
      if (source && target?.type === 'LINE_NOTIFY' && !String(target.data.message ?? '').trim()) {
        const sourceKey = (source.data.nodeKey as string) || source.id;
        setNodes((nds) =>
          nds.map((n) =>
            n.id === target.id ? { ...n, data: { ...n.data, message: errorMessagePlaceholder(sourceKey) } } : n
          )
        );
      }
    }
    setPendingConnection(null);
  };

  const hasStartNode = nodes.some((n) => n.type === 'START');

  const addStepNode = (type: TaskType) => {
    if (type === 'START' && hasStartNode) return;
    const usedKeys = new Set(nodes.map((n) => n.data.nodeKey as string));
    let key: string;
    if (type === 'START') {
      key = 'start';
    } else if (type === 'STOP') {
      let stopSeq = 1;
      key = 'stop';
      while (usedKeys.has(key)) key = `stop_${++stopSeq}`;
    } else {
      let seq = nodes.length + 1;
      while (usedKeys.has(`step_${seq}`)) seq++;
      key = `step_${seq}`;
    }
    const labelMap: Record<TaskType, string> = {
      DATABASE_BACKUP: 'Database Dump',
      FILE_BACKUP: 'Directory Archive',
      SPLIT_TRANSFER: 'Split & SFTP Upload',
      EMAIL_ALERT: 'SES Notification',
      GOOGLE_DRIVE_UPLOAD: 'Google Drive Upload',
      LINE_NOTIFY: 'LINE Notify',
      START: 'Start',
      STOP: 'Stop',
    };

    const defaultDataMap: Record<TaskType, Record<string, any>> = {
      DATABASE_BACKUP: {
        databaseName: '',
        compressionFormat: 'GZIP',
        destinationDir: 'storage/backups',
      },
      FILE_BACKUP: {
        sourcePath: 'storage/data',
        destinationDir: 'storage/backups',
        compressionFormat: 'TAR_GZ',
        exclusionPatterns: 'node_modules/**, *.log, temp/**, .git/**',
      },
      SPLIT_TRANSFER: {
        sourceFilePath: '${last_output_path}',
        remoteDirectory: '/upload',
        chunkSizeMb: '50',
        maxRetries: '3',
      },
      EMAIL_ALERT: {
        recipient: 'devops@company.com',
        subject: 'Custos Pipeline Report: ${last_output_path}',
      },
      GOOGLE_DRIVE_UPLOAD: {
        sourceFilePath: '${last_output_path}',
        folderId: '',
        systemSource: 'CUSTOS_PIPELINE',
      },
      LINE_NOTIFY: {
        credentialId: '',
        credentialName: '',
        taskId: '',
        taskTitle: '',
        message: '',
      },
      START: {},
      STOP: {},
    };

    const newNode: Node = {
      id: `node-${Date.now()}`,
      type,
      position: { x: 180 + Math.random() * 40, y: 120 + nodes.length * 90 },
      data: {
        label: labelMap[type],
        nodeKey: key,
        ...defaultDataMap[type],
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

  // บันทึก Pipeline (ตรวจความถูกต้อง + เรียก API) — คืนผลลัพธ์จาก Backend หรือ null ถ้าไม่ผ่าน/ล้มเหลว
  const persistPipeline = async (): Promise<PipelineDetailResponse | null> => {
    try {
      setIsSaving(true);
      setStatusMessage(null);

      if (!pipelineName.trim()) {
        setNameTouched(true);
        setStatusMessage({ text: 'Error: Pipeline name is required', type: 'error' });
        return null;
      }

      if (scheduleError) {
        setStatusMessage({ text: `Error: ${scheduleError}`, type: 'error' });
        return null;
      }

      const startCount = nodes.filter((n) => n.type === 'START').length;
      if (startCount !== 1) {
        setStatusMessage({
          text: startCount === 0
            ? 'Error: Pipeline must have exactly one Start node'
            : 'Error: A pipeline can have only one Start node',
          type: 'error',
        });
        return null;
      }

      // เส้นเชื่อมส่งเป็น nodeKey เพราะ Node ใหม่ยังไม่มี UUID (Backend จะแปลงเป็น ID ให้หลังบันทึก)
      const nodeKeys = nodes.map((node, index) => (node.data.nodeKey as string) || `step_${index + 1}`);
      const duplicateKey = nodeKeys.find((key, index) => nodeKeys.indexOf(key) !== index);
      if (duplicateKey) {
        setStatusMessage({ text: `Error: Duplicate node key "${duplicateKey}"`, type: 'error' });
        return null;
      }
      const keyByNodeId = new Map(nodes.map((node, index) => [node.id, nodeKeys[index]]));

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
        delete dataCopy.onDelete;

        return {
          id: node.id.includes('-') && node.id.length === 36 ? node.id : undefined,
          nodeKey: nodeKeys[index],
          nodeLabel: (node.data.label as string) || `Step ${index + 1}`,
          nodeType: (node.type as TaskType) || 'DATABASE_BACKUP',
          stepOrder: index + 1,
          positionX: node.position.x,
          positionY: node.position.y,
          configOverrideJson: JSON.stringify(dataCopy),
          onSuccessNodeKey: successEdge ? keyByNodeId.get(successEdge.target) : undefined,
          onFailureNodeKey: failureEdge ? keyByNodeId.get(failureEdge.target) : undefined,
        };
      });

      // Start ต้องเป็นลำดับแรกเสมอ
      stepDtos.sort((a, b) => Number(b.nodeType === 'START') - Number(a.nodeType === 'START'));
      stepDtos.forEach((dto, index) => {
        dto.stepOrder = index + 1;
      });

      const payload: SavePipelineRequest = {
        id: initialPipeline?.id ?? createdId ?? undefined,
        name: pipelineName.trim(),
        description,
        cronExpression,
        timezone,
        misfirePolicy,
        isActive,
        nodes: stepDtos,
      };

      let result: PipelineDetailResponse;
      if (payload.id) {
        result = await pipelineApi.updatePipeline(payload.id, payload);
      } else {
        result = await pipelineApi.createPipeline(payload);
        // เก็บ ID ไว้ เผื่อขั้นตอนต่อไป (เช่น ย้าย Task) ล้มเหลว จะได้ไม่สร้าง Pipeline ซ้ำเมื่อกดลองใหม่
        setCreatedId(result.id);
      }
      return result;
    } catch (err: any) {
      const errorMsg = err.response?.data?.message || err.message || 'Failed to save pipeline';
      setStatusMessage({ text: `Error: ${errorMsg}`, type: 'error' });
      return null;
    } finally {
      setIsSaving(false);
    }
  };

  const handleSavePipeline = async () => {
    const result = await persistPipeline();
    if (!result) return;
    setStatusMessage({ text: 'Pipeline workflow and Quartz schedule saved successfully!', type: 'success' });
    if (onSaved) {
      onSaved(result);
    }
  };

  // ย้าย Task ที่เลือกไป Pipeline อื่น: บันทึก Pipeline ปัจจุบันก่อน (ให้ทุก Node มี ID จริงและไม่เสียงานที่ยังไม่ได้ Save)
  // แล้วระบุ Node ที่ย้ายด้วย nodeKey ซึ่งไม่ซ้ำกันภายใน Pipeline
  const handleConfirmMove = async (targetPipelineId: string) => {
    if (!moveDialog) return;
    setMoveError(null);
    setIsMoving(true);
    try {
      const keysToMove = moveDialog.nodeIds.map((id) => nodes.find((n) => n.id === id)?.data.nodeKey as string | undefined);
      const saved = await persistPipeline();
      if (!saved) {
        setMoveError('Could not save the current pipeline, so nothing was moved. See the message above the canvas.');
        return;
      }
      const idsToMove = saved.nodes.filter((n) => keysToMove.includes(n.nodeKey)).map((n) => n.id as string);
      const res = await pipelineApi.moveNodes(saved.id, { targetPipelineId, nodeIds: idsToMove });
      setMoveDialog(null);
      setStatusMessage({
        text: `Moved ${res.movedCount} task(s) to "${res.target.name}"`,
        type: 'success',
      });
      if (onMoved) {
        onMoved(res.source, res.target);
      }
    } catch (err: any) {
      setMoveError(err.response?.data?.message || err.message || 'Failed to move tasks');
    } finally {
      setIsMoving(false);
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
            <label htmlFor="pipeline-name" className="block text-[11px] font-medium text-slate-400 px-1">
              Pipeline name <span className="text-red-400">*</span>
            </label>
            <input
              id="pipeline-name"
              type="text"
              value={pipelineName}
              maxLength={100}
              onChange={(e) => setPipelineName(e.target.value)}
              onBlur={() => setNameTouched(true)}
              aria-invalid={nameTouched && !pipelineName.trim()}
              className={`text-lg font-bold text-white bg-slate-950 border rounded-lg focus:outline-none px-3 py-1.5 w-full max-w-lg transition-colors ${
                nameTouched && !pipelineName.trim()
                  ? 'border-red-500/60 focus:border-red-500'
                  : 'border-slate-800 hover:border-slate-700 focus:border-primary'
              }`}
              placeholder="e.g. Nightly DB Backup"
            />
            {nameTouched && !pipelineName.trim() && (
              <p className="text-[11px] text-red-400 px-1">Please enter a pipeline name</p>
            )}
            <input
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="text-xs text-slate-400 bg-transparent border-b border-transparent hover:border-slate-700 focus:border-primary focus:outline-none px-1 w-full max-w-xl transition-colors"
              placeholder="Pipeline description..."
            />
          </div>

          <div className="flex items-center gap-3 flex-wrap">
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

        {/* Schedule / Timezone / Misfire / Active */}
        <div className="flex items-start gap-3 flex-wrap mt-4">
          <SchedulePicker
            value={cronExpression}
            onChange={(cron, error) => {
              setCronExpression(cron);
              setScheduleError(error);
            }}
          />

          <div className="flex items-center gap-3 flex-wrap">
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
        <div className="absolute top-4 left-4 max-w-[calc(100%-2rem)] z-10 flex flex-wrap items-center gap-2 bg-slate-900/90 backdrop-blur-md p-2 rounded-xl border border-slate-800 shadow-xl">
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
          <button
            onClick={() => addStepNode('GOOGLE_DRIVE_UPLOAD')}
            className="flex items-center gap-1.5 text-xs px-2.5 py-1.5 rounded-lg bg-sky-500/10 border border-sky-500/20 text-sky-400 hover:bg-sky-500/20 transition-all font-medium"
          >
            <HardDriveUpload className="w-3.5 h-3.5" />
            Google Drive
          </button>
          <button
            onClick={() => addStepNode('LINE_NOTIFY')}
            className="flex items-center gap-1.5 text-xs px-2.5 py-1.5 rounded-lg bg-green-500/10 border border-green-500/20 text-green-400 hover:bg-green-500/20 transition-all font-medium"
          >
            <MessageSquare className="w-3.5 h-3.5" />
            LINE Notify
          </button>
          <span className="w-px h-5 bg-slate-700 mx-1" />
          <button
            onClick={() => addStepNode('START')}
            disabled={hasStartNode}
            title={hasStartNode ? 'Pipeline มี Start ได้เพียง 1 อัน' : 'เพิ่มจุดเริ่มต้น'}
            className="flex items-center gap-1.5 text-xs px-2.5 py-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 hover:bg-emerald-500/20 transition-all font-medium disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-emerald-500/10"
          >
            <Play className="w-3.5 h-3.5" />
            Start
          </button>
          <button
            onClick={() => addStepNode('STOP')}
            className="flex items-center gap-1.5 text-xs px-2.5 py-1.5 rounded-lg bg-red-500/10 border border-red-500/30 text-red-300 hover:bg-red-500/20 transition-all font-medium"
          >
            <Square className="w-3.5 h-3.5" />
            Stop
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
          isValidConnection={isValidConnection}
          nodeTypes={nodeTypes}
          // Ctrl/Cmd+คลิกเพื่อเลือกหลาย Task (Shift+ลากเพื่อกรอบเลือก) — ปิด Backspace-ลบ เพราะข้ามการยืนยันของปุ่ม X
          multiSelectionKeyCode={['Control', 'Meta']}
          deleteKeyCode={null}
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

      {/* Edge type chooser */}
      <Dialog open={!!pendingConnection} onOpenChange={(open) => !open && setPendingConnection(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="text-base font-bold">เลือกเงื่อนไขของเส้นเชื่อม</DialogTitle>
            <DialogDescription className="text-xs text-muted-foreground">
              Task ปลายทางจะทำงานเมื่อ Task ต้นทางเป็นไปตามเงื่อนไขที่เลือก
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-2.5 pt-1">
            <button
              type="button"
              onClick={() => handleConfirmEdge('success')}
              className="w-full flex items-start gap-3 p-3 rounded-lg border border-emerald-500/30 bg-emerald-500/5 hover:bg-emerald-500/10 text-left transition-colors"
            >
              <CheckCircle2 className="w-4 h-4 mt-0.5 text-emerald-400 flex-shrink-0" />
              <div>
                <div className="text-sm font-semibold text-emerald-300">On Success</div>
                <div className="text-[11px] text-slate-400">ไปต่อเมื่อ Task ก่อนหน้าทำงานสำเร็จ</div>
              </div>
            </button>

            <button
              type="button"
              onClick={() => handleConfirmEdge('failure')}
              className="w-full flex items-start gap-3 p-3 rounded-lg border border-red-500/30 bg-red-500/5 hover:bg-red-500/10 text-left transition-colors"
            >
              <XCircle className="w-4 h-4 mt-0.5 text-red-400 flex-shrink-0" />
              <div>
                <div className="text-sm font-semibold text-red-300">On Failed</div>
                <div className="text-[11px] text-slate-400">
                  ไปต่อเมื่อ Task ก่อนหน้าล้มเหลว — ใช้ Error Message ของ Task นั้นได้ผ่านตัวแปร{' '}
                  <span className="font-mono text-slate-300">{'${last_error_message}'}</span> หรือ{' '}
                  <span className="font-mono text-slate-300">{'${nodeKey.error_message}'}</span>{' '}
                  (LINE Notify จะตั้งเป็นค่าเริ่มต้นให้อัตโนมัติ)
                </div>
              </div>
            </button>
          </div>
        </DialogContent>
      </Dialog>

      {/* Move Tasks Dialog */}
      {moveDialog && (
        <MoveNodesDialog
          open
          onClose={() => setMoveDialog(null)}
          targets={pipelines
            .filter((p) => p.id !== (initialPipeline?.id ?? createdId))
            .map((p) => ({ id: p.id, name: p.name }))}
          nodeCount={moveDialog.nodeIds.length}
          internalEdgeCount={
            edges.filter((e) => moveDialog.nodeIds.includes(e.source) && moveDialog.nodeIds.includes(e.target)).length
          }
          crossingEdgeCount={
            edges.filter((e) => moveDialog.nodeIds.includes(e.source) !== moveDialog.nodeIds.includes(e.target)).length
          }
          startExcluded={moveDialog.startExcluded}
          isMoving={isMoving}
          error={moveError}
          onConfirm={handleConfirmMove}
        />
      )}

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
