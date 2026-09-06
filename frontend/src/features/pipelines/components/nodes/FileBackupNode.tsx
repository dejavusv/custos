import React from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { Archive, Settings2 } from 'lucide-react';

export interface FileBackupNodeData {
  label: string;
  nodeKey: string;
  sourcePath?: string;
  onEdit?: () => void;
  [key: string]: unknown;
}

export const FileBackupNode: React.FC<NodeProps> = ({ data, selected }) => {
  const nodeData = data as FileBackupNodeData;

  return (
    <div
      className={`min-w-[220px] rounded-xl border bg-slate-900/95 p-3.5 shadow-xl backdrop-blur transition-all ${
        selected
          ? 'border-amber-500 shadow-amber-500/20 ring-2 ring-amber-500/30'
          : 'border-slate-700/80 hover:border-slate-600'
      }`}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="w-3 h-3 !bg-amber-400 !border-2 !border-slate-900 rounded-full"
      />

      <div className="flex items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-amber-500/10 border border-amber-500/20 text-amber-400 flex items-center justify-center">
            <Archive className="w-4 h-4" />
          </div>
          <div>
            <div className="text-xs font-semibold text-white tracking-tight">
              {nodeData.label || 'File Backup'}
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
          <span>Source:</span>
          <span className="font-mono text-amber-300 font-medium truncate max-w-[120px]">
            {nodeData.sourcePath || 'storage/data'}
          </span>
        </div>
        <div className="flex justify-between text-slate-400">
          <span>Format:</span>
          <span className="font-mono text-slate-300">.tar.gz</span>
        </div>
      </div>

      <Handle
        type="source"
        position={Position.Bottom}
        className="w-3 h-3 !bg-amber-400 !border-2 !border-slate-900 rounded-full"
      />
    </div>
  );
};
