import React, { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Folder,
  File,
  FileArchive,
  ArrowUp,
  Home,
  RefreshCw,
  FolderPlus,
  Search,
  Check,
  X,
  Loader2,
  HardDrive,
  AlertCircle,
  Copy,
  ChevronRight,
} from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '../ui/Dialog';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { backupApi } from '../../services/backupApi';
import { StorageItem } from '../../types/backup';

export interface StorageBrowserDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSelect: (selectedPath: string, item?: StorageItem) => void;
  title?: string;
  description?: string;
  mode?: 'folder' | 'file' | 'both';
  initialPath?: string;
}

export const StorageBrowserDialog: React.FC<StorageBrowserDialogProps> = ({
  open,
  onOpenChange,
  onSelect,
  title = 'เลือกไฟล์หรือโฟลเดอร์จาก Storage บน Server',
  description,
  mode = 'folder',
  initialPath,
}) => {
  const queryClient = useQueryClient();
  const [currentPath, setCurrentPath] = useState<string>(initialPath || '');
  const [selectedItem, setSelectedItem] = useState<StorageItem | null>(null);
  const [filterText, setFilterText] = useState('');
  const [isCreatingFolder, setIsCreatingFolder] = useState(false);
  const [newFolderName, setNewFolderName] = useState('');
  const [folderCreateError, setFolderCreateError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  // Sync initialPath when dialog opens
  useEffect(() => {
    if (open) {
      setCurrentPath(initialPath || '');
      setSelectedItem(null);
      setFilterText('');
      setIsCreatingFolder(false);
      setNewFolderName('');
      setFolderCreateError(null);
    }
  }, [open, initialPath]);

  // Query to browse storage
  const {
    data: browseData,
    isLoading,
    isFetching,
    error,
    refetch,
  } = useQuery({
    queryKey: ['storageBrowse', currentPath],
    queryFn: () => backupApi.browseStorage(currentPath || undefined),
    enabled: open,
  });

  // Mutation to create new directory
  const createFolderMutation = useMutation({
    mutationFn: (name: string) => {
      const parent = browseData?.currentPath || currentPath || '';
      return backupApi.createDirectory({
        parentPath: parent,
        folderName: name,
      });
    },
    onSuccess: (newItem) => {
      setIsCreatingFolder(false);
      setNewFolderName('');
      setFolderCreateError(null);
      queryClient.invalidateQueries({ queryKey: ['storageBrowse', currentPath] });
      if (mode === 'folder' || mode === 'both') {
        setSelectedItem(newItem);
      }
    },
    onError: (err: any) => {
      setFolderCreateError(err.response?.data?.message || err.message || 'ไม่สามารถสร้างโฟลเดอร์ได้');
    },
  });

  const handleFolderDoubleClick = (item: StorageItem) => {
    if (item.isDirectory) {
      setCurrentPath(item.path);
      setSelectedItem(null);
      setFilterText('');
      setIsCreatingFolder(false);
    }
  };

  const handleItemClick = (item: StorageItem) => {
    if (mode === 'folder' && !item.isDirectory) return;
    if (mode === 'file' && item.isDirectory) return;
    setSelectedItem(item);
  };

  const handleGoUp = () => {
    if (browseData?.parentPath) {
      setCurrentPath(browseData.parentPath);
      setSelectedItem(null);
      setIsCreatingFolder(false);
    }
  };

  const handleGoHome = () => {
    if (browseData?.defaultDirectory) {
      setCurrentPath(browseData.defaultDirectory);
    } else {
      setCurrentPath('');
    }
    setSelectedItem(null);
    setIsCreatingFolder(false);
  };

  const handleConfirm = () => {
    if (selectedItem) {
      onSelect(selectedItem.path, selectedItem);
      onOpenChange(false);
    } else if ((mode === 'folder' || mode === 'both') && browseData?.currentPath) {
      onSelect(browseData.currentPath);
      onOpenChange(false);
    }
  };

  const handleSelectCurrentFolder = () => {
    if (browseData?.currentPath) {
      onSelect(browseData.currentPath);
      onOpenChange(false);
    }
  };

  const handleCreateFolderSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newFolderName.trim()) return;
    createFolderMutation.mutate(newFolderName.trim());
  };

  const copyPathToClipboard = (path: string) => {
    navigator.clipboard.writeText(path);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  const formatDate = (isoString?: string) => {
    if (!isoString) return '-';
    try {
      const d = new Date(isoString);
      return d.toLocaleDateString('th-TH', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  const isArchiveFile = (ext?: string | null) => {
    if (!ext) return false;
    const e = ext.toLowerCase();
    return e === 'gz' || e === 'tar.gz' || e === 'sql.gz' || e === 'zip' || e === 'zst' || e === 'tar';
  };

  const filteredItems = (browseData?.items || []).filter((item) =>
    item.name.toLowerCase().includes(filterText.toLowerCase())
  );

  const selectedPathPreview = selectedItem
    ? selectedItem.path
    : (mode === 'folder' || mode === 'both')
    ? browseData?.currentPath || '-'
    : '-';

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl w-full p-0 overflow-hidden flex flex-col max-h-[85vh] bg-card border-border shadow-2xl">
        {/* Header */}
        <DialogHeader className="p-5 pb-3 border-b border-border bg-card">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-primary/10 text-primary">
              <HardDrive className="w-5 h-5" />
            </div>
            <div>
              <DialogTitle className="text-base font-bold text-foreground">
                {title}
              </DialogTitle>
              <DialogDescription className="text-xs text-muted-foreground mt-0.5">
                {description || (
                  <>
                    เริ่มต้นที่โฟลเดอร์{' '}
                    <code className="px-1.5 py-0.5 rounded bg-secondary text-primary font-mono text-[11px]">
                      {browseData?.defaultDirectory || 'storage/backups'}
                    </code>
                    {mode === 'folder' && ' • เลือกโฟลเดอร์สำหรับจัดเก็บ'}
                    {mode === 'file' && ' • ดับเบิ้ลคลิกโฟลเดอร์เพื่อเปิด และคลิกเลือกไฟล์'}
                    {mode === 'both' && ' • เลือกไฟล์หรือโฟลเดอร์'}
                  </>
                )}
              </DialogDescription>
            </div>
          </div>

          {/* Navigation Bar */}
          <div className="flex items-center gap-1.5 mt-3 pt-2">
            <Button
              size="sm"
              variant="outline"
              onClick={handleGoHome}
              title="กลับไปที่โฟลเดอร์เริ่มต้น (Default)"
              className="h-8 px-2.5 gap-1.5 text-xs text-foreground"
            >
              <Home className="w-3.5 h-3.5" />
              <span>ค่าเริ่มต้น</span>
            </Button>

            <Button
              size="sm"
              variant="outline"
              onClick={handleGoUp}
              disabled={!browseData?.canGoUp || isLoading}
              title="ขึ้นไป 1 ระดับ (Up)"
              className="h-8 px-2.5 gap-1.5 text-xs text-foreground disabled:opacity-40"
            >
              <ArrowUp className="w-3.5 h-3.5" />
              <span>ขึ้นไป</span>
            </Button>

            <Button
              size="sm"
              variant="outline"
              onClick={() => refetch()}
              disabled={isFetching}
              title="โหลดรายการใหม่ (Refresh)"
              className="h-8 px-2 text-xs text-foreground"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isFetching ? 'animate-spin' : ''}`} />
            </Button>

            <Button
              size="sm"
              variant={isCreatingFolder ? 'secondary' : 'outline'}
              onClick={() => {
                setIsCreatingFolder(!isCreatingFolder);
                setFolderCreateError(null);
                setNewFolderName('');
              }}
              title="สร้างโฟลเดอร์ใหม่"
              className="h-8 px-2.5 gap-1.5 text-xs text-foreground ml-auto"
            >
              <FolderPlus className="w-3.5 h-3.5 text-primary" />
              <span>สร้างโฟลเดอร์</span>
            </Button>
          </div>

          {/* Current Path Bar */}
          <div className="flex items-center gap-1.5 mt-2 px-3 py-1.5 rounded-md bg-secondary/40 border border-border text-xs font-mono">
            <span className="text-muted-foreground select-none">Path:</span>
            <span className="text-foreground truncate flex-1 font-medium">
              {browseData?.currentPath || currentPath || 'storage/backups'}
            </span>
            <button
              type="button"
              onClick={() => copyPathToClipboard(browseData?.currentPath || currentPath || '')}
              className="p-1 hover:bg-secondary rounded text-muted-foreground hover:text-foreground transition-colors"
              title="คัดลอกพาธ"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-emerald-500" /> : <Copy className="w-3.5 h-3.5" />}
            </button>
          </div>

          {/* Inline Create Folder Form */}
          {isCreatingFolder && (
            <form onSubmit={handleCreateFolderSubmit} className="mt-2.5 p-2.5 rounded-md bg-primary/5 border border-primary/20 space-y-2">
              <div className="flex items-center gap-2">
                <FolderPlus className="w-4 h-4 text-primary flex-shrink-0" />
                <Input
                  autoFocus
                  placeholder="ชื่อโฟลเดอร์ใหม่ (เช่น daily_dumps หรือ archive_2026)"
                  value={newFolderName}
                  onChange={(e) => setNewFolderName(e.target.value)}
                  className="h-8 text-xs bg-background"
                />
                <Button
                  type="submit"
                  size="sm"
                  disabled={!newFolderName.trim() || createFolderMutation.isPending}
                  className="h-8 px-3 text-xs gap-1"
                >
                  {createFolderMutation.isPending ? (
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  ) : (
                    <Check className="w-3.5 h-3.5" />
                  )}
                  สร้าง
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  onClick={() => setIsCreatingFolder(false)}
                  className="h-8 px-2 text-xs"
                >
                  <X className="w-3.5 h-3.5" />
                </Button>
              </div>
              {folderCreateError && (
                <p className="text-[11px] text-destructive flex items-center gap-1">
                  <AlertCircle className="w-3 h-3" />
                  {folderCreateError}
                </p>
              )}
            </form>
          )}

          {/* Search Filter Bar */}
          <div className="relative mt-2">
            <Search className="w-3.5 h-3.5 absolute left-2.5 top-2.5 text-muted-foreground" />
            <Input
              placeholder="ค้นหาชื่อไฟล์หรือโฟลเดอร์ในนี้..."
              value={filterText}
              onChange={(e) => setFilterText(e.target.value)}
              className="h-8 pl-8 text-xs bg-background"
            />
          </div>
        </DialogHeader>

        {/* Content Item List */}
        <div className="flex-1 overflow-y-auto p-3 space-y-1 min-h-[260px] max-h-[380px]">
          {isLoading ? (
            <div className="flex flex-col items-center justify-center py-16 text-muted-foreground gap-2.5">
              <Loader2 className="w-6 h-6 animate-spin text-primary" />
              <span className="text-xs">กำลังอ่านรายการไฟล์จาก Server...</span>
            </div>
          ) : error ? (
            <div className="p-4 rounded-md bg-destructive/10 border border-destructive/20 text-destructive text-xs flex items-start gap-2">
              <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
              <div>
                <span className="font-semibold block">ไม่สามารถเปิดพาธนี้ได้</span>
                <span className="text-muted-foreground">{(error as any)?.response?.data?.message || (error as Error)?.message}</span>
              </div>
            </div>
          ) : filteredItems.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-14 text-muted-foreground gap-2">
              <Folder className="w-8 h-8 opacity-40 text-muted-foreground" />
              <span className="text-xs">
                {filterText ? 'ไม่พบไฟล์หรือโฟลเดอร์ที่ตรงกับคำค้นหา' : 'โฟลเดอร์นี้ว่างเปล่า (Empty directory)'}
              </span>
            </div>
          ) : (
            <div className="space-y-1">
              {filteredItems.map((item) => {
                const isSelected = selectedItem?.path === item.path;
                const isArchive = isArchiveFile(item.extension);
                const isSelectable =
                  mode === 'both' ||
                  (mode === 'folder' && item.isDirectory) ||
                  (mode === 'file' && !item.isDirectory);

                return (
                  <div
                    key={item.path}
                    onClick={() => handleItemClick(item)}
                    onDoubleClick={() => handleFolderDoubleClick(item)}
                    className={`group flex items-center justify-between px-3 py-2 rounded-md text-xs transition-all cursor-pointer select-none border ${
                      isSelected
                        ? 'bg-primary/10 border-primary text-foreground font-medium shadow-xs'
                        : isSelectable
                        ? 'border-transparent hover:bg-secondary/60 text-foreground'
                        : 'border-transparent opacity-50 hover:bg-secondary/30 text-muted-foreground'
                    }`}
                  >
                    <div className="flex items-center gap-2.5 min-w-0 flex-1">
                      {item.isDirectory ? (
                        <Folder className="w-4 h-4 text-amber-500 flex-shrink-0 fill-amber-500/20" />
                      ) : isArchive ? (
                        <FileArchive className="w-4 h-4 text-purple-400 flex-shrink-0" />
                      ) : (
                        <File className="w-4 h-4 text-blue-400 flex-shrink-0" />
                      )}

                      <span className="truncate font-mono">{item.name}</span>

                      {item.isDirectory && (
                        <span className="text-[10px] text-muted-foreground px-1.5 py-0.2 rounded bg-secondary/50 border border-border/50">
                          folder
                        </span>
                      )}
                    </div>

                    <div className="flex items-center gap-4 text-[11px] text-muted-foreground flex-shrink-0 ml-3">
                      <span>{item.isDirectory ? '-' : formatBytes(item.sizeBytes)}</span>
                      <span className="w-28 text-right hidden sm:inline-block">{formatDate(item.lastModified)}</span>

                      {item.isDirectory ? (
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            handleFolderDoubleClick(item);
                          }}
                          className="p-1 hover:bg-primary/20 hover:text-primary rounded text-muted-foreground transition-colors"
                          title="เปิดโฟลเดอร์"
                        >
                          <ChevronRight className="w-3.5 h-3.5" />
                        </button>
                      ) : (
                        <div className="w-5" />
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Footer */}
        <DialogFooter className="p-4 border-t border-border bg-card flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="text-xs text-muted-foreground truncate max-w-full sm:max-w-[50%] flex items-center gap-1.5">
            <span className="font-semibold text-foreground">เลือก:</span>
            <code className="font-mono text-[11px] text-primary truncate max-w-[260px] bg-secondary/60 px-1.5 py-0.5 rounded">
              {selectedPathPreview}
            </code>
          </div>

          <div className="flex items-center gap-2 w-full sm:w-auto justify-end">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => onOpenChange(false)}
              className="text-xs h-9 px-3"
            >
              ยกเลิก
            </Button>

            {mode === 'folder' && (
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={handleSelectCurrentFolder}
                disabled={isLoading || !browseData?.currentPath}
                title="เลือกโฟลเดอร์ปัจจุบันที่กำลังเปิดอยู่"
                className="text-xs h-9 px-3 gap-1.5"
              >
                <Folder className="w-3.5 h-3.5 text-amber-500" />
                ใช้โฟลเดอร์นี้
              </Button>
            )}

            <Button
              type="button"
              size="sm"
              onClick={handleConfirm}
              disabled={
                isLoading ||
                (mode === 'file' && (!selectedItem || selectedItem.isDirectory)) ||
                (mode === 'folder' && !selectedItem && !browseData?.currentPath)
              }
              className="text-xs h-9 px-4 gap-1.5"
            >
              <Check className="w-3.5 h-3.5" />
              ยืนยันการเลือก
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};
