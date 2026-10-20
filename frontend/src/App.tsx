import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Play, RefreshCw, Settings, AlertCircle } from 'lucide-react';
import { AppShell } from './components/layout/AppShell';
import type { ActiveNavView, SetupTab, RulesTab } from './components/layout/Sidebar';
import { Button } from './components/common';
import { CommandCenterView } from './views/CommandCenterView';
import { SchoolSetupView } from './views/SchoolSetupView';
import { TimetableRulesView } from './views/TimetableRulesView';
import { GenerateTimetableView } from './views/GenerateTimetableView';
import { TimetableStudioView } from './views/TimetableStudioView';
import { SettingsModal } from './views/SettingsModal';
import { TimetableApiClient, DEFAULT_API_BASE_URL } from './api/client';
import type {
  HealthResponse,
  ConfigResponse,
  WorkspaceDetectResponse,
  ModelPresetResponse,
  BackendLifecycleState,
} from './api/types';

export const App: React.FC = () => {
  const [activeView, setActiveView] = useState<ActiveNavView>('home');
  const [activeSetupTab, setActiveSetupTab] = useState<SetupTab>('structure');
  const [activeRulesTab, setActiveRulesTab] = useState<RulesTab>('bell');
  const [hasTimetable, setHasTimetable] = useState<boolean>(false);

  const [baseUrl, setBaseUrl] = useState<string>(() => {
    if (typeof window !== 'undefined') {
      const globalUrl = (window as unknown as { __SIES_BACKEND_URL__?: string }).__SIES_BACKEND_URL__;
      if (globalUrl) return globalUrl;
      const stored = localStorage.getItem('sies_api_base_url');
      if (stored) return stored;
    }
    return DEFAULT_API_BASE_URL;
  });
  const [isSettingsOpen, setIsSettingsOpen] = useState<boolean>(false);
  const [lifecycleState, setLifecycleState] = useState<BackendLifecycleState>('ONLINE');
  const [isLifecycleBusy, setIsLifecycleBusy] = useState<boolean>(false);

  // Auto-detect dynamic local port when launched inside Tauri
  useEffect(() => {
    if (typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window) {
      import('@tauri-apps/api/core')
        .then(({ invoke }) => invoke<string>('get_backend_url'))
        .then((url) => {
          if (url) {
            setBaseUrl(url);
          }
        })
        .catch((e) => console.warn('Could not invoke get_backend_url:', e));
    }
  }, []);

  // Live Backend Data States
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [config, setConfig] = useState<ConfigResponse | null>(null);
  const [workspace, setWorkspace] = useState<WorkspaceDetectResponse | null>(null);
  const [preset, setPreset] = useState<ModelPresetResponse | null>(null);

  const [workspacePath, setWorkspacePath] = useState<string>('.');
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  const client = useMemo(() => new TimetableApiClient(baseUrl), [baseUrl]);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      // 1. Health & System Telemetry
      const healthData = await client.getHealth();
      setHealth(healthData);
      setLifecycleState('ONLINE');

      // 2. Active Configuration
      try {
        const configData = await client.getConfig();
        setConfig(configData);
      } catch (e) {
        console.warn('Failed to load active school-config.yaml:', e);
      }

      // 3. Workspace Detection
      try {
        const wsData = await client.detectWorkspace(workspacePath);
        setWorkspace(wsData);
      } catch (e) {
        console.warn('Failed to detect workspace in target path:', e);
      }

      // 4. Model Preset Data
      try {
        const presetData = await client.getPreset();
        setPreset(presetData);
      } catch (e) {
        console.warn('Failed to load school academic preset:', e);
      }

      // 5. Active Timetable Status
      try {
        const summaryData = await client.getScheduleSummary();
        setHasTimetable(Boolean(summaryData.available));
      } catch (e) {
        console.warn('Failed to query schedule summary status:', e);
      }

      setLastUpdated(new Date());
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setError(msg);
      setHealth(null);
      setLifecycleState('OFFLINE');
    } finally {
      setIsLoading(false);
    }
  }, [client, workspacePath]);

  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect
    loadData();
    const interval = setInterval(loadData, 10000); // 10s auto-refresh
    return () => clearInterval(interval);
  }, [loadData]);

  const handleScanWorkspace = async () => {
    setIsLoading(true);
    try {
      const wsData = await client.detectWorkspace(workspacePath);
      setWorkspace(wsData);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setError('Workspace scan failed: ' + msg);
    } finally {
      setIsLoading(false);
    }
  };

  const handleValidateModel = async () => {
    const res = await client.validateModel(workspacePath);
    return {
      isValid: res.isValid,
      statusMessage: res.statusMessage,
      missingFiles: res.missingFiles || [],
    };
  };

  const handleStartBackend = async () => {
    setIsLifecycleBusy(true);
    setLifecycleState('STARTING');
    setError(null);
    try {
      const res = await client.startBackendProcess();
      setLifecycleState(res.state);
      if (res.url) {
        setBaseUrl(res.url);
      }
      await loadData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setError(`Could not start scheduling engine: ${msg}`);
      setLifecycleState('FAILED');
    } finally {
      setIsLifecycleBusy(false);
    }
  };

  const handleRestartBackend = async () => {
    setIsLifecycleBusy(true);
    setLifecycleState('RECOVERING');
    setError(null);
    try {
      const res = await client.restartBackendProcess();
      setLifecycleState(res.state);
      if (res.url) {
        setBaseUrl(res.url);
      }
      await loadData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setError(`Could not restart scheduling engine: ${msg}`);
      setLifecycleState('FAILED');
    } finally {
      setIsLifecycleBusy(false);
    }
  };

  const handleShutdownDaemon = async () => {
    setIsLifecycleBusy(true);
    setLifecycleState('SHUTTING_DOWN');
    try {
      if (client.isTauriEnvironment()) {
        const res = await client.shutdownBackendProcess();
        setLifecycleState(res.state);
      } else {
        await client.shutdown();
        setLifecycleState('OFFLINE');
      }
      setHealth(null);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      console.warn('Shutdown error:', msg);
      setLifecycleState('OFFLINE');
      setHealth(null);
    } finally {
      setIsLifecycleBusy(false);
    }
  };

  const [studioTarget, setStudioTarget] = useState<{ viewMode: 'class' | 'teacher'; id: string } | null>(null);

  const handleNavigateToStudio = (target?: { viewMode: 'class' | 'teacher'; id: string }) => {
    if (target) {
      setStudioTarget(target);
    }
    setActiveView('timetable-studio');
  };

  const handleSelectView = (view: ActiveNavView, tab?: SetupTab, rulesTab?: RulesTab) => {
    setActiveView(view);
    if (tab) {
      setActiveSetupTab(tab);
    }
    if (rulesTab) {
      setActiveRulesTab(rulesTab);
    }
  };

  return (
    <>
      <AppShell
        activeView={activeView}
        activeSetupTab={activeSetupTab}
        activeRulesTab={activeRulesTab}
        onSelectView={handleSelectView}
        health={health}
        lifecycleState={lifecycleState}
        isLoading={isLoading}
        lastUpdated={lastUpdated}
        hasTimetable={hasTimetable}
        onRefresh={loadData}
        onOpenSettings={() => setIsSettingsOpen(true)}
        onStartDaemon={handleStartBackend}
      >
        {(!health || error) && (
          <div
            className="engine-offline-card"
            style={{
              marginBottom: '1.25rem',
              padding: '1.25rem 1.5rem',
              background: 'var(--color-surface, #ffffff)',
              border: '1px solid var(--color-border-warning, #f59e0b)',
              borderLeft: '5px solid var(--color-warning, #d97706)',
              borderRadius: 'var(--radius-md, 8px)',
              boxShadow: 'var(--shadow-sm, 0 1px 3px rgba(0,0,0,0.08))',
            }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                flexWrap: 'wrap',
                gap: '1rem',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.85rem' }}>
                <div
                  style={{
                    width: '38px',
                    height: '38px',
                    borderRadius: '50%',
                    background: 'rgba(217, 119, 6, 0.12)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: 'var(--color-warning, #d97706)',
                    flexShrink: 0,
                  }}
                >
                  <AlertCircle size={22} />
                </div>
                <div>
                  <h4
                    style={{
                      margin: 0,
                      fontSize: '1rem',
                      fontWeight: 600,
                      color: 'var(--color-text-primary, #1e293b)',
                    }}
                  >
                    {lifecycleState === 'STARTING' || lifecycleState === 'RECOVERING'
                      ? 'Scheduling Engine Starting...'
                      : 'Scheduling Engine Offline'}
                  </h4>
                  <p
                    style={{
                      margin: '0.2rem 0 0',
                      fontSize: '0.85rem',
                      color: 'var(--color-text-secondary, #64748b)',
                    }}
                  >
                    {lifecycleState === 'STARTING' || lifecycleState === 'RECOVERING'
                      ? 'Initializing timetable optimization engine and establishing local connection...'
                      : 'The timetable calculation service is currently paused. Start the engine to generate schedules, edit allocations, or inspect conflicts.'}
                  </p>
                </div>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                <Button
                  variant="primary"
                  size="sm"
                  leftIcon={<Play size={14} />}
                  isLoading={isLifecycleBusy || lifecycleState === 'STARTING'}
                  onClick={handleStartBackend}
                >
                  Start Scheduling Engine
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  leftIcon={<RefreshCw size={14} />}
                  isLoading={isLoading}
                  onClick={loadData}
                >
                  Check Connection
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  leftIcon={<Settings size={14} />}
                  onClick={() => setIsSettingsOpen(true)}
                >
                  Engine Settings
                </Button>
              </div>
            </div>

            {error && (
              <details style={{ fontSize: '0.78rem', color: 'var(--color-text-tertiary, #94a3b8)', marginTop: '0.5rem' }}>
                <summary style={{ cursor: 'pointer', userSelect: 'none' }}>Technical Connection Diagnostics</summary>
                <div
                  style={{
                    marginTop: '0.35rem',
                    fontFamily: 'monospace',
                    whiteSpace: 'pre-wrap',
                    background: 'var(--color-surface-subtle, #f8fafc)',
                    padding: '0.5rem 0.75rem',
                    borderRadius: '4px',
                    border: '1px solid var(--color-border-subtle, #e2e8f0)',
                  }}
                >
                  {`Status: ${lifecycleState}\nService Endpoint: ${baseUrl}\nDiagnostic detail: ${error}`}
                </div>
              </details>
            )}
          </div>
        )}

        {/* Persistent View Dispatcher (5 Core Destinations) */}
        {(activeView === 'home' || activeView === 'command-center') && (
          <CommandCenterView
            client={client}
            health={health}
            config={config}
            workspace={workspace}
            preset={preset}
            isLoading={isLoading}
            workspacePath={workspacePath}
            onWorkspacePathChange={setWorkspacePath}
            onScanWorkspace={handleScanWorkspace}
            onValidateModel={handleValidateModel}
            onNavigateToStudio={() => handleNavigateToStudio()}
            onNavigateToSetup={(tab) => handleSelectView('school-setup', tab || 'structure')}
            onNavigateToRules={(subTab) => handleSelectView('timetable-rules', undefined, subTab || 'bell')}
            onNavigateToGenerate={() => handleSelectView('generate')}
          />
        )}

        {activeView === 'school-setup' && (
          <SchoolSetupView
            client={client}
            activeTab={activeSetupTab}
            onTabChange={setActiveSetupTab}
            onNavigateToStudio={() => handleNavigateToStudio()}
            onNavigateToRules={(subTab) => handleSelectView('timetable-rules', undefined, subTab || 'bell')}
            onNavigateToGenerate={() => handleSelectView('generate')}
          />
        )}

        {activeView === 'timetable-rules' && (
          <TimetableRulesView
            client={client}
            activeSubTab={activeRulesTab}
            onSubTabChange={setActiveRulesTab}
            onNavigateToGenerate={() => handleSelectView('generate')}
          />
        )}

        {activeView === 'generate' && (
          <GenerateTimetableView
            client={client}
            onNavigateToStudio={() => handleNavigateToStudio()}
            onNavigateToSetup={(tab) => handleSelectView('school-setup', tab || 'structure')}
            onNavigateToRules={(subTab) => handleSelectView('timetable-rules', undefined, subTab || 'bell')}
          />
        )}

        {activeView === 'timetable-studio' && (
          <TimetableStudioView client={client} initialTarget={studioTarget} />
        )}
      </AppShell>

      {/* Loopback Endpoint Settings & Diagnostics Dialog */}
      <SettingsModal
        isOpen={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
        currentBaseUrl={baseUrl}
        onApplyBaseUrl={setBaseUrl}
        health={health}
        lifecycleState={lifecycleState}
        isLifecycleBusy={isLifecycleBusy}
        onStartDaemon={handleStartBackend}
        onRestartDaemon={handleRestartBackend}
        onShutdownDaemon={handleShutdownDaemon}
      />
    </>
  );
};

export default App;
