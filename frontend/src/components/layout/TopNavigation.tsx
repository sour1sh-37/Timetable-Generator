import React, { useState, useEffect } from 'react';
import { RefreshCw, Settings, CheckCircle2, AlertCircle, Play, Sun, Moon, Loader2, Palette } from 'lucide-react';
import { Button } from '../common/Button';
import { getStoredAccentColor, getStoredDisplayMode, applyTheme, type DisplayMode } from '../../utils/themeManager';
import type { HealthResponse, BackendLifecycleState } from '../../api/types';
import siesLogo from '../../assets/sies-logo.png';
import './layout.css';

export interface TopNavigationProps {
  health: HealthResponse | null;
  lifecycleState?: BackendLifecycleState;
  isLoading: boolean;
  onRefresh: () => void;
  onOpenSettings: (tab?: 'service' | 'appearance' | 'diagnostics') => void;
  onStartDaemon?: () => void;
}

export const TopNavigation: React.FC<TopNavigationProps> = ({
  health,
  lifecycleState,
  isLoading,
  onRefresh,
  onOpenSettings,
  onStartDaemon,
}) => {
  const isOnline = Boolean(health && health.status === 'UP');
  const isStarting = lifecycleState === 'STARTING' || lifecycleState === 'RECOVERING';
  const isStopping = lifecycleState === 'SHUTTING_DOWN';

  const [mode, setMode] = useState<DisplayMode>(() => getStoredDisplayMode());
  const [accent, setAccent] = useState<string>(() => getStoredAccentColor());

  useEffect(() => {
    // Keep local topnav state in sync
    const handleStorageChange = () => {
      setMode(getStoredDisplayMode());
      setAccent(getStoredAccentColor());
    };
    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, []);

  const handleToggleTheme = () => {
    const currentEffective = document.documentElement.getAttribute('data-theme') || 'light';
    const nextMode: DisplayMode = currentEffective === 'dark' ? 'light' : 'dark';
    setMode(nextMode);
    applyTheme(getStoredAccentColor(), nextMode, true);
  };

  const isDarkMode = (document.documentElement.getAttribute('data-theme') || mode) === 'dark';

  return (
    <header className="app-topbar" aria-label="Global application header">
      {/* Brand Logo & Institutional Title */}
      <div className="app-topbar__brand">
        <div className="app-topbar__crest">
          <img src={siesLogo} alt="SIES institutional emblem" className="app-topbar__logo-img" />
        </div>
        <div className="app-topbar__titles">
          <div className="app-topbar__main-title">
            SIES High School & Junior College
          </div>
          <div className="app-topbar__subtitle">
            Academic Timetable Generator
          </div>
        </div>
      </div>

      {/* Real-time System Status & Actions */}
      <div className="app-topbar__actions">
        {/* Status Dot / Indicator */}
        <div
          className={`app-topbar__connection-pill ${
            isOnline ? 'is-online' : isStarting || isStopping ? 'is-starting' : 'is-offline'
          }`}
          onClick={!isOnline && !isStarting && onStartDaemon ? onStartDaemon : () => onOpenSettings?.('appearance')}
          style={{ cursor: 'pointer' }}
          title={!isOnline ? 'Click to manage or start the scheduling engine' : 'Scheduling engine active'}
        >
          {isStarting || isStopping ? (
            <Loader2 size={13} className="conn-icon spinning" />
          ) : isOnline ? (
            <CheckCircle2 size={13} className="conn-icon" />
          ) : (
            <AlertCircle size={13} className="conn-icon" />
          )}
          <span>
            {isStarting
              ? 'Starting Engine...'
              : isStopping
              ? 'Stopping Engine...'
              : isOnline
              ? `Engine Online`
              : 'Engine Offline'}
          </span>
        </div>

        {/* Quick Start Button when Offline */}
        {!isOnline && !isStarting && !isStopping && onStartDaemon && (
          <Button
            variant="primary"
            size="sm"
            leftIcon={<Play size={12} />}
            onClick={onStartDaemon}
            title="Start Scheduling Engine"
          >
            Start Engine
          </Button>
        )}

        {/* Dark/Light Theme Toggle */}
        <Button
          variant="secondary"
          size="sm"
          leftIcon={isDarkMode ? <Sun size={13} /> : <Moon size={13} />}
          onClick={handleToggleTheme}
          title={isDarkMode ? 'Switch to light mode' : 'Switch to dark mode'}
          aria-label={isDarkMode ? 'Switch to light mode' : 'Switch to dark mode'}
        >
          {isDarkMode ? 'Light' : 'Dark'}
        </Button>

        {/* Custom Theme / Appearance Shortcut */}
        <Button
          variant="secondary"
          size="sm"
          leftIcon={<Palette size={13} />}
          onClick={() => onOpenSettings('appearance')}
          title="Open Custom Colour Picker & Appearance Settings"
          aria-label="Appearance & Accent Color"
        >
          <span
            style={{
              display: 'inline-block',
              width: 8,
              height: 8,
              borderRadius: '50%',
              backgroundColor: accent,
              marginRight: 4,
            }}
          />
          Theme
        </Button>

        {/* Sync Action */}
        <Button
          variant="secondary"
          size="sm"
          leftIcon={<RefreshCw size={13} className={isLoading ? 'spinning' : ''} />}
          onClick={onRefresh}
          disabled={isLoading}
          title="Sync with backend service"
          aria-label="Sync with backend"
        >
          Sync
        </Button>

        {/* Settings */}
        <Button
          variant="secondary"
          size="sm"
          leftIcon={<Settings size={13} />}
          onClick={() => onOpenSettings('service')}
          title="Open engine settings & diagnostics"
          aria-label="Engine settings"
        >
          Settings
        </Button>
      </div>
    </header>
  );
};
