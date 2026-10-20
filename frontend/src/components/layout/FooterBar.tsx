import type { HealthResponse, BackendLifecycleState } from '../../api/types';
import './layout.css';

export interface FooterBarProps {
  health: HealthResponse | null;
  lifecycleState?: BackendLifecycleState;
  lastUpdated: Date | null;
}

export const FooterBar: React.FC<FooterBarProps> = ({ health, lifecycleState, lastUpdated }) => {
  const isOnline = Boolean(health?.status === 'UP');
  const isStarting = lifecycleState === 'STARTING' || lifecycleState === 'RECOVERING';
  const isStopping = lifecycleState === 'SHUTTING_DOWN';

  const statusLabel = isStarting
    ? 'Starting Engine...'
    : isStopping
    ? 'Stopping Engine...'
    : isOnline
    ? 'Scheduling Engine Ready'
    : 'Engine Offline';

  const dotClass = isStarting || isStopping ? 'is-yellow' : isOnline ? 'is-green' : 'is-red';

  return (
    <footer className="app-footer" aria-label="Application status footer">
      <div className="app-footer__brand">
        <span>SIES High School & Junior College • Academic Timetable Management</span>
      </div>

      <div className="app-footer__telemetry">
        <span className="app-footer__item">
          <span className={`status-pill-dot ${dotClass}`} />
          <span>{statusLabel}</span>
        </span>
        <span className="app-footer__separator">•</span>
        <span className="app-footer__item">
          <span>Synced: {lastUpdated ? lastUpdated.toLocaleTimeString() : '—'}</span>
        </span>
      </div>
    </footer>
  );
};
