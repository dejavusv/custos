import React from 'react';
import { LogOut, Menu, Shield } from 'lucide-react';
import { useAuthStore } from '../../stores/authStore';
import { Button } from '../ui/Button';
import { Badge } from '../ui/Badge';

interface NavbarProps {
  onToggleSidebar: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ onToggleSidebar }) => {
  const { user, logout } = useAuthStore();

  const handleLogout = () => {
    logout();
    window.location.href = '/login';
  };

  return (
    <header className="h-16 border-b border-border bg-card/60 backdrop-blur-md px-4 md:px-8 flex items-center justify-between gap-3 sticky top-0 z-20">
      <div className="flex items-center gap-3 min-w-0">
        <Button
          variant="ghost"
          size="sm"
          onClick={onToggleSidebar}
          aria-label="Toggle menu"
          className="h-9 w-9 p-0 text-muted-foreground hover:text-foreground"
        >
          <Menu className="w-5 h-5" />
        </Button>
        <span className="hidden sm:inline text-xs text-muted-foreground font-mono">WORKSPACE:</span>
        <Badge variant="outline" className="hidden sm:inline-flex font-mono text-xs border-primary/30 text-primary bg-primary/5">
          PRODUCTION-ENV
        </Badge>
      </div>

      <div className="flex items-center gap-2 sm:gap-4 min-w-0">
        <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-secondary/50 border border-border min-w-0">
          <Shield className="w-3.5 h-3.5 shrink-0 text-emerald-400" />
          <span className="hidden sm:inline text-xs font-medium text-muted-foreground">Session Active:</span>
          <span className="text-xs font-semibold text-foreground truncate max-w-[96px] sm:max-w-none">{user?.username}</span>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={handleLogout}
          aria-label="Logout"
          className="gap-2 text-muted-foreground hover:text-destructive hover:border-destructive/40"
        >
          <LogOut className="w-4 h-4" />
          <span className="hidden sm:inline">Logout</span>
        </Button>
      </div>
    </header>
  );
};
