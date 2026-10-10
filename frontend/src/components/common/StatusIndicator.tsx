import React from 'react';
import './common.css';

export interface StatusIndicatorProps {
  status: 'online' | 'offline' | 'busy' | 'warning';
  label: string;
  sublabel?: string;
  className?: string;
}

export const StatusIndicator: React.FC<StatusIndicatorProps> = ({
  status,
  label,
  sublabel,
  className = '',
}) => {
  return (
    <div className={`app-status-indicator app-status-indicator--${status} ${className}`}>
      <span className="app-status-indicator__dot" aria-hidden="true" />
      <div className="app-status-indicator__text">
        <span className="app-status-indicator__label">{label}</span>
        {sublabel && <span className="app-status-indicator__sublabel">{sublabel}</span>}
      </div>
    </div>
  );
};
