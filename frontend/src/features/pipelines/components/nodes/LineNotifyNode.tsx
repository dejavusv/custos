import React from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { MessageSquare } from 'lucide-react';
import { NodeHeaderActions } from './NodeHeaderActions';

export interface LineNotifyNodeData {
  label: string;
  nodeKey: string;
  credentialName?: string;
  taskId?: string;
  taskTitle?: string;
  message?: string;
  onEdit?: () => void;
  onDelete?: () => void;
  [key: string]: unknown;
}

export const LineNotifyNode: React.FC<NodeProps> = ({ data, selected }) => {
  const nodeData = data as LineNotifyNodeData;
  const taskLabel = nodeData.taskTitle || (nodeData.taskId ? `${nodeData.taskId.slice(0, 8)}…` : 'ยังไม่ได้เลือก');

  return (
    <div
      className={`min-w-[220px] rounded-xl border bg-slate-900/95 p-3.5 shadow-xl backdrop-blur transition-all ${
        selected
          ? 'border-green-500 shadow-green-500/20 ring-2 ring-green-500/30'
          : 'border-slate-700/80 hover:border-slate-600'
      }`}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="w-3 h-3 !bg-green-400 !border-2 !border-slate-900 rounded-full"
      />

      <div className="flex items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-green-500/10 border border-green-500/20 text-green-400 flex items-center justify-center">
            <MessageSquare className="w-4 h-4" />
          </div>
          <div>
            <div className="text-xs font-semibold text-white tracking-tight">
              {nodeData.label || 'LINE Notify'}
            </div>
            <div className="text-[10px] font-mono text-slate-400">
              {nodeData.nodeKey}
            </div>
          </div>
        </div>

        <NodeHeaderActions onEdit={nodeData.onEdit} onDelete={nodeData.onDelete} />
      </div>

      <div className="space-y-1 pt-1 border-t border-slate-800/80 text-[11px]">
        <div className="flex justify-between text-slate-400">
          <span>Task:</span>
          <span className="font-mono text-green-300 font-medium truncate max-w-[130px]">{taskLabel}</span>
        </div>
        <div className="flex justify-between text-slate-400">
          <span>Source:</span>
          <span className="font-mono text-slate-300 truncate max-w-[130px]">
            {nodeData.credentialName || 'ยังไม่ได้เลือก'}
          </span>
        </div>
      </div>

      <Handle
        type="source"
        position={Position.Bottom}
        className="w-3 h-3 !bg-green-400 !border-2 !border-slate-900 rounded-full"
      />
    </div>
  );
};
