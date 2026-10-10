import React from 'react';
import { TopNavigation } from './TopNavigation';
import { Sidebar, type ActiveNavView } from './Sidebar';
import { FooterBar } from './FooterBar';
import type { HealthResponse } from '../../api/types';
import './layout.css';

export interface AppShellProps {
  activeView: ActiveNavView;
  onSelectView: (view: ActiveNavView) => void;
  health: HealthResponse | null;
  isLoading: boolean;
  lastUpdated: Date | null;
  onRefresh: () => void;
  onOpenSettings: () => void;
  children: React.ReactNode;
}

export const AppShell: React.FC<AppShellProps> = ({
  activeView,
  onSelectView,
  health,
  isLoading,
  lastUpdated,
  onRefresh,
  onOpenSettings,
  children,
}) => {
  return (
    <div className="app-shell-root">
      {/* Top Application Bar */}
      <TopNavigation
        health={health}
        isLoading={isLoading}
        onRefresh={onRefresh}
        onOpenSettings={onOpenSettings}
      />

      {/* Main Workspace Frame */}
      <div className="app-shell-workspace">
        <Sidebar
          activeView={activeView}
          onSelectView={onSelectView}
          solverAvailable={health?.solver.status === 'AVAILABLE'}
        />

        <main className="app-shell-content" id="main-content" tabIndex={-1}>
          {children}
        </main>
      </div>

      {/* Bottom Status / Telemetry Footer */}
      <FooterBar health={health} lastUpdated={lastUpdated} />
    </div>
  );
};
