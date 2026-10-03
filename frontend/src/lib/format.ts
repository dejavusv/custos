export const formatDuration = (ms?: number | null): string => {
  if (ms === undefined || ms === null) return '-';
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
};

export const formatDateTime = (iso?: string | null): string => {
  if (!iso) return '-';
  return new Date(iso).toLocaleString('th-TH');
};
