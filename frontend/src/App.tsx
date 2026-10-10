import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { AppShell } from './components/layout/AppShell';
import type { ActiveNavView } from './components/layout/Sidebar';
import { ErrorBanner } from './components/common';
import { CommandCenterView } from './views/CommandCenterView';
import { TimetableStudioView } from './views/TimetableStudioView';
import { SchoolSetupView } from './views/SchoolSetupView';
import { SettingsModal } from './views/SettingsModal';
import { TimetableApiClient, DEFAULT_API_BASE_URL } from './api/client';
import type {
  HealthResponse,
  ConfigResponse,
  WorkspaceDetectResponse,
  ModelPresetResponse,
} from './api/types';

export const App: React.FC = () => {
  const [activeView, setActiveView] = useState<ActiveNavView>('command-center');
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

      setLastUpdated(new Date());
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setError(msg);
      setHealth(null);
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

  const handleShutdownDaemon = async () => {
    await client.shutdown();
    setHealth(null);
  };

  const [studioTarget, setStudioTarget] = useState<{ viewMode: 'class' | 'teacher'; id: string } | null>(null);

  const handleNavigateToStudio = (target?: { viewMode: 'class' | 'teacher'; id: string }) => {
    if (target) {
      setStudioTarget(target);
    }
    setActiveView('timetable-studio');
  };

  return (
    <>
      <AppShell
        activeView={activeView}
        onSelectView={setActiveView}
        health={health}
        isLoading={isLoading}
        lastUpdated={lastUpdated}
        onRefresh={loadData}
        onOpenSettings={() => setIsSettingsOpen(true)}
      >
        {error && (
          <div style={{ marginBottom: '1.25rem' }}>
            <ErrorBanner
              title="Java 21 Loopback API Daemon Offline"
              message={error}
              details={`Target URL: ${baseUrl}\nVerify that the Java daemon is running:\n  .\\mvnw exec:java -Dexec.args="--server 8080"`}
              onRetry={loadData}
            />
          </div>
        )}

        {/* Persistent View Dispatcher */}
        {activeView === 'command-center' && (
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
            onNavigateToSetup={() => setActiveView('school-setup')}
          />
        )}

        {activeView === 'school-setup' && (
          <SchoolSetupView
            client={client}
            onNavigateToStudio={() => handleNavigateToStudio()}
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
        onShutdownDaemon={handleShutdownDaemon}
      />
    </>
  );
};

export default App;
