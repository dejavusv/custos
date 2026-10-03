import React, { useState } from 'react';
import { AlertTriangle, ArrowRightLeft } from 'lucide-react';
import { Button } from '../../../components/ui/Button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '../../../components/ui/Dialog';

export interface MoveTarget {
  id: string;
  name: string;
}

interface MoveNodesDialogProps {
  open: boolean;
  onClose: () => void;
  targets: MoveTarget[];
  nodeCount: number;
  internalEdgeCount: number; // เส้นเชื่อมระหว่าง Task ที่ย้ายด้วยกัน — ติดไปด้วย
  crossingEdgeCount: number; // เส้นที่เชื่อมกับ Task ที่ไม่ได้ย้าย — จะถูกตัดออก
  startExcluded: boolean;
  isMoving: boolean;
  error: string | null;
  onConfirm: (targetPipelineId: string) => void;
}

export const MoveNodesDialog: React.FC<MoveNodesDialogProps> = ({
  open,
  onClose,
  targets,
  nodeCount,
  internalEdgeCount,
  crossingEdgeCount,
  startExcluded,
  isMoving,
  error,
  onConfirm,
}) => {
  const [targetId, setTargetId] = useState('');

  return (
    <Dialog open={open} onOpenChange={(o) => !o && !isMoving && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="text-base font-bold flex items-center gap-2">
            <ArrowRightLeft className="w-4 h-4 text-sky-400" />
            Move {nodeCount} {nodeCount === 1 ? 'task' : 'tasks'} to another pipeline
          </DialogTitle>
          <DialogDescription className="text-xs text-muted-foreground">
            The current pipeline is saved first, then the selected tasks are moved. Hold Ctrl and click tasks on the
            canvas to select several at once.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3 pt-1">
          {targets.length === 0 ? (
            <p className="text-xs text-slate-400">There is no other pipeline to move to. Create another pipeline first.</p>
          ) : (
            <div className="space-y-1.5">
              <label className="text-xs text-slate-400" htmlFor="move-target">
                Target pipeline
              </label>
              <select
                id="move-target"
                value={targetId}
                onChange={(e) => setTargetId(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-primary"
              >
                <option value="" className="bg-slate-900">
                  Select a pipeline...
                </option>
                {targets.map((t) => (
                  <option key={t.id} value={t.id} className="bg-slate-900">
                    {t.name}
                  </option>
                ))}
              </select>
            </div>
          )}

          <ul className="text-[11px] text-slate-400 space-y-1 list-disc pl-4">
            <li>{internalEdgeCount} connection(s) between the selected tasks move with them.</li>
            {crossingEdgeCount > 0 && (
              <li className="text-amber-400">
                {crossingEdgeCount} connection(s) to tasks that stay behind will be removed.
              </li>
            )}
            <li>Moved tasks are placed below the existing tasks in the target; connect them to its flow afterwards.</li>
            {startExcluded && <li>The Start node cannot be moved and is left out of the selection.</li>}
          </ul>

          {error && (
            <div className="p-2.5 rounded-lg border text-xs flex items-center gap-2 bg-red-500/10 border-red-500/20 text-red-400">
              <AlertTriangle className="w-4 h-4 flex-shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <div className="flex justify-end gap-2 pt-1">
            <Button variant="outline" size="sm" onClick={onClose} disabled={isMoving} className="text-xs">
              Cancel
            </Button>
            <Button
              size="sm"
              onClick={() => onConfirm(targetId)}
              disabled={isMoving || !targetId || nodeCount === 0}
              className="text-xs gap-1.5"
            >
              <ArrowRightLeft className="w-3.5 h-3.5" />
              {isMoving ? 'Moving...' : 'Move'}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
};
