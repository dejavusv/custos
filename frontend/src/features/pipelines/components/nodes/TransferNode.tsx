import React from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { Send, Settings2 } from 'lucide-react';

export interface TransferNodeData {
  label: string;
  nodeKey: string;
  protocol?: string;
  remoteDirectory?: string;
  chunkSizeBytes?: number;
  onEdit?: () => void;
  [key: string]: unknown;
}

export const TransferNode: React.FC<NodeProps> = ({ data, selected }) => {
  const nodeData = data as TransferNodeData;

  return (
    <div
      className={`min-w-[220px] rounded-xl border bg-slate-900/95 p-3.5 shadow-xl backdrop-blur transition-all ${
        selected
          ? 'border-purple-500 shadow-purple-500/20 ring-2 ring-purple-500/30'
          : 'border-slate-700/80 hover:border-slate-600'
      }`}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="w-3 h-3 !bg-purple-400 !border-2 !border-slate-900 rounded-full"
      />

      <div className="flex items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-purple-500/10 border border-purple-500/20 text-purple-400 flex items-center justify-center">
            <Send className="w-4 h-4" />
          </div>
          <div>
            <div className="text-xs font-semibold text-white tracking-tight">
              {nodeData.label || 'Split & Transfer'}
            </div>
            <div className="text-[10px] font-mono text-slate-400">
              {nodeData.nodeKey}
            </div>
          </div>
        </div>

        {nodeData.onEdit && (
          <button
            onClick={nodeData.onEdit}
            className="text-slate-400 hover:text-white p-1 rounded hover:bg-slate-800 transition-colors"
            title="Configure Step"
          >
            <Settings2 className="w-3.5 h-3.5" />
          </button>
        )}
      </div>

      <div className="space-y-1 pt-1 border-t border-slate-800/80 text-[11px]">
        <div className="flex justify-between text-slate-400">
          <span>Target:</span>
          <span className="font-mono text-purple-300 font-medium truncate max-w-[120px]">
            {nodeData.credentialName
              ? String(nodeData.credentialName)
              : `${nodeData.protocol || 'SFTP'}`}
          </span>
        </div>
        <div className="flex justify-between text-slate-400">
          <span>Chunk:</span>
          <span className="font-mono text-slate-300">
            {nodeData.chunkSizeMb ? `${nodeData.chunkSizeMb} MB` : '50 MB'}
          </span>
        </div>
        <div className="flex justify-between text-slate-400">
          <span>Remote:</span>
          <span className="font-mono text-slate-300 truncate max-w-[110px]">
            {nodeData.remoteDirectory || '/upload'}
          </span>
        </div>
      </div>

      <Handle
        type="source"
        position={Position.Bottom}
        className="w-3 h-3 !bg-purple-400 !border-2 !border-slate-900 rounded-full"
      />
    </div>
  );
};
