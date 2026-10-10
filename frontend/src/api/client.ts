import type {
  HealthResponse,
  ConfigResponse,
  WorkspaceDetectResponse,
  ModelPresetResponse,
  ModelValidateResponse,
  SolverJobStatus,
  ScheduleSummaryResponse,
  ClassGridResponse,
  TeacherGridResponse,
  ScheduleValidationResponse,
  SetupModelResponse,
  SetupValidateResponse,
  ScheduleEditRequest,
  ScheduleEditValidateResponse,
  ScheduleEditApplyResponse,
} from './types';

export const DEFAULT_API_BASE_URL = 'http://127.0.0.1:8080';

export class TimetableApiClient {
  private baseUrl: string;

  constructor(baseUrl: string = DEFAULT_API_BASE_URL) {
    this.baseUrl = baseUrl.replace(/\/+$/, '');
  }

  setBaseUrl(url: string) {
    this.baseUrl = url.replace(/\/+$/, '');
  }

  getBaseUrl(): string {
    return this.baseUrl;
  }

  private async request<T>(endpoint: string, options?: RequestInit): Promise<T> {
    const url = `${this.baseUrl}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 10000);

    try {
      const response = await fetch(url, {
        ...options,
        signal: controller.signal,
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json',
          ...(options?.headers || {}),
        },
      });

      if (!response.ok) {
        let errMessage = `HTTP ${response.status} ${response.statusText}`;
        try {
          const errData = await response.json();
          if (errData?.error) errMessage = errData.error;
          else if (errData?.message) errMessage = errData.message;
        } catch {
          // ignore json parse error on non-json error responses
        }
        throw new Error(errMessage);
      }

      return (await response.json()) as T;
    } catch (err: unknown) {
      if (err instanceof Error) {
        if (err.name === 'AbortError') {
          throw new Error(`Request to ${url} timed out after 10 seconds.`);
        }
        throw err;
      }
      throw new Error(String(err));
    } finally {
      clearTimeout(timeoutId);
    }
  }

  // Phase 1 Core Endpoints
  async getHealth(): Promise<HealthResponse> {
    return this.request<HealthResponse>('/api/health', { method: 'GET' });
  }

  async getConfig(configPath?: string): Promise<ConfigResponse> {
    const query = configPath ? `?path=${encodeURIComponent(configPath)}` : '';
    return this.request<ConfigResponse>(`/api/config${query}`, { method: 'GET' });
  }

  async detectWorkspace(targetPath?: string): Promise<WorkspaceDetectResponse> {
    return this.request<WorkspaceDetectResponse>('/api/workspace/detect', {
      method: 'POST',
      body: JSON.stringify(targetPath ? { path: targetPath } : {}),
    });
  }

  async getPreset(): Promise<ModelPresetResponse> {
    return this.request<ModelPresetResponse>('/api/model/preset', { method: 'GET' });
  }

  async validateModel(folder?: string): Promise<ModelValidateResponse> {
    return this.request<ModelValidateResponse>('/api/model/validate', {
      method: 'POST',
      body: JSON.stringify(folder ? { folder } : {}),
    });
  }

  async shutdown(): Promise<{ status: string; message: string }> {
    return this.request<{ status: string; message: string }>('/api/admin/shutdown', {
      method: 'POST',
    });
  }

  // Phase 3 Solver & Studio Endpoints
  async startSolve(mode: 'preset' | 'workspace' = 'preset', folder?: string): Promise<{ status: string; jobId: string; message: string }> {
    return this.request<{ status: string; jobId: string; message: string }>('/api/solve/start', {
      method: 'POST',
      body: JSON.stringify({ mode, folder: folder || '.' }),
    });
  }

  async getSolveStatus(): Promise<SolverJobStatus> {
    return this.request<SolverJobStatus>('/api/solve/status', { method: 'GET' });
  }

  async cancelSolve(): Promise<{ status: string; message: string }> {
    return this.request<{ status: string; message: string }>('/api/solve/cancel', {
      method: 'POST',
    });
  }

  async getScheduleSummary(): Promise<ScheduleSummaryResponse> {
    return this.request<ScheduleSummaryResponse>('/api/schedule/summary', { method: 'GET' });
  }

  async getClassGrid(classId?: string): Promise<ClassGridResponse> {
    const query = classId ? `?id=${encodeURIComponent(classId)}` : '';
    return this.request<ClassGridResponse>(`/api/schedule/class${query}`, { method: 'GET' });
  }

  async getTeacherGrid(teacherCode?: string): Promise<TeacherGridResponse> {
    const query = teacherCode ? `?code=${encodeURIComponent(teacherCode)}` : '';
    return this.request<TeacherGridResponse>(`/api/schedule/teacher${query}`, { method: 'GET' });
  }

  async getValidationReport(): Promise<ScheduleValidationResponse> {
    return this.request<ScheduleValidationResponse>('/api/schedule/validation', { method: 'GET' });
  }

  getExportDownloadUrl(type: 'excel' | 'student_csv' | 'teacher_csv' | 'pdf', classId?: string): string {
    const classParam = classId ? `&class=${encodeURIComponent(classId)}` : '';
    return `${this.baseUrl}/api/export/download?type=${type}${classParam}`;
  }

  async openOutputFolder(): Promise<{ status: string; opened: boolean; path: string }> {
    return this.request<{ status: string; opened: boolean; path: string }>('/api/export/open-folder', {
      method: 'POST',
    });
  }

  async getSetupModel(): Promise<SetupModelResponse> {
    return this.request<SetupModelResponse>('/api/setup/model', { method: 'GET' });
  }

  async updateSetupModel(payload: Partial<SetupModelResponse['model']>): Promise<SetupModelResponse> {
    return this.request<SetupModelResponse>('/api/setup/model', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  }

  async resetSetupModel(): Promise<SetupModelResponse> {
    return this.request<SetupModelResponse>('/api/setup/reset', { method: 'POST' });
  }

  async validateSetupModel(): Promise<SetupValidateResponse> {
    return this.request<SetupValidateResponse>('/api/setup/validate', { method: 'POST' });
  }

  async validateScheduleEdit(payload: ScheduleEditRequest): Promise<ScheduleEditValidateResponse> {
    return this.request<ScheduleEditValidateResponse>('/api/schedule/edit/validate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  }

  async applyScheduleEdit(payload: ScheduleEditRequest): Promise<ScheduleEditApplyResponse> {
    return this.request<ScheduleEditApplyResponse>('/api/schedule/edit/apply', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  }
}

export const defaultClient = new TimetableApiClient();
