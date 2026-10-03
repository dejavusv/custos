import React, { useEffect, useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Navbar } from './Navbar';
import { useIsMobile } from '../../hooks/useIsMobile';
import { cn } from '../../lib/utils';

export const AppLayout: React.FC = () => {
  const isMobile = useIsMobile();
  // Collapsed by default on mobile, expanded on desktop.
  const [collapsed, setCollapsed] = useState(isMobile);

  // Re-apply the default whenever the viewport crosses the mobile breakpoint.
  useEffect(() => {
    setCollapsed(isMobile);
  }, [isMobile]);

  return (
    <div className="min-h-screen bg-background">
      <Sidebar collapsed={collapsed} isMobile={isMobile} onToggle={() => setCollapsed((c) => !c)} />
      <div
        className={cn(
          'flex flex-col min-h-screen min-w-0 transition-[margin] duration-200 ml-16',
          !collapsed && 'md:ml-64'
        )}
      >
        <Navbar onToggleSidebar={() => setCollapsed((c) => !c)} />
        <main className="flex-1 min-w-0 p-4 md:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
