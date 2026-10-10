import React, { useState } from 'react';
import { Server, Power, ShieldCheck, Check } from 'lucide-react';
import { Modal } from '../components/common/Modal';
import { Button } from '../components/common/Button';
import type { HealthResponse } from '../api/types';
import './views.css';

export interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentBaseUrl: string;
  onApplyBaseUrl: (url: string) => void;
  health: HealthResponse | null;
  onShutdownDaemon: () => Promise<void>;
}

export const SettingsModal: React.FC<SettingsModalProps> = ({
  isOpen,
  onClose,
  currentBaseUrl,
  onApplyBaseUrl,
  health,
  onShutdownDaemon,
}) => {
  const [urlInput, setUrlInput] = useState<string>(currentBaseUrl);
  const [isShuttingDown, setIsShuttingDown] = useState<boolean>(false);
  const [shutdownConfirmed, setShutdownConfirmed] = useState<boolean>(false);
  const [activeTab, setActiveTab] = useState<'endpoint' | 'diagnostics'>('endpoint');

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    onApplyBaseUrl(urlInput);
    onClose();
  };

  const handleShutdown = async () => {
    if (!shutdownConfirmed) {
      setShutdownConfirmed(true);
      return;
    }

    setIsShuttingDown(true);
    try {
      await onShutdownDaemon();
      onClose();
    } catch (e) {
      console.error('Shutdown failed:', e);
    } finally {
      setIsShuttingDown(false);
      setShutdownConfirmed(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={
        <span className="modal-title-cluster">
          <Server size={18} className="text-gold" />
          <span>Loopback Daemon Configuration</span>
        </span>
      }
      description="Manage local Java 21 process connection, loopback interface port, and runtime diagnostics."
      size="md"
    >
      <div className="settings-tab-bar">
        <button
          className={`settings-tab-btn ${activeTab === 'endpoint' ? 'is-active' : ''}`}
          onClick={() => setActiveTab('endpoint')}
        >
          Endpoint & Port
        </button>
        <button
          className={`settings-tab-btn ${activeTab === 'diagnostics' ? 'is-active' : ''}`}
          onClick={() => setActiveTab('diagnostics')}
        >
          Live Diagnostics (JSON)
        </button>
      </div>

      {activeTab === 'endpoint' && (
        <form onSubmit={handleSave} className="settings-form">
          <div className="form-group">
            <label className="form-label" htmlFor="endpoint-url">
              Loopback API URL (127.0.0.1)
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
              Default is <code>http://127.0.0.1:8080</code>. Strictly loopback-only for desktop security.
            </span>
          </div>

          <div className="security-notice-box">
            <ShieldCheck size={16} className="text-jade" />
            <div>
              <strong>Security & Loopback Enforcement</strong>
              <p>The daemon rejects external NIC traffic. Only localhost callers can invoke solver and admin endpoints.</p>
            </div>
          </div>

          <div className="daemon-power-section">
            <div className="daemon-power-header">
              <strong>Graceful Daemon Lifecycle Control</strong>
              <p>Send safe termination signal via <code>POST /api/admin/shutdown</code>.</p>
            </div>
            <Button
              type="button"
              variant={shutdownConfirmed ? 'danger' : 'outline'}
              size="sm"
              leftIcon={<Power size={14} />}
              isLoading={isShuttingDown}
              onClick={handleShutdown}
            >
              {shutdownConfirmed ? 'Click Again to Confirm Shutdown' : 'Shutdown Java Daemon'}
            </Button>
          </div>

          <div className="modal-actions-bar">
            <Button type="button" variant="ghost" size="md" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" variant="gold" size="md" leftIcon={<Check size={14} />}>
              Apply & Reconnect
            </Button>
          </div>
        </form>
      )}

      {activeTab === 'diagnostics' && (
        <div className="diagnostics-viewer">
          <pre className="diagnostics-pre">
            <code>
              {health ? JSON.stringify(health, null, 2) : 'No live diagnostics available. Daemon offline.'}
            </code>
          </pre>
        </div>
      )}
    </Modal>
  );
};
