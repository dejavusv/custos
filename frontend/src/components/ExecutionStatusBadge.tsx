import React from 'react';
import { CheckCircle2, AlertCircle, Clock, Square } from 'lucide-react';
import { ExecutionStatus } from '../types/pipeline';
import { Badge } from './ui/Badge';

interface ExecutionStatusBadgeProps {
  status: ExecutionStatus;
}

export const ExecutionStatusBadge: React.FC<ExecutionStatusBadgeProps> = ({ status }) => {
  switch (status) {
    case 'SUCCESS':
      return (
        <Badge variant="success" className="gap-1">
          <CheckCircle2 className="w-3 h-3" />
          SUCCESS
        </Badge>
      );
    case 'RUNNING':
      return (
        <Badge variant="warning" className="gap-1 animate-pulse">
          <Clock className="w-3 h-3" />
          RUNNING
        </Badge>
      );
    case 'FAILED':
      return (
        <Badge variant="destructive" className="gap-1">
          <AlertCircle className="w-3 h-3" />
          FAILED
        </Badge>
      );
    case 'ABORTED':
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-md text-xs font-semibold bg-slate-800 text-slate-400 border border-slate-700">
          <Square className="w-3 h-3" />
          ABORTED
        </span>
      );
    default:
      return <Badge variant="outline">{status}</Badge>;
  }
};
