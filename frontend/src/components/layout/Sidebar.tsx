import React from 'react';
import { NavLink } from 'react-router-dom';
import { 
  LayoutDashboard, 
  Users, 
  FileText, 
  Terminal, 
  Layers, 
  HardDrive,
  ShieldAlert,
  KeyRound
} from 'lucide-react';
import { useAuthStore } from '../../stores/authStore';
import { cn } from '../../lib/utils';

export const Sidebar: React.FC = () => {
  const { user } = useAuthStore();
  const isAdminOrSuper = user?.roles.some(r => r === 'ROLE_SUPER_ADMIN' || r === 'ROLE_ADMIN');

  const navItems = [
    { name: 'Dashboard', path: '/dashboard', icon: LayoutDashboard },
    ...(isAdminOrSuper ? [{ name: 'User Management', path: '/users', icon: Users }] : []),
    ...(isAdminOrSuper ? [{ name: 'Credentials Vault', path: '/vault', icon: KeyRound }] : []),
    { name: 'Tasks', path: '/tasks', icon: HardDrive },
    { name: 'Pipelines', path: '/pipelines', icon: Layers },
    { name: 'Live Console', path: '/console', icon: Terminal },
    ...(isAdminOrSuper ? [{ name: 'Audit Logs', path: '/audit-logs', icon: FileText }] : []),
  ];

  return (
    <aside className="w-64 border-r border-border bg-card flex flex-col h-screen fixed left-0 top-0 z-40">
      {/* Brand Header */}
      <div className="h-16 flex items-center px-6 border-b border-border gap-3">
        <div className="w-9 h-9 rounded-lg bg-primary/10 border border-primary/20 flex items-center justify-center text-primary font-bold shadow-sm">
          <ShieldAlert className="w-5 h-5 text-primary" />
        </div>
        <div>
          <h1 className="font-bold text-base tracking-tight text-foreground">CUSTOS</h1>
          <p className="text-[10px] text-muted-foreground uppercase tracking-widest font-mono">Task Platform</p>
        </div>
      </div>

      {/* Navigation */}
      <div className="flex-1 py-6 px-3 space-y-1">
        <div className="px-3 pb-2 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
          Platform Menu
        </div>
        {navItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all',
                  isActive
                    ? 'bg-primary text-primary-foreground shadow-sm'
                    : 'text-muted-foreground hover:text-foreground hover:bg-secondary/50'
                )
              }
            >
              <Icon className="w-4 h-4" />
              <span>{item.name}</span>
            </NavLink>
          );
        })}
      </div>

      {/* User Info Footer */}
      <div className="p-4 border-t border-border bg-secondary/20">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-full bg-primary/20 border border-primary/30 flex items-center justify-center text-primary font-bold text-sm">
            {user?.username ? user.username.substring(0, 2).toUpperCase() : 'CU'}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-semibold truncate text-foreground">{user?.username}</p>
            <p className="text-xs text-muted-foreground truncate">{user?.roles?.[0] || 'User'}</p>
          </div>
        </div>
      </div>
    </aside>
  );
};
