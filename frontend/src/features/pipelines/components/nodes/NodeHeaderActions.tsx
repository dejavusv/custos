import React from 'react';
import { Settings2, X } from 'lucide-react';

interface NodeHeaderActionsProps {
  onEdit?: () => void;
  onDelete?: () => void;
}

// ปุ่มตั้งค่า / ลบ ที่มุมขวาบนของทุก Node (class "nodrag" กันไม่ให้การคลิกกลายเป็นการลาก Node)
export const NodeHeaderActions: React.FC<NodeHeaderActionsProps> = ({ onEdit, onDelete }) => (
  <div className="flex items-center gap-0.5">
    {onEdit && (
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          onEdit();
        }}
        className="nodrag text-slate-400 hover:text-white p-1 rounded hover:bg-slate-800 transition-colors"
        title="Configure Step"
      >
        <Settings2 className="w-3.5 h-3.5" />
      </button>
    )}
    {onDelete && (
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          onDelete();
        }}
        className="nodrag text-slate-400 hover:text-red-400 p-1 rounded hover:bg-red-500/10 transition-colors"
        title="Delete Step"
      >
        <X className="w-3.5 h-3.5" />
      </button>
    )}
  </div>
);
