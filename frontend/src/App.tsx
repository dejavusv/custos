import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './stores/authStore';
import { LoginPage } from './features/auth/LoginPage';
import { AppLayout } from './components/layout/AppLayout';
import { DashboardPage } from './features/dashboard/DashboardPage';
import { UserManagementPage } from './features/users/UserManagementPage';
import { CredentialsPage } from './features/vault/CredentialsPage';
import { AuditLogsPage } from './features/audit/AuditLogsPage';
import { TasksPage } from './features/tasks/TasksPage';
import { PipelinesPage } from './features/pipelines/PipelinesPage';
import { ConsolePage } from './features/console/ConsolePage';

const ProtectedRoute: React.FC<{ children: React.ReactElement }> = ({ children }) => {
  const { isAuthenticated } = useAuthStore();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return children;
};

const AdminRoute: React.FC<{ children: React.ReactElement }> = ({ children }) => {
  const { user, isAuthenticated } = useAuthStore();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  const hasAccess = user?.roles.some((r) => r === 'ROLE_SUPER_ADMIN' || r === 'ROLE_ADMIN');
  if (!hasAccess) {
    return <Navigate to="/dashboard" replace />;
  }
  return children;
};

export const App: React.FC = () => {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route
        path="/"
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<DashboardPage />} />
        <Route
          path="users"
          element={
            <AdminRoute>
              <UserManagementPage />
            </AdminRoute>
          }
        />
        <Route
          path="vault"
          element={
            <AdminRoute>
              <CredentialsPage />
            </AdminRoute>
          }
        />
        <Route
          path="audit-logs"
          element={
            <AdminRoute>
              <AuditLogsPage />
            </AdminRoute>
          }
        />
        <Route path="tasks" element={<TasksPage />} />
        <Route path="pipelines" element={<PipelinesPage />} />
        <Route path="console" element={<ConsolePage />} />
      </Route>

      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
};
