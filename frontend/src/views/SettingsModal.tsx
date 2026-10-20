import React, { useState, useEffect } from 'react';
import {
  Server,
  Power,
  ShieldCheck,
  Check,
  Sun,
  Moon,
  Laptop,
  Palette,
  Play,
  RotateCcw,
  CheckCircle2,
  Cpu,
  ChevronDown,
  ChevronRight,
} from 'lucide-react';
import { Modal } from '../components/common/Modal';
import { Button } from '../components/common/Button';
import { Badge } from '../components/common/Badge';
import { ColorPicker } from '../components/common/ColorPicker';
import {
  getStoredAccentColor,
  getStoredDisplayMode,
  applyTheme,
  DEFAULT_ACCENT,
  type DisplayMode,
} from '../utils/themeManager';
import type { HealthResponse, BackendLifecycleState } from '../api/types';
import './views.css';

export interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentBaseUrl: string;
  onApplyBaseUrl: (url: string) => void;
  health: HealthResponse | null;
  lifecycleState?: BackendLifecycleState;
  isLifecycleBusy?: boolean;
  onStartDaemon?: () => Promise<void>;
  onShutdownDaemon: () => Promise<void>;
  onRestartDaemon?: () => Promise<void>;
  initialTab?: 'service' | 'appearance' | 'diagnostics';
}

