import React from 'react';
import { RefreshCw, Settings, Activity } from 'lucide-react';
import { StatusIndicator } from '../common/StatusIndicator';
import { Button } from '../common/Button';
import type { HealthResponse } from '../../api/types';
import siesLogo from '../../assets/sies-logo.png';
import './layout.css';

export interface TopNavigationProps {
  health: HealthResponse | null;
  isLoading: boolean;
  onRefresh: () => void;
  onOpenSettings: () => void;
}

export const TopNavigation: React.FC<TopNavigationProps> = ({
  health,
  isLoading,
  onRefresh,
  onOpenSettings,
}) => {
  return (
    <header className="app-topbar">
      {/* Brand Logo & Title Cluster */}
      <div className="app-topbar__brand">
        <div className="app-topbar__crest">
          <img src={siesLogo} alt="SIES institutional logo" className="app-topbar__logo-img" />
        </div>
        <div className="app-topbar__titles">
          <div className="app-topbar__main-title">
            SIES (Dr APJ Abdul Kalam Memorial) High School
          </div>
          <div className="app-topbar__subtitle">
            Next-Gen Academic Timetable Generator & Multi-Constraint Optimizer
            <span className="app-topbar__phase-badge">Desktop Edition</span>
          </div>
        </div>
      </div>

      {/* Real-time System Status Cluster */}
      <div className="app-topbar__actions">
        {/* Authoritative Solver Tag */}
        <div className="app-topbar__solver-pill">
          <Activity size={14} className="text-crimson" />
          <span>OR-Tools CP-SAT</span>
          <span className="app-topbar__solver-status">
            {health?.solver.status === 'AVAILABLE' ? 'Authoritative' : 'Standby'}
          </span>
        </div>

        {/* Live Loopback Status Indicator */}
        <StatusIndicator
          status={health ? 'online' : 'offline'}
          label={health ? `127.0.0.1:${health.port}` : 'Daemon Disconnected'}
          sublabel={health ? 'Virtual Threads Loopback' : 'Reconnecting...'}
        />

        {/* Action Controls */}
        <Button
          variant="secondary"
          size="sm"
          leftIcon={<RefreshCw size={14} className={isLoading ? 'spinning' : ''} />}
          onClick={onRefresh}
          disabled={isLoading}
          title="Refresh Backend Telemetry"
          aria-label="Refresh telemetry"
        >
          Sync
        </Button>

        <Button
          variant="outline"
          size="sm"
          leftIcon={<Settings size={14} />}
          onClick={onOpenSettings}
          title="Loopback Endpoint Settings"
          aria-label="Open settings dialog"
        >
          Endpoint
        </Button>
      </div>
    </header>
  );
};
