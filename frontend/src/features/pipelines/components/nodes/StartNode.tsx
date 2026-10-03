import React from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { Play } from 'lucide-react';
import { NodeHeaderActions } from './NodeHeaderActions';

export interface StartNodeData {
  label: string;
  nodeKey: string;
  onDelete?: () => void;
  [key: string]: unknown;
}

// จุดเริ่มต้นของ Pipeline (มีได้ 1 อันต่อ Pipeline) — มีเฉพาะเส้นออก
export const StartNode: React.FC<NodeProps> = ({ data, selected }) => {
  const nodeData = data as StartNodeData;

  return (
    <div
      className={`min-w-[160px] rounded-full border bg-slate-900/95 px-4 py-2.5 shadow-xl backdrop-blur transition-all ${
        selected
          ? 'border-emerald-500 shadow-emerald-500/20 ring-2 ring-emerald-500/30'
          : 'border-emerald-500/40 hover:border-emerald-500/70'
      }`}
    >
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-full bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 flex items-center justify-center">
            <Play className="w-3.5 h-3.5" />
          </div>
          <div className="text-xs font-semibold text-white tracking-tight">
            {nodeData.label || 'Start'}
          </div>
        </div>
        <NodeHeaderActions onDelete={nodeData.onDelete} />
      </div>

      <Handle
        type="source"
        position={Position.Bottom}
        className="w-3 h-3 !bg-emerald-400 !border-2 !border-slate-900 rounded-full"
      />
    </div>
  );
};
