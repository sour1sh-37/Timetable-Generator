import React from 'react';
import { TopNavigation } from './TopNavigation';
import { Sidebar, type ActiveNavView, type SetupTab, type RulesTab } from './Sidebar';
import { FooterBar } from './FooterBar';
import type { HealthResponse, BackendLifecycleState } from '../../api/types';
import './layout.css';

export interface AppShellProps {
  activeView: ActiveNavView;
  activeSetupTab?: SetupTab;
  activeRulesTab?: RulesTab;
  onSelectView: (view: ActiveNavView, setupTab?: SetupTab, rulesTab?: RulesTab) => void;
  health: HealthResponse | null;
  lifecycleState?: BackendLifecycleState;
  isLoading: boolean;
  lastUpdated: Date | null;
  hasTimetable?: boolean;
  isSolving?: boolean;
  onRefresh: () => void;
  onOpenSettings: () => void;
  onStartDaemon?: () => void;
  children: React.ReactNode;
}

export const AppShell: React.FC<AppShellProps> = ({
  activeView,
  activeSetupTab,
  activeRulesTab,
  onSelectView,
  health,
  lifecycleState,
  isLoading,
  lastUpdated,
  hasTimetable,
  isSolving,
  onRefresh,
  onOpenSettings,
  onStartDaemon,
  children,
}) => {
  return (
    <div className="app-shell-root">
      {/* Top Application Bar */}
      <TopNavigation
        health={health}
        lifecycleState={lifecycleState}
        isLoading={isLoading}
        onRefresh={onRefresh}
        onOpenSettings={onOpenSettings}
        onStartDaemon={onStartDaemon}
      />

      {/* Main Workspace Frame */}
      <div className="app-shell-workspace">
        <Sidebar
          activeView={activeView}
          activeSetupTab={activeSetupTab}
          activeRulesTab={activeRulesTab}
          onSelectView={onSelectView}
          solverAvailable={health?.solver.status === 'AVAILABLE'}
          isSolving={isSolving}
          hasTimetable={hasTimetable}
        />

        <main className="app-shell-content" id="main-content" tabIndex={-1}>
          {children}
        </main>
      </div>

      {/* Bottom Status / Telemetry Footer */}
      <FooterBar health={health} lifecycleState={lifecycleState} lastUpdated={lastUpdated} />
    </div>
  );
};

export default AppShell;
