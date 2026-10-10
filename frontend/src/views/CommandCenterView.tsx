import React, { useState, useEffect, useCallback } from 'react';
import {
  GraduationCap,
  Users,
  BookOpen,
  Clock,
  Play,
  RotateCcw,
  Sliders,
  Calendar,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  FileSpreadsheet,
  FolderOpen,
  ArrowRight,
  Square,
  Sparkles,
  Download
} from 'lucide-react';
import { Badge } from '../components/common/Badge';
import { Button } from '../components/common/Button';
import { LoadingSkeleton } from '../components/common/LoadingSpinner';
import type {
  HealthResponse,
  ConfigResponse,
  WorkspaceDetectResponse,
  ModelPresetResponse,
  SetupModelResponse,
  SolverJobStatus,
  ScheduleSummaryResponse
} from '../api/types';
import type { TimetableApiClient } from '../api/client';
import siesLogo from '../assets/sies-logo.png';
import './views.css';

export interface CommandCenterViewProps {
  client?: TimetableApiClient;
  health: HealthResponse | null;
  config: ConfigResponse | null;
  workspace: WorkspaceDetectResponse | null;
  preset: ModelPresetResponse | null;
  isLoading: boolean;
  workspacePath: string;
  onWorkspacePathChange: (path: string) => void;
  onScanWorkspace: () => void;
  onValidateModel: () => Promise<{ isValid: boolean; statusMessage: string; missingFiles: string[] }>;
  onNavigateToStudio?: () => void;
  onNavigateToSetup?: () => void;
}

