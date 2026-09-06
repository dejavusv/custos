import React, { useState, useEffect } from 'react';
import { X, Save, Settings } from 'lucide-react';
import { Button } from '../../../components/ui/Button';
import { TaskType } from '../../../types/pipeline';

interface NodeConfigModalProps {
  isOpen: boolean;
  onClose: () => void;
  nodeKey: string;
  nodeLabel: string;
  nodeType: TaskType;
  configJson: string;
  onSave: (updatedLabel: string, updatedConfigJson: string) => void;
}

export const NodeConfigModal: React.FC<NodeConfigModalProps> = ({
  isOpen,
  onClose,
  nodeKey,
  nodeLabel,
  nodeType,
  configJson,
  onSave,
}) => {
  const [label, setLabel] = useState(nodeLabel);
  const [formFields, setFormFields] = useState<Record<string, string>>({});

  useEffect(() => {
    setLabel(nodeLabel);
    try {
      const parsed = JSON.parse(configJson || '{}');
      const mapped: Record<string, string> = {};
      for (const [k, v] of Object.entries(parsed)) {
        mapped[k] = String(v ?? '');
      }
      setFormFields(mapped);
    } catch {
      setFormFields({});
    }
  }, [nodeLabel, configJson, isOpen]);

  if (!isOpen) return null;

  const handleFieldChange = (key: string, value: string) => {
    setFormFields((prev) => ({ ...prev, [key]: value }));
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    onSave(label, JSON.stringify(formFields));
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-in fade-in duration-150">
      <div className="bg-slate-900 border border-slate-800 w-full max-w-lg rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="px-6 py-4 border-b border-slate-800 flex items-center justify-between bg-slate-900/80">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-primary/10 border border-primary/20 text-primary flex items-center justify-center">
              <Settings className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-white">Configure Step: {nodeLabel}</h2>
              <p className="text-xs text-slate-400 font-mono">Key: {nodeKey} | Type: {nodeType}</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSave} className="p-6 space-y-4 overflow-y-auto flex-1 text-sm">
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1.5">
              Step Display Name
            </label>
            <input
              type="text"
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-primary text-sm"
              required
            />
          </div>

          {nodeType === 'DATABASE_BACKUP' && (
            <>
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Database Name
                </label>
                <input
                  type="text"
                  placeholder="e.g. production_db or ${context.db}"
                  value={formFields.databaseName || ''}
                  onChange={(e) => handleFieldChange('databaseName', e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-primary text-sm font-mono"
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-300 mb-1.5">
                    Host
                  </label>
                  <input
                    type="text"
                    placeholder="localhost"
                    value={formFields.host || ''}
                    onChange={(e) => handleFieldChange('host', e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-primary text-sm font-mono"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-300 mb-1.5">
                    Port
                  </label>
                  <input
                    type="number"
                    placeholder="5432"
                    value={formFields.port || ''}
                    onChange={(e) => handleFieldChange('port', e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-primary text-sm font-mono"
                  />
                </div>
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Destination Directory
                </label>
                <input
                  type="text"
                  placeholder="storage/backups"
                  value={formFields.destinationDir || ''}
                  onChange={(e) => handleFieldChange('destinationDir', e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-primary text-sm font-mono"
                />
              </div>
            </>
          )}

          {nodeType === 'FILE_BACKUP' && (
            <>
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Source Directory Path
                </label>
                <input
                  type="text"
                  placeholder="e.g. /var/www/uploads or storage/data"
                  value={formFields.sourcePath || ''}
                  onChange={(e) => handleFieldChange('sourcePath', e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-primary text-sm font-mono"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Destination Directory
                </label>
                <input
                  type="text"
                  placeholder="storage/backups"
                  value={formFields.destinationDir || ''}
                  onChange={(e) => handleFieldChange('destinationDir', e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-primary text-sm font-mono"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Custom Archive Filename
                </label>
                <input
                  type="text"
                  placeholder="e.g. web-assets.tar.gz"
                  value={formFields.customFileName || ''}
                  onChange={(e) => handleFieldChange('customFileName', e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-primary text-sm font-mono"
                />
              </div>
            </>
          )}

          {nodeType === 'SPLIT_TRANSFER' && (
            <>
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Source File Path
                </label>
                <input
                  type="text"
                  placeholder="Leave empty or use ${last_output_path}"
                  value={formFields.sourceFilePath || ''}
                  onChange={(e) => handleFieldChange('sourceFilePath', e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-primary text-sm font-mono"
                />
                <p className="text-[11px] text-slate-500 mt-1">
                  Supports dynamic context passing: {'${last_output_path}'}
                </p>
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Remote Destination Directory
                </label>
                <input
                  type="text"
                  placeholder="/upload"
                  value={formFields.remoteDirectory || ''}
                  onChange={(e) => handleFieldChange('remoteDirectory', e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-primary text-sm font-mono"
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-300 mb-1.5">
                    Chunk Size (Bytes)
                  </label>
                  <input
                    type="number"
                    placeholder="104857600 (100MB)"
                    value={formFields.chunkSizeBytes || ''}
                    onChange={(e) => handleFieldChange('chunkSizeBytes', e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-primary text-sm font-mono"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-300 mb-1.5">
                    Max Retries per Chunk
                  </label>
                  <input
                    type="number"
                    placeholder="3"
                    value={formFields.maxRetries || ''}
                    onChange={(e) => handleFieldChange('maxRetries', e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-primary text-sm font-mono"
                  />
                </div>
              </div>
            </>
          )}

          {nodeType === 'EMAIL_ALERT' && (
            <>
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Recipient Email
                </label>
                <input
                  type="email"
                  placeholder="ops@company.com"
                  value={formFields.recipient || ''}
                  onChange={(e) => handleFieldChange('recipient', e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-primary text-sm"
                  required
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Email Subject
                </label>
                <input
                  type="text"
                  placeholder="e.g. Pipeline Complete: ${last_output_path}"
                  value={formFields.subject || ''}
                  onChange={(e) => handleFieldChange('subject', e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-primary text-sm"
                  required
                />
              </div>
            </>
          )}

          <div className="pt-4 border-t border-slate-800 flex items-center justify-end gap-3">
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" className="gap-2">
              <Save className="w-4 h-4" />
              Apply Changes
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