export const SettingsModal: React.FC<SettingsModalProps> = ({
  isOpen,
  onClose,
  currentBaseUrl,
  onApplyBaseUrl,
  health,
  lifecycleState = health?.status === 'UP' ? 'ONLINE' : 'OFFLINE',
  isLifecycleBusy = false,
  onStartDaemon,
  onShutdownDaemon,
  onRestartDaemon,
  initialTab = 'appearance',
}) => {
  const [urlInput, setUrlInput] = useState<string>(currentBaseUrl);
  const [shutdownConfirmed, setShutdownConfirmed] = useState<boolean>(false);
  const [activeTab, setActiveTab] = useState<'service' | 'appearance' | 'diagnostics'>(initialTab);
  const [showRawJson, setShowRawJson] = useState<boolean>(false);

  const [accentColor, setAccentColor] = useState<string>(() => getStoredAccentColor());
  const [displayMode, setDisplayMode] = useState<DisplayMode>(() => getStoredDisplayMode());
  const [accentSuccessMsg, setAccentSuccessMsg] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      setAccentColor(getStoredAccentColor());
      setDisplayMode(getStoredDisplayMode());
      setAccentSuccessMsg(null);
    }
  }, [isOpen]);

  const isOnline = Boolean(health && health.status === 'UP');

  const handleSelectMode = (newMode: DisplayMode) => {
    setDisplayMode(newMode);
    applyTheme(accentColor, newMode, true);
  };

  const handleApplyAccent = (hex: string) => {
    setAccentColor(hex);
    applyTheme(hex, displayMode, true);
    setAccentSuccessMsg(`Accent colour updated to ${hex.toUpperCase()}!`);
    setTimeout(() => setAccentSuccessMsg(null), 3000);
  };

  const handleResetAccent = () => {
    setAccentColor(DEFAULT_ACCENT);
    applyTheme(DEFAULT_ACCENT, displayMode, true);
    setAccentSuccessMsg(`Accent colour reset to SIES Burgundy (${DEFAULT_ACCENT})!`);
    setTimeout(() => setAccentSuccessMsg(null), 3000);
  };

  const handlePreviewAccent = (hex: string) => {
    // Live preview in DOM without persisting to storage yet
    applyTheme(hex, displayMode, false);
  };

  const handleClose = () => {
    // Ensure active theme matches persisted state
    applyTheme(getStoredAccentColor(), getStoredDisplayMode(), false);
    onClose();
  };

  const handleSaveConnection = (e: React.FormEvent) => {
    e.preventDefault();
    onApplyBaseUrl(urlInput);
    handleClose();
  };

  const handleShutdownClick = async () => {
    if (!shutdownConfirmed) {
      setShutdownConfirmed(true);
      return;
    }
    try {
      await onShutdownDaemon();
      setShutdownConfirmed(false);
    } catch (e) {
      console.error('Shutdown failed:', e);
    }
  };

  const handleStartClick = async () => {
    if (onStartDaemon) {
      try {
        await onStartDaemon();
      } catch (e) {
        console.error('Start failed:', e);
      }
    }
  };

  const handleRestartClick = async () => {
    if (onRestartDaemon) {
      try {
        await onRestartDaemon();
      } catch (e) {
        console.error('Restart failed:', e);
      }
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title={
        <span className="modal-title-cluster" style={{ display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
          <Palette size={20} className="text-brand" />
          <span>System Settings & Appearance</span>
        </span>
      }
      description="Customize visual themes, full-spectrum accent colors, and background timetable engine services."
      size="lg"
    >
      <div className="settings-tab-bar">
        <button
          className={`settings-tab-btn ${activeTab === 'appearance' ? 'is-active' : ''}`}
          onClick={() => setActiveTab('appearance')}
        >
          Appearance & Themes
        </button>
        <button
          className={`settings-tab-btn ${activeTab === 'service' ? 'is-active' : ''}`}
          onClick={() => setActiveTab('service')}
        >
          Service & Connection
        </button>
        <button
          className={`settings-tab-btn ${activeTab === 'diagnostics' ? 'is-active' : ''}`}
          onClick={() => setActiveTab('diagnostics')}
        >
          Engine Status & Diagnostics
        </button>
      </div>

      {activeTab === 'appearance' && (
        <div className="settings-appearance-panel" style={{ padding: '0.5rem 0', display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          {/* Display Mode Selection */}
          <div className="form-group">
            <label className="form-label" style={{ fontSize: 'var(--text-sm)', fontWeight: 700 }}>
              Display Mode
            </label>
            <span className="form-hint">
              Select between light mode for bright campus environments, dark mode for reduced glare, or match system settings.
            </span>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '0.85rem', marginTop: '0.75rem' }}>
              <button
                type="button"
                className={`theme-card ${displayMode === 'light' ? 'is-active' : ''}`}
                onClick={() => handleSelectMode('light')}
                style={{
                  padding: '0.85rem 1rem',
                  borderRadius: 'var(--radius-md)',
                  border: displayMode === 'light' ? '2px solid var(--color-brand)' : '1px solid var(--surface-border)',
                  background: 'var(--bg-surface)',
                  color: 'var(--color-text-primary)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.65rem',
                  cursor: 'pointer',
                  textAlign: 'left',
                }}
              >
                <Sun size={20} className={displayMode === 'light' ? 'text-brand' : ''} />
                <div>
                  <strong style={{ display: 'block', fontSize: 'var(--text-sm)' }}>Light Mode</strong>
                  <span style={{ fontSize: '0.72rem', color: 'var(--color-text-tertiary)' }}>Institutional ivory</span>
                </div>
              </button>

              <button
                type="button"
                className={`theme-card ${displayMode === 'dark' ? 'is-active' : ''}`}
                onClick={() => handleSelectMode('dark')}
                style={{
                  padding: '0.85rem 1rem',
                  borderRadius: 'var(--radius-md)',
                  border: displayMode === 'dark' ? '2px solid var(--color-brand)' : '1px solid var(--surface-border)',
                  background: 'var(--bg-surface)',
                  color: 'var(--color-text-primary)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.65rem',
                  cursor: 'pointer',
                  textAlign: 'left',
                }}
              >
                <Moon size={20} className={displayMode === 'dark' ? 'text-brand' : ''} />
                <div>
                  <strong style={{ display: 'block', fontSize: 'var(--text-sm)' }}>Dark Mode</strong>
                  <span style={{ fontSize: '0.72rem', color: 'var(--color-text-tertiary)' }}>Charcoal & slate</span>
                </div>
              </button>

              <button
                type="button"
                className={`theme-card ${displayMode === 'system' ? 'is-active' : ''}`}
                onClick={() => handleSelectMode('system')}
                style={{
                  padding: '0.85rem 1rem',
                  borderRadius: 'var(--radius-md)',
                  border: displayMode === 'system' ? '2px solid var(--color-brand)' : '1px solid var(--surface-border)',
                  background: 'var(--bg-surface)',
                  color: 'var(--color-text-primary)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.65rem',
                  cursor: 'pointer',
                  textAlign: 'left',
                }}
              >
                <Laptop size={20} className={displayMode === 'system' ? 'text-brand' : ''} />
                <div>
                  <strong style={{ display: 'block', fontSize: 'var(--text-sm)' }}>System Mode</strong>
                  <span style={{ fontSize: '0.72rem', color: 'var(--color-text-tertiary)' }}>Auto OS match</span>
                </div>
              </button>
            </div>
          </div>

          {/* Full-Spectrum Custom Accent Color Picker */}
          <div className="form-group">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
              <label className="form-label" style={{ fontSize: 'var(--text-sm)', fontWeight: 700 }}>
                Custom UI Accent Colour
              </label>
              <span style={{ fontSize: '0.75rem', color: 'var(--color-text-tertiary)' }}>
                Active: <code style={{ color: 'var(--color-brand)', fontWeight: 700 }}>{accentColor}</code>
              </span>
            </div>
            <span className="form-hint">
              Customize the application accent color. Primary buttons, navigation highlights, active tabs, and sliders will update in real time.
            </span>

            {accentSuccessMsg && (
              <div className="validation-alert-banner is-pass" style={{ marginTop: '0.5rem' }}>
                <CheckCircle2 size={16} />
                <span>{accentSuccessMsg}</span>
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'center', marginTop: '1rem' }}>
              <ColorPicker
                initialColor={accentColor}
                onApply={handleApplyAccent}
                onResetDefault={handleResetAccent}
                onCancel={handleClose}
                onPreviewChange={handlePreviewAccent}
              />
            </div>
          </div>

          <div className="modal-actions-bar" style={{ marginTop: '0.75rem' }}>
            <Button type="button" variant="primary" size="md" onClick={handleClose} leftIcon={<Check size={14} />}>
              Done
            </Button>
          </div>
        </div>
      )}

      {activeTab === 'service' && (
        <div className="settings-service-panel" style={{ padding: '0.5rem 0' }}>
          {/* Live Engine Status Card */}
          <div
            style={{
              padding: '1rem',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--surface-border)',
              backgroundColor: 'var(--bg-subtle)',
              marginBottom: '1.25rem',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <strong style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)' }}>
                  Optimization Engine Status
                </strong>
                <p style={{ margin: '0.2rem 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-tertiary)' }}>
                  {isOnline
                    ? `Actively running on port ${health?.port ?? 8080} (Google OR-Tools CP-SAT)`
                    : lifecycleState === 'STARTING'
                    ? 'Launching Java daemon process and verifying loopback response...'
                    : lifecycleState === 'RECOVERING'
                    ? 'Restarting Java process...'
                    : 'The background optimization engine is currently stopped.'}
                </p>
              </div>
              <Badge
                variant={
                  isOnline
                    ? 'jade'
                    : lifecycleState === 'STARTING' || lifecycleState === 'RECOVERING'
                    ? 'gold'
                    : 'terracotta'
                }
                size="md"
              >
                {isOnline
                  ? 'ONLINE'
                  : lifecycleState === 'STARTING'
                  ? 'STARTING...'
                  : lifecycleState === 'RECOVERING'
                  ? 'RECOVERING...'
                  : 'OFFLINE'}
              </Badge>
            </div>

            {/* Lifecycle Control Buttons */}
            <div style={{ display: 'flex', gap: '0.65rem', marginTop: '1rem', flexWrap: 'wrap' }}>
              {!isOnline ? (
                <Button
                  type="button"
                  variant="primary"
                  size="sm"
                  leftIcon={<Play size={14} />}
                  isLoading={isLifecycleBusy || lifecycleState === 'STARTING'}
                  onClick={handleStartClick}
                >
                  Start Scheduling Engine
                </Button>
              ) : (
                <>
                  <Button
                    type="button"
                    variant={shutdownConfirmed ? 'danger' : 'outline'}
                    size="sm"
                    leftIcon={<Power size={14} />}
                    isLoading={isLifecycleBusy || lifecycleState === 'SHUTTING_DOWN'}
                    onClick={handleShutdownClick}
                  >
                    {shutdownConfirmed ? 'Click Again to Confirm Shutdown' : 'Shut Down Engine'}
                  </Button>

                  {onRestartDaemon && (
                    <Button
                      type="button"
                      variant="secondary"
                      size="sm"
                      leftIcon={<RotateCcw size={14} />}
                      isLoading={isLifecycleBusy || lifecycleState === 'RECOVERING'}
                      onClick={handleRestartClick}
                    >
                      Restart Engine
                    </Button>
                  )}
                </>
              )}
            </div>
          </div>

          {/* Connection Endpoint Configuration Form */}
          <form onSubmit={handleSaveConnection} className="settings-form">
            <div className="form-group">
              <label className="form-label" htmlFor="endpoint-url">
                Local Engine Endpoint URL
              </label>
              <input
                id="endpoint-url"
                type="text"
                value={urlInput}
                onChange={(e) => setUrlInput(e.target.value)}
                placeholder="http://127.0.0.1:8080"
                className="form-input"
              />
              <span className="form-hint">
                Default: <code>http://127.0.0.1:8080</code>. Communicates strictly via local loopback for desktop privacy.
              </span>
            </div>

            <div className="security-notice-box">
              <ShieldCheck size={16} className="text-jade" />
              <div>
                <strong>Local Loopback Privacy Enforced</strong>
                <p>Data remains entirely local on this machine; no scheduling data or rosters are sent to external servers.</p>
              </div>
            </div>

            <div className="modal-actions-bar" style={{ marginTop: '1.25rem' }}>
              <Button type="button" variant="secondary" size="md" onClick={handleClose}>
                Cancel
              </Button>
              <Button type="submit" variant="primary" size="md" leftIcon={<Check size={14} />}>
                Save Connection
              </Button>
            </div>
          </form>
        </div>
      )}

      {activeTab === 'diagnostics' && (
        <div className="diagnostics-panel" style={{ padding: '0.5rem 0' }}>
          {/* User-friendly telemetry cards */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem', marginBottom: '1.25rem' }}>
            <div style={{ padding: '0.75rem', borderRadius: 'var(--radius-md)', border: '1px solid var(--surface-border)', background: 'var(--bg-subtle)' }}>
              <div style={{ fontSize: '0.75rem', color: 'var(--color-text-tertiary)', display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <Cpu size={14} />
                <span>Solver Engine</span>
              </div>
              <strong style={{ display: 'block', marginTop: '0.25rem', fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)' }}>
                {health?.solver.engine ?? 'Google OR-Tools CP-SAT'}
              </strong>
              <span style={{ fontSize: '0.72rem', color: 'var(--color-text-tertiary)' }}>
                Version: {health?.solver.version ?? '9.11'} • Two-Phase Optimizer
              </span>
            </div>

            <div style={{ padding: '0.75rem', borderRadius: 'var(--radius-md)', border: '1px solid var(--surface-border)', background: 'var(--bg-subtle)' }}>
              <div style={{ fontSize: '0.75rem', color: 'var(--color-text-tertiary)', display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <Server size={14} />
                <span>Runtime Environment</span>
              </div>
              <strong style={{ display: 'block', marginTop: '0.25rem', fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)' }}>
                Java {health?.jvm?.version ?? '21'} ({health?.jvm?.vendor ?? 'Temurin'})
              </strong>
              <span style={{ fontSize: '0.72rem', color: 'var(--color-text-tertiary)' }}>
                Process ID: {health?.jvm?.pid ?? '—'}
              </span>
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: 'var(--text-xs)', fontWeight: 600, color: 'var(--color-text-secondary)' }}>
              Raw Diagnostics Payload
            </span>
            <button
              type="button"
              onClick={() => setShowRawJson(!showRawJson)}
              style={{
                background: 'transparent',
                border: 'none',
                cursor: 'pointer',
                fontSize: '0.72rem',
                color: 'var(--color-brand)',
                display: 'flex',
                alignItems: 'center',
                gap: '0.2rem',
              }}
            >
              {showRawJson ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
              <span>{showRawJson ? 'Collapse JSON' : 'Expand Raw JSON'}</span>
            </button>
          </div>

          {showRawJson && (
            <div className="diagnostics-viewer">
              <pre className="diagnostics-pre">{JSON.stringify(health, null, 2)}</pre>
            </div>
          )}

          <div className="modal-actions-bar" style={{ marginTop: '1.25rem' }}>
            <Button type="button" variant="primary" size="md" onClick={handleClose}>
              Close
            </Button>
          </div>
        </div>
      )}
    </Modal>
  );
};
