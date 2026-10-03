import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Box, Loader2 } from 'lucide-react';
import { dockerApi } from '../../services/dockerApi';

export interface DockerContainerSelectProps {
  // Selected container name; empty string means the Custos server's own filesystem
  value: string;
  onChange: (containerName: string) => void;
  className?: string;
  selectClassName?: string;
}

/**
 * Picks where the source files live: the Custos server itself, or a running Docker container
 * on the same Docker network as the backend.
 */
export const DockerContainerSelect: React.FC<DockerContainerSelectProps> = ({
  value,
  onChange,
  className,
  selectClassName = 'w-full px-3 py-2 text-sm rounded-md border border-input bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring',
}) => {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['dockerContainers'],
    queryFn: () => dockerApi.listContainers(),
    staleTime: 15_000,
  });

  const containers = data?.containers ?? [];
  // keep a previously saved container selectable even when it is not running right now
  const missingSelected = value !== '' && !containers.some((c) => c.name === value);

  return (
    <div className={className}>
      <div className="flex items-center gap-2">
        <select
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className={selectClassName}
        >
          <option value="">Custos Server (ไฟล์บนเครื่อง backend)</option>
          {missingSelected && <option value={value}>{value}{data ? ' (ไม่พบ / ไม่ได้รันอยู่)' : ''}</option>}
          {containers.map((c) => (
            <option key={c.name} value={c.name}>
              {c.name} · {c.image}
            </option>
          ))}
        </select>
        {isLoading && <Loader2 className="w-4 h-4 animate-spin text-muted-foreground shrink-0" />}
      </div>

      {value !== '' && (
        <p className="mt-1 text-[11px] text-muted-foreground flex items-center gap-1">
          <Box className="w-3 h-3" />
          เลือกพาธต้นทางจากภายใน container นี้
        </p>
      )}
      {isError && (
        <p className="mt-1 text-[11px] text-destructive">
          โหลดรายการ Docker container ไม่สำเร็จ{' '}
          <button type="button" className="underline" onClick={() => refetch()}>
            ลองใหม่
          </button>
        </p>
      )}
      {data && !data.available && (
        <p className="mt-1 text-[11px] text-amber-500">{data.message || 'Docker ไม่พร้อมใช้งาน'}</p>
      )}
      {data?.available && containers.length === 0 && (
        <p className="mt-1 text-[11px] text-muted-foreground">ไม่พบ container ที่รันอยู่ใน network เดียวกับ backend</p>
      )}
    </div>
  );
};
