import React from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { Database, Settings2 } from 'lucide-react';

export interface DatabaseBackupNodeData {
  label: string;
  nodeKey: string;
  databaseName?: string;
  host?: string;
  onEdit?: () => void;
  [key: string]: unknown;
}

export const DatabaseBackupNode: React.FC<NodeProps> = ({ data, selected }) => {
  const nodeData = data as DatabaseBackupNodeData;

  return (
    <div
      className={`min-w-[220px] rounded-xl border bg-slate-900/95 p-3.5 shadow-xl backdrop-blur transition-all ${
        selected
          ? 'border-blue-500 shadow-blue-500/20 ring-2 ring-blue-500/30'
          : 'border-slate-700/80 hover:border-slate-600'
      }`}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="w-3 h-3 !bg-blue-400 !border-2 !border-slate-900 rounded-full"
      />

      <div className="flex items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-blue-500/10 border border-blue-500/20 text-blue-400 flex items-center justify-center">
            <Database className="w-4 h-4" />
          </div>
          <div>
            <div className="text-xs font-semibold text-white tracking-tight">
              {nodeData.label || 'Database Backup'}
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
          <span>Target DB:</span>
          <span className="font-mono text-blue-300 font-medium truncate max-w-[110px]">
            {nodeData.databaseName || 'Auto/Default'}
          </span>
        </div>
        <div className="flex justify-between text-slate-400">
          <span>Format:</span>
          <span className="font-mono text-slate-300">GZIP (.sql.gz)</span>
        </div>
      </div>

      <Handle
        type="source"
        position={Position.Bottom}
        className="w-3 h-3 !bg-blue-400 !border-2 !border-slate-900 rounded-full"
      />
    </div>
  );
};
