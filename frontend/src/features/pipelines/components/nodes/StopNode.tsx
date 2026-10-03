import React from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { Square } from 'lucide-react';
import { NodeHeaderActions } from './NodeHeaderActions';

export interface StopNodeData {
  label: string;
  nodeKey: string;
  onMove?: () => void;
  onDelete?: () => void;
  [key: string]: unknown;
}

// จุดสิ้นสุดของกิ่งงาน — มีเฉพาะเส้นเข้า (ถึงทาง On Failure = Pipeline FAILED)
export const StopNode: React.FC<NodeProps> = ({ data, selected }) => {
  const nodeData = data as StopNodeData;

  return (
    <div
      className={`min-w-[160px] rounded-full border bg-slate-900/95 px-4 py-2.5 shadow-xl backdrop-blur transition-all ${
        selected
          ? 'border-red-500 shadow-red-500/20 ring-2 ring-red-500/30'
          : 'border-red-500/40 hover:border-red-500/70'
      }`}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="w-3 h-3 !bg-red-400 !border-2 !border-slate-900 rounded-full"
      />

      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-full bg-red-500/10 border border-red-500/30 text-red-400 flex items-center justify-center">
            <Square className="w-3.5 h-3.5" />
          </div>
          <div className="text-xs font-semibold text-white tracking-tight">
            {nodeData.label || 'Stop'}
          </div>
        </div>
        <NodeHeaderActions onMove={nodeData.onMove} onDelete={nodeData.onDelete} />
      </div>
    </div>
  );
};
