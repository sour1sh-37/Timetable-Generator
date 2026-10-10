import React from 'react';
import { Cpu, Database, Command } from 'lucide-react';
import type { HealthResponse } from '../../api/types';
import './layout.css';

export interface FooterBarProps {
  health: HealthResponse | null;
  lastUpdated: Date | null;
}

export const FooterBar: React.FC<FooterBarProps> = ({ health, lastUpdated }) => {
  const formatBytes = (bytes?: number) => {
    if (!bytes) return '0 MB';
    return `${(bytes / (1024 * 1024)).toFixed(0)} MB`;
  };

  return (
    <footer className="app-footer">
      <div className="app-footer__brand">
        <span>SIES (Dr APJ Abdul Kalam Memorial) High School • Timetable Generator & Optimizer</span>
      </div>

      <div className="app-footer__telemetry">
        {health && (
          <>
            <span className="app-footer__item">
              <Cpu size={12} className="text-terracotta" />
              <span>PID {health.jvm.pid} ({health.jvm.availableProcessors} Cores)</span>
            </span>
            <span className="app-footer__separator">•</span>
            <span className="app-footer__item">
              <Database size={12} className="text-gold" />
              <span>Heap: {formatBytes((health.jvm.totalMemoryBytes - health.jvm.freeMemoryBytes))} / {formatBytes(health.jvm.maxMemoryBytes)}</span>
            </span>
            <span className="app-footer__separator">•</span>
          </>
        )}
        <span className="app-footer__item">
          <span>Synced: {lastUpdated ? lastUpdated.toLocaleTimeString() : '—'}</span>
        </span>
        <span className="app-footer__separator">•</span>
        <span className="app-footer__item app-footer__shortcuts">
          <Command size={11} />
          <span>Tab / Shift+Tab Navigate</span>
        </span>
      </div>
    </footer>
  );
};
