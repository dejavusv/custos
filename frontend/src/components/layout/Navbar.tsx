import React from 'react';
import { LogOut, Shield } from 'lucide-react';
import { useAuthStore } from '../../stores/authStore';
import { Button } from '../ui/Button';
import { Badge } from '../ui/Badge';

export const Navbar: React.FC = () => {
  const { user, logout } = useAuthStore();

  const handleLogout = () => {
    logout();
    window.location.href = '/login';
  };

  return (
    <header className="h-16 border-b border-border bg-card/60 backdrop-blur-md px-8 flex items-center justify-between sticky top-0 z-30 ml-64">
      <div className="flex items-center gap-3">
        <span className="text-xs text-muted-foreground font-mono">WORKSPACE:</span>
        <Badge variant="outline" className="font-mono text-xs border-primary/30 text-primary bg-primary/5">
          PRODUCTION-ENV
        </Badge>
      </div>

      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-secondary/50 border border-border">
          <Shield className="w-3.5 h-3.5 text-emerald-400" />
          <span className="text-xs font-medium text-muted-foreground">Session Active:</span>
          <span className="text-xs font-semibold text-foreground">{user?.username}</span>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={handleLogout}
          className="gap-2 text-muted-foreground hover:text-destructive hover:border-destructive/40"
        >
          <LogOut className="w-4 h-4" />
          <span>Logout</span>
        </Button>
      </div>
    </header>
  );
};
