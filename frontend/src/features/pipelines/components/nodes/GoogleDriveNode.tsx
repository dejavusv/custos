import React from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { HardDriveUpload } from 'lucide-react';
import { NodeHeaderActions } from './NodeHeaderActions';

export interface GoogleDriveNodeData {
  label: string;
  nodeKey: string;
  sourceFilePath?: string;
  folderId?: string;
  systemSource?: string;
  onEdit?: () => void;
  onMove?: () => void;
  onDelete?: () => void;
  [key: string]: unknown;
}

export const GoogleDriveNode: React.FC<NodeProps> = ({ data, selected }) => {
  const nodeData = data as GoogleDriveNodeData;

  return (
    <div
      className={`min-w-[220px] rounded-xl border bg-slate-900/95 p-3.5 shadow-xl backdrop-blur transition-all ${
        selected
          ? 'border-sky-500 shadow-sky-500/20 ring-2 ring-sky-500/30'
          : 'border-slate-700/80 hover:border-slate-600'
      }`}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="w-3 h-3 !bg-sky-400 !border-2 !border-slate-900 rounded-full"
      />

      <div className="flex items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-sky-500/10 border border-sky-500/20 text-sky-400 flex items-center justify-center">
            <HardDriveUpload className="w-4 h-4" />
          </div>
          <div>
            <div className="text-xs font-semibold text-white tracking-tight">
              {nodeData.label || 'Google Drive Upload'}
            </div>
            <div className="text-[10px] font-mono text-slate-400">
              {nodeData.nodeKey}
            </div>
          </div>
        </div>

        <NodeHeaderActions onEdit={nodeData.onEdit} onMove={nodeData.onMove} onDelete={nodeData.onDelete} />
      </div>

      <div className="space-y-1 pt-1 border-t border-slate-800/80 text-[11px]">
        <div className="flex justify-between text-slate-400">
          <span>Folder:</span>
          <span className="font-mono text-sky-300 font-medium truncate max-w-[120px]">
            {nodeData.folderId || 'Default folder'}
          </span>
        </div>
        <div className="flex justify-between text-slate-400">
          <span>Source:</span>
          <span className="font-mono text-slate-300 truncate max-w-[120px]">
            {nodeData.systemSource || 'CUSTOS_PIPELINE'}
          </span>
        </div>
        <div className="flex justify-between text-slate-400">
          <span>Audit:</span>
          <span className="font-mono text-slate-300">Firestore</span>
        </div>
      </div>

      <Handle
        type="source"
        position={Position.Bottom}
        className="w-3 h-3 !bg-sky-400 !border-2 !border-slate-900 rounded-full"
      />
    </div>
  );
};