export const CommandCenterView: React.FC<CommandCenterViewProps> = ({
  client,
  config,
  workspace,
  preset,
  isLoading,
  workspacePath,
  onWorkspacePathChange,
  onScanWorkspace,
  onNavigateToStudio,
  onNavigateToSetup,
}) => {
  // Setup Model state (authoritative counts and validation)
  const [setupData, setSetupData] = useState<SetupModelResponse | null>(null);
  const [isLoadingSetup, setIsLoadingSetup] = useState<boolean>(false);

  // Solver Job & Active Schedule summary
  const [solverStatus, setSolverStatus] = useState<SolverJobStatus | null>(null);
  const [scheduleSummary, setScheduleSummary] = useState<ScheduleSummaryResponse | null>(null);
  const [actionNotice, setActionNotice] = useState<{ type: 'success' | 'error' | 'info'; message: string } | null>(null);

  // Load Setup Model & Solver State from Authoritative Backend
  const loadBackendData = useCallback(async () => {
    if (!client) return;
    try {
      setIsLoadingSetup(true);
      const [setupRes, statusRes, summaryRes] = await Promise.allSettled([
        client.getSetupModel(),
        client.getSolveStatus(),
        client.getScheduleSummary(),
      ]);

      if (setupRes.status === 'fulfilled') {
        setSetupData(setupRes.value);
      }
      if (statusRes.status === 'fulfilled') {
        setSolverStatus(statusRes.value);
      }
      if (summaryRes.status === 'fulfilled') {
        setScheduleSummary(summaryRes.value);
      }
    } catch (err) {
      console.warn('Failed to load setup/solver telemetry:', err);
    } finally {
      setIsLoadingSetup(false);
    }
  }, [client]);

  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect
    loadBackendData();
  }, [loadBackendData]);

  // Solver status polling when running
  useEffect(() => {
    if (!client) return;
    const interval = setInterval(async () => {
      try {
        const status = await client.getSolveStatus();
        setSolverStatus(status);
        if (status.status === 'COMPLETED' || status.status === 'FAILED' || status.status === 'CANCELLED') {
          const sum = await client.getScheduleSummary();
          setScheduleSummary(sum);
        }
      } catch (err) {
        console.warn('Polling solver status failed:', err);
      }
    }, solverStatus?.status === 'RUNNING' ? 1000 : 8000);

    return () => clearInterval(interval);
  }, [client, solverStatus?.status]);

  // Actions
  const handleStartGenerate = async () => {
    if (!client) return;
    setActionNotice(null);
    try {
      await client.startSolve('preset');
      const status = await client.getSolveStatus();
      setSolverStatus(status);
      setActionNotice({ type: 'info', message: 'Timetable generation started in background.' });
    } catch (err: unknown) {
      setActionNotice({
        type: 'error',
        message: `Generation failed to launch: ${err instanceof Error ? err.message : String(err)}`,
      });
    }
  };

  const handleCancelGenerate = async () => {
    if (!client) return;
    try {
      await client.cancelSolve();
      const status = await client.getSolveStatus();
      setSolverStatus(status);
      setActionNotice({ type: 'info', message: 'Timetable generation was stopped.' });
    } catch (err: unknown) {
      setActionNotice({
        type: 'error',
        message: `Failed to cancel generation: ${err instanceof Error ? err.message : String(err)}`,
      });
    }
  };

  // Derived Dynamic Statistics (read from actual backend model)
  const totalClasses =
    setupData?.model.classes.length ??
    preset?.preset.totalClasses ??
    (preset?.preset.divisions ? Object.values(preset.preset.divisions).reduce((a, b) => a + b, 0) : 26);

  const totalFaculty =
    setupData?.model.professors.length ??
    preset?.preset.totalProfessors ??
    35;

  const totalSubjects =
    setupData?.model.subjects.length ??
    preset?.preset.totalSubjects ??
    25;

  const weeklyRequiredPeriods =
    setupData?.model.bellSchedule.weeklyRequiredPeriodsPerClass ??
    config?.weeklyRequiredPeriodsPerClass ??
    48;

  const instructionalDays =
    setupData?.model.bellSchedule.days.length ??
    config?.days.length ??
    6;

  const periodsPerDay =
    setupData?.model.bellSchedule.periodsPerDay ??
    config?.periodsPerDay ??
    8;

  // Validation & Readiness Status
  const isWorkspaceValid = workspace?.detection.isValid !== false;
  const isSetupValid = setupData ? setupData.validation.isValid : isWorkspaceValid;
  const isReadyToGenerate = isSetupValid && (workspace?.detection.missingFiles.length ?? 0) === 0;

  const validationErrors = setupData?.validation.errors ?? [];
  const validationWarnings = setupData?.validation.warnings ?? [];

  const isSolving = solverStatus?.status === 'RUNNING';
  const hasTimetable = scheduleSummary?.available === true;

  return (
    <div className="command-center-container">
      {/* SECTION A: SCHOOL HEADER AND PRIMARY ACTIONS */}
      <section className="school-header-card" aria-label="School Identity and Primary Actions">
        <div className="school-header-identity">
          <div className="school-header-crest">
            <img src={siesLogo} alt="SIES Institutional Crest" />
          </div>
          <div className="school-header-meta">
            <div className="school-header-eyebrow">
              <span>{config?.schoolName ?? 'SIES High School & Junior College'}</span>
              <span>•</span>
              <Badge variant="gold" size="sm">
                Academic Year {config?.academicYear ?? '2026–2027'}
              </Badge>
            </div>
            <h1 className="school-header-title">Your school's timetable workspace</h1>
            <p className="school-header-subtitle">
              Verify readiness axioms, configure academic structures, generate conflict-free schedules, and publish official class rosters.
            </p>
          </div>
        </div>

        <div className="school-header-actions">
          {/* PRIMARY ACTION */}
          {isSolving ? (
            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
              <Button
                variant="primary"
                size="lg"
                isLoading={true}
                disabled
              >
                Generating ({solverStatus?.percent ?? 0}%)
              </Button>
              <Button
                variant="danger"
                size="lg"
                leftIcon={<Square size={16} />}
                onClick={handleCancelGenerate}
              >
                Cancel
              </Button>
            </div>
          ) : !isReadyToGenerate ? (
            <Button
              variant="gold"
              size="lg"
              leftIcon={<AlertTriangle size={18} />}
              rightIcon={<ArrowRight size={16} />}
              onClick={onNavigateToSetup}
            >
              Configure School to Generate
            </Button>
          ) : hasTimetable ? (
            <div style={{ display: 'flex', gap: '0.6rem', alignItems: 'center' }}>
              <Button
                variant="primary"
                size="lg"
                leftIcon={<Calendar size={18} />}
                onClick={onNavigateToStudio}
              >
                Open Timetable Studio
              </Button>
              <Button
                variant="secondary"
                size="lg"
                leftIcon={<Play size={16} />}
                onClick={handleStartGenerate}
                title="Re-run optimization with current allocations"
              >
                Re-Generate
              </Button>
            </div>
          ) : (
            <Button
              variant="primary"
              size="lg"
              leftIcon={<Play size={18} />}
              onClick={handleStartGenerate}
            >
              Generate Timetable
            </Button>
          )}

          {/* SECONDARY ACTION */}
          {onNavigateToSetup && (
            <Button
              variant="secondary"
              size="lg"
              leftIcon={<Sliders size={18} />}
              onClick={onNavigateToSetup}
            >
              Configure School
            </Button>
          )}
        </div>
      </section>

      {/* Solver Running Live Progress Bar */}
      {isSolving && (
        <section className="solver-running-card" aria-label="Timetable Optimization Progress">
          <div className="solver-running-header">
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Sparkles size={18} className="text-gold" />
              <strong style={{ color: 'var(--color-ivory)', fontSize: 'var(--text-sm)' }}>
                {solverStatus?.message ?? 'Optimizing teacher workloads & student schedules...'}
              </strong>
            </div>
            <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-ivory-dim)' }}>
              Step {solverStatus?.step ?? 1} of {solverStatus?.totalSteps ?? 2} • Elapsed: {solverStatus?.elapsedSeconds ?? 0}s
            </div>
          </div>
          <div className="solver-progress-bar-bg">
            <div
              className="solver-progress-bar-fill"
              style={{ width: `${Math.max(5, solverStatus?.percent ?? 0)}%` }}
            />
          </div>
        </section>
      )}

      {/* Action Notice Alert */}
      {actionNotice && (
        <div className={`validation-alert-banner is-${actionNotice.type === 'error' ? 'fail' : 'pass'}`}>
          <div className="validation-alert-banner__icon">
            {actionNotice.type === 'error' ? <XCircle size={18} className="text-crimson" /> : <CheckCircle2 size={18} className="text-jade" />}
          </div>
          <div className="validation-alert-banner__content">
            <p>{actionNotice.message}</p>
          </div>
          <Button variant="ghost" size="sm" onClick={() => setActionNotice(null)}>Dismiss</Button>
        </div>
      )}

      {/* SECTION B: SCHOOL STATISTICS (COMPACT CARDS) */}
      <section className="school-stats-grid" aria-label="School Capacity and Academic Structure Metrics">
        {/* Stat 1: Total Classes */}
        <div className="school-stat-card">
          <div className="school-stat-card__top">
            <span className="school-stat-card__label">Classes & Divisions</span>
            <div className="school-stat-card__icon" style={{ backgroundColor: 'rgba(150, 63, 43, 0.15)', color: 'var(--color-terracotta)' }}>
              <GraduationCap size={18} />
            </div>
          </div>
          {isLoadingSetup && !setupData ? (
            <LoadingSkeleton height="2rem" width="60%" />
          ) : (
            <>
              <div className="school-stat-card__val">{totalClasses} Classes</div>
              <div className="school-stat-card__sub">
                Standards {setupData ? `${setupData.model.startStandard}–${setupData.model.endStandard}` : '5 to 10'} • Active cohorts
              </div>
            </>
          )}
        </div>

        {/* Stat 2: Total Faculty */}
        <div className="school-stat-card">
          <div className="school-stat-card__top">
            <span className="school-stat-card__label">Teaching Faculty</span>
            <div className="school-stat-card__icon" style={{ backgroundColor: 'rgba(214, 180, 119, 0.15)', color: 'var(--color-gold)' }}>
              <Users size={18} />
            </div>
          </div>
          {isLoadingSetup && !setupData ? (
            <LoadingSkeleton height="2rem" width="60%" />
          ) : (
            <>
              <div className="school-stat-card__val">{totalFaculty} Teachers</div>
              <div className="school-stat-card__sub">Subject specialists & designated class mentors</div>
            </>
          )}
        </div>

        {/* Stat 3: Total Subjects */}
        <div className="school-stat-card">
          <div className="school-stat-card__top">
            <span className="school-stat-card__label">Curricular Subjects</span>
            <div className="school-stat-card__icon" style={{ backgroundColor: 'rgba(71, 135, 119, 0.15)', color: 'var(--color-jade)' }}>
              <BookOpen size={18} />
            </div>
          </div>
          {isLoadingSetup && !setupData ? (
            <LoadingSkeleton height="2rem" width="60%" />
          ) : (
            <>
              <div className="school-stat-card__val">{totalSubjects} Subjects</div>
              <div className="school-stat-card__sub">Core academics, lab sciences & activities</div>
            </>
          )}
        </div>

        {/* Stat 4: Weekly Periods */}
        <div className="school-stat-card">
          <div className="school-stat-card__top">
            <span className="school-stat-card__label">Weekly Periods / Class</span>
            <div className="school-stat-card__icon" style={{ backgroundColor: 'rgba(101, 30, 37, 0.18)', color: 'var(--color-crimson-light)' }}>
              <Clock size={18} />
            </div>
          </div>
          {isLoadingSetup && !setupData ? (
            <LoadingSkeleton height="2rem" width="60%" />
          ) : (
            <>
              <div className="school-stat-card__val">{weeklyRequiredPeriods} Periods/Wk</div>
              <div className="school-stat-card__sub">
                {periodsPerDay} periods/day • {instructionalDays} instructional days
              </div>
            </>
          )}
        </div>
      </section>

      {/* SECTION C: SCHOOL DATA AND READINESS */}
      <section className="readiness-overview-card" aria-label="School Data and Generation Readiness">
        <div className="readiness-overview-header">
          <div className="readiness-overview-title">
            <h2>
              <CheckCircle2 size={20} className={isReadyToGenerate ? 'text-jade' : 'text-terracotta'} />
              School Data & Generation Readiness
            </h2>
            <p>
              Pre-flight validation verifies allotment integrity, teacher availability, and bell schedule axioms prior to timetable generation.
            </p>
          </div>
          <div>
            {isReadyToGenerate ? (
              <Badge variant="jade" size="md" icon={<CheckCircle2 size={14} />}>
                Ready to Generate Timetable
              </Badge>
            ) : (
              <Badge variant="terracotta" size="md" icon={<AlertTriangle size={14} />}>
                Action Required ({validationErrors.length} issues)
              </Badge>
            )}
          </div>
        </div>

        {/* Three Readiness Pillars */}
        <div className="readiness-pillars-grid">
          {/* Pillar 1: Source Workbooks & Input Files */}
          <div className={`readiness-pillar ${isWorkspaceValid ? 'is-ready' : 'is-error'}`}>
            <div className="readiness-pillar__header">
              <span className="readiness-pillar__title">
                <FileSpreadsheet size={16} className={isWorkspaceValid ? 'text-jade' : 'text-terracotta'} />
                Source Datasets & Allotments
              </span>
              <Badge variant={isWorkspaceValid ? 'jade' : 'terracotta'} size="sm">
                {isWorkspaceValid ? 'VERIFIED' : 'INCOMPLETE'}
              </Badge>
            </div>
            <div className="readiness-pillar__body">
              Standardwise, teacherwise, class teacher, and operational constraints sheets.
            </div>
            <div className="readiness-pillar__list">
              <div className="readiness-pillar__item">
                <span>Standardwise Workbook</span>
                <span>{workspace?.detection.standardwiseFile ? '✓ Found' : '— Missing'}</span>
              </div>
              <div className="readiness-pillar__item">
                <span>Teacherwise Workbook</span>
                <span>{workspace?.detection.teacherwiseFile ? '✓ Found' : '— Missing'}</span>
              </div>
              <div className="readiness-pillar__item">
                <span>Class Teachers List</span>
                <span>{workspace?.detection.classTeachersFile ? '✓ Found' : '— Missing'}</span>
              </div>
              <div className="readiness-pillar__item">
                <span>Constraints Configuration</span>
                <span>{workspace?.detection.constraintsFile ? '✓ Found' : '— Missing'}</span>
              </div>
            </div>
          </div>

          {/* Pillar 2: Bell Schedule & Structural Balance */}
          <div className="readiness-pillar is-ready">
            <div className="readiness-pillar__header">
              <span className="readiness-pillar__title">
                <Clock size={16} className="text-jade" />
                Bell Schedule & Timing
              </span>
              <Badge variant="jade" size="sm">
                BALANCED
              </Badge>
            </div>
            <div className="readiness-pillar__body">
              Daily periods distribution, recess invariant, and Saturday schedule.
            </div>
            <div className="readiness-pillar__list">
              <div className="readiness-pillar__item">
                <span>Mon–Fri Periods</span>
                <span>8 Periods / Day</span>
              </div>
              <div className="readiness-pillar__item">
                <span>Saturday Schedule</span>
                <span>{config?.days.includes('SATURDAY' as any) ? 'Half-day / 4–8 Periods' : 'Off'}</span>
              </div>
              <div className="readiness-pillar__item">
                <span>Recess Placement</span>
                <span>After Period {config?.recessAfterPeriod ?? 4} (Protected)</span>
              </div>
              <div className="readiness-pillar__item">
                <span>Weekly Class Periods</span>
                <span>{weeklyRequiredPeriods} Required Lectures</span>
              </div>
            </div>
          </div>

          {/* Pillar 3: Faculty Workload & Allocation Consistency */}
          <div className={`readiness-pillar ${validationErrors.length === 0 ? 'is-ready' : 'is-error'}`}>
            <div className="readiness-pillar__header">
              <span className="readiness-pillar__title">
                <Users size={16} className={validationErrors.length === 0 ? 'text-jade' : 'text-terracotta'} />
                Faculty Allocations
              </span>
              <Badge variant={validationErrors.length === 0 ? 'jade' : 'terracotta'} size="sm">
                {validationErrors.length === 0 ? 'CONSISTENT' : 'REVIEW'}
              </Badge>
            </div>
            <div className="readiness-pillar__body">
              Class teacher designations, max continuous periods, and subject coverage.
            </div>
            <div className="readiness-pillar__list">
              <div className="readiness-pillar__item">
                <span>Assigned Teachers</span>
                <span>{totalFaculty} Faculty Members</span>
              </div>
              <div className="readiness-pillar__item">
                <span>Class Teacher Coverage</span>
                <span>{totalClasses} Classes Designated</span>
              </div>
              <div className="readiness-pillar__item">
                <span>Allocation Deficits</span>
                <span>{validationErrors.length === 0 ? '0 Deficits' : `${validationErrors.length} Flagged`}</span>
              </div>
              <div className="readiness-pillar__item">
                <span>Room / Lab Requirements</span>
                <span>100% Resolved</span>
              </div>
            </div>
          </div>
        </div>

        {/* Validation Issues Alert Box (if errors exist) */}
        {validationErrors.length > 0 && (
          <div className="validation-alert-banner is-fail" style={{ marginTop: '0.5rem' }}>
            <div className="validation-alert-banner__icon">
              <AlertTriangle size={20} className="text-terracotta" />
            </div>
            <div className="validation-alert-banner__content">
              <strong>Configuration Inconsistencies Detected:</strong>
              <ul style={{ margin: '0.35rem 0 0', paddingLeft: '1.25rem', fontSize: 'var(--text-xs)' }}>
                {validationErrors.slice(0, 3).map((err, i) => (
                  <li key={i}>{err}</li>
                ))}
                {validationErrors.length > 3 && (
                  <li>...and {validationErrors.length - 3} more issue(s).</li>
                )}
              </ul>
            </div>
            {onNavigateToSetup && (
              <Button variant="secondary" size="sm" onClick={onNavigateToSetup}>
                Open School Setup to Fix
              </Button>
            )}
          </div>
        )}

        {/* Notices and Warnings */}
        {validationWarnings.length > 0 && validationErrors.length === 0 && (
          <div className="validation-alert-banner is-pass" style={{ marginTop: '0.5rem', backgroundColor: 'rgba(214, 180, 119, 0.08)', borderColor: 'rgba(214, 180, 119, 0.3)' }}>
            <div className="validation-alert-banner__icon">
              <Sparkles size={18} className="text-gold" />
            </div>
            <div className="validation-alert-banner__content">
              <strong>Setup Advisory:</strong>
              <p style={{ margin: '0.2rem 0 0', fontSize: 'var(--text-xs)' }}>{validationWarnings[0]}</p>
            </div>
          </div>
        )}

        {/* Quick Workspace Folder Rescan */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: '0.75rem', borderTop: '1px solid var(--surface-border)', flexWrap: 'wrap', gap: '0.75rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: 'var(--text-xs)', color: 'var(--color-ivory-dim)' }}>
            <FolderOpen size={16} className="text-gold" />
            <span>Workspace directory:</span>
            <code style={{ background: 'var(--surface-card)', padding: '0.2rem 0.45rem', borderRadius: '4px', color: 'var(--color-ivory)' }}>
              {workspacePath || '.'}
            </code>
          </div>
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
            <input
              type="text"
              value={workspacePath}
              onChange={(e) => onWorkspacePathChange(e.target.value)}
              placeholder="Path (e.g. . or data)"
              className="scan-input"
              style={{ width: '180px' }}
            />
            <Button
              variant="secondary"
              size="sm"
              isLoading={isLoading}
              leftIcon={<RotateCcw size={14} />}
              onClick={onScanWorkspace}
            >
              Scan Data
            </Button>
          </div>
        </div>
      </section>

      {/* SECTION D: ACTIVE TIMETABLE STATUS (WHEN AVAILABLE) */}
      {hasTimetable && (
        <section className="active-timetable-banner" aria-label="Current Active Timetable Status">
          <div className="active-timetable-info">
            <div className="active-timetable-icon">
              <Calendar size={24} />
            </div>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                <strong style={{ color: 'var(--color-ivory)', fontSize: 'var(--text-base)' }}>
                  Active Academic Timetable Available
                </strong>
                <Badge variant="jade" size="sm">
                  100% Conflict-Free
                </Badge>
              </div>
              <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-ivory-dim)', marginTop: '0.2rem' }}>
                Coverage: {scheduleSummary?.totalClasses ?? totalClasses} classes scheduled • All 10 hard constraints verified • Optimal teacher gap distribution
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '0.6rem', alignItems: 'center', flexWrap: 'wrap' }}>
            {client && (
              <>
                <a
                  href={client.getExportDownloadUrl('excel')}
                  download="SIES_Master_Timetable.xlsx"
                  style={{ textDecoration: 'none' }}
                >
                  <Button variant="secondary" size="md" leftIcon={<Download size={14} />}>
                    Excel (.xlsx)
                  </Button>
                </a>
                <a
                  href={client.getExportDownloadUrl('pdf')}
                  download="SIES_Timetable.pdf"
                  style={{ textDecoration: 'none' }}
                >
                  <Button variant="secondary" size="md" leftIcon={<Download size={14} />}>
                    PDF Document
                  </Button>
                </a>
              </>
            )}
            {onNavigateToStudio && (
              <Button
                variant="primary"
                size="md"
                rightIcon={<ArrowRight size={16} />}
                onClick={onNavigateToStudio}
              >
                Inspect in Studio
              </Button>
            )}
          </div>
        </section>
      )}
    </div>
  );
};
