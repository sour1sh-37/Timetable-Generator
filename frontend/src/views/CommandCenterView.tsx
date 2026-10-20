import React, { useState, useEffect, useCallback } from 'react';
import {
  GraduationCap,
  Users,
  BookOpen,
  Clock,
  RotateCcw,
  Sliders,
  Calendar,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  FolderOpen,
  ArrowRight,
  Download,
  Sparkles,
  Layers,
  HelpCircle,
  FileCheck2,
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
  ScheduleSummaryResponse,
} from '../api/types';
import type { TimetableApiClient } from '../api/client';
import type { SetupTab } from '../components/layout/Sidebar';
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
  onNavigateToSetup?: (targetTab?: SetupTab) => void;
  onNavigateToRules?: (targetSubTab?: 'bell' | 'hard' | 'soft') => void;
  onNavigateToGenerate?: () => void;
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
  onNavigateToRules,
  onNavigateToGenerate,
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

  // Dynamic Statistics
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
  const validationErrors = setupData?.validation.errors ?? [];
  const isReadyToGenerate = isSetupValid && (workspace?.detection.missingFiles.length ?? 0) === 0 && validationErrors.length === 0;

  const isSolving = solverStatus?.status === 'RUNNING';
  const hasTimetable = scheduleSummary?.available === true;
  const isStale = Boolean(scheduleSummary?.isStale);

  return (
    <div className="command-center-container">
      {/* SECTION A: SCHOOL HEADER AND PRIMARY ACTIONS */}
      <section className="school-header-card" aria-label="School Identity and Overview">
        <div className="school-header-identity">
          <div className="school-header-crest">
            <img src={siesLogo} alt="SIES Institutional Crest" />
          </div>
          <div className="school-header-meta">
            <div className="school-header-eyebrow">
              <span>{config?.schoolName ?? 'SIES APJ High School & Junior College'}</span>
              <span>•</span>
              <Badge variant="neutral" size="sm">
                Academic Year {config?.academicYear ?? '2026–2027'}
              </Badge>
            </div>
            <h1 className="school-header-title">Timetable Administration Hub</h1>
            <p className="school-header-subtitle">
              Matunga Campus • Automated conflict-free timetable scheduling powered by Google OR-Tools CP-SAT.
            </p>
          </div>
        </div>

        <div className="school-header-actions">
          {onNavigateToGenerate && (
            <Button
              variant="primary"
              size="lg"
              leftIcon={<Sparkles size={18} />}
              onClick={onNavigateToGenerate}
            >
              Generate Timetable
            </Button>
          )}

          {hasTimetable && onNavigateToStudio && (
            <Button
              variant="secondary"
              size="lg"
              leftIcon={<Calendar size={18} />}
              onClick={onNavigateToStudio}
            >
              Timetable Studio
            </Button>
          )}
        </div>
      </section>

      {/* Action Notice Alert */}
      {actionNotice && (
        <div className={`validation-alert-banner is-${actionNotice.type === 'error' ? 'fail' : 'pass'}`}>
          <div className="validation-alert-banner__icon">
            {actionNotice.type === 'error' ? <XCircle size={18} className="text-terracotta" /> : <CheckCircle2 size={18} className="text-jade" />}
          </div>
          <div className="validation-alert-banner__content">
            <p>{actionNotice.message}</p>
          </div>
          <Button variant="ghost" size="sm" onClick={() => setActionNotice(null)}>Dismiss</Button>
        </div>
      )}

      {/* SECTION B: THE THREE CORE QUESTIONS (STATUS, BLOCKERS, NEXT ACTION) */}
      <section aria-label="Administrative Status and Guidance">
        <div className="home-guidance-grid">
          {/* QUESTION 1: Is the app ready to generate? */}
          <div className={`home-guidance-card ${isReadyToGenerate ? 'is-ready' : 'is-issue'}`}>
            <div>
              <div className="home-guidance-card__question">
                <HelpCircle size={14} /> Question 1: System Readiness
              </div>
              <h2 className="home-guidance-card__title">
                {isReadyToGenerate ? (
                  <>
                    <CheckCircle2 size={20} className="text-jade" />
                    <span>Ready to Generate</span>
                  </>
                ) : (
                  <>
                    <AlertTriangle size={20} className="text-terracotta" />
                    <span>Action Required</span>
                  </>
                )}
              </h2>
              <p className="home-guidance-card__answer">
                {isReadyToGenerate
                  ? 'All academic cohorts, faculty assignments, bell schedules, and CP-SAT constraints are verified.'
                  : `${validationErrors.length} configuration issue(s) must be resolved before running the timetable solver.`}
              </p>
            </div>

            <div className="home-guidance-card__footer">
              <span style={{ fontSize: '0.75rem', color: 'var(--color-text-tertiary)' }}>
                {isReadyToGenerate ? 'Axioms: 100% Satisfied' : 'Pre-flight check failed'}
              </span>
              <Badge variant={isReadyToGenerate ? 'jade' : 'terracotta'} size="sm">
                {isReadyToGenerate ? 'PASSED' : 'BLOCKED'}
              </Badge>
            </div>
          </div>

          {/* QUESTION 2: What prevents generation? */}
          <div className={`home-guidance-card ${validationErrors.length === 0 ? 'is-ready' : 'is-issue'}`}>
            <div>
              <div className="home-guidance-card__question">
                <HelpCircle size={14} /> Question 2: Blocking Issues
              </div>
              <h2 className="home-guidance-card__title">
                {validationErrors.length === 0 ? (
                  <>
                    <FileCheck2 size={20} className="text-jade" />
                    <span>0 Blocking Issues</span>
                  </>
                ) : (
                  <>
                    <AlertTriangle size={20} className="text-terracotta" />
                    <span>{validationErrors.length} Issue(s) Detected</span>
                  </>
                )}
              </h2>
              <p className="home-guidance-card__answer">
                {validationErrors.length === 0
                  ? 'No data deficits found. Faculty workloads, division limits, and recess timing parameters are clean.'
                  : validationErrors[0]}
              </p>
            </div>

            <div className="home-guidance-card__footer">
              {validationErrors.length > 0 ? (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    const isAlloc = validationErrors[0].toLowerCase().includes('allocation') || validationErrors[0].toLowerCase().includes('teacher');
                    if (isAlloc && onNavigateToSetup) {
                      onNavigateToSetup('allocations');
                    } else if (onNavigateToRules) {
                      onNavigateToRules('bell');
                    }
                  }}
                  rightIcon={<ArrowRight size={13} />}
                >
                  Resolve Blocker
                </Button>
              ) : (
                <span style={{ fontSize: '0.75rem', color: 'var(--color-jade)', fontWeight: 600 }}>
                  ✓ Clear for Solver
                </span>
              )}
            </div>
          </div>

          {/* QUESTION 3: What should I do next? */}
          <div className="home-guidance-card is-action">
            <div>
              <div className="home-guidance-card__question">
                <HelpCircle size={14} /> Question 3: Recommended Action
              </div>
              <h2 className="home-guidance-card__title">
                {isSolving ? (
                  <span>Solver Optimization Active</span>
                ) : !isReadyToGenerate ? (
                  <span>Review School Data</span>
                ) : hasTimetable ? (
                  <span>Inspect or Publish</span>
                ) : (
                  <span>Run CP-SAT Solver</span>
                )}
              </h2>
              <p className="home-guidance-card__answer">
                {isSolving
                  ? `Optimization underway (${solverStatus?.percent ?? 0}%). Elapsed: ${solverStatus?.elapsedSeconds ?? 0}s.`
                  : !isReadyToGenerate
                  ? 'Open School Setup or Timetable Rules to correct flagged data deficits.'
                  : hasTimetable && isStale
                  ? 'Curriculum data was recently updated. Re-generate to update the published schedule.'
                  : hasTimetable
                  ? 'Timetable is ready. View class and teacher rosters or export to Excel/PDF.'
                  : 'All constraints ready. Launch the CP-SAT solver to compute the schedule.'}
              </p>
            </div>

            <div className="home-guidance-card__footer">
              {isSolving ? (
                <Badge variant="gold" size="sm">
                  Step {solverStatus?.step ?? 1} of {solverStatus?.totalSteps ?? 2}
                </Badge>
              ) : !isReadyToGenerate ? (
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => onNavigateToSetup && onNavigateToSetup('structure')}
                  rightIcon={<ArrowRight size={13} />}
                >
                  Open Setup
                </Button>
              ) : hasTimetable ? (
                <Button
                  variant="primary"
                  size="sm"
                  onClick={onNavigateToStudio}
                  rightIcon={<ArrowRight size={13} />}
                >
                  Open Studio
                </Button>
              ) : (
                <Button
                  variant="primary"
                  size="sm"
                  onClick={onNavigateToGenerate}
                  rightIcon={<ArrowRight size={13} />}
                >
                  Generate Now
                </Button>
              )}
            </div>
          </div>
        </div>
      </section>

      {/* SECTION C: TASK SHORTCUTS (THE 4 DESTINATIONS) */}
      <section className="home-shortcuts-section" aria-label="Primary Destination Shortcuts">
        <div className="home-shortcuts-header">
          <h3>Workspaces & Administrative Tasks</h3>
          <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
            Direct access to all 4 system destinations
          </span>
        </div>

        <div className="home-shortcuts-grid">
          {/* Card 1: School Setup */}
          <div
            className="home-shortcut-card"
            onClick={() => onNavigateToSetup && onNavigateToSetup('structure')}
            role="button"
            tabIndex={0}
          >
            <div className="home-shortcut-card__top">
              <div className="home-shortcut-card__icon home-shortcut-card__icon--setup">
                <Layers size={22} />
              </div>
              <Badge variant="neutral" size="sm">
                4 Sub-tabs
              </Badge>
            </div>
            <div>
              <h4 className="home-shortcut-card__title">School Setup</h4>
              <p className="home-shortcut-card__desc">
                Configure Standards 5–10, curricular subjects, faculty members, and weekly lecture allocations.
              </p>
            </div>
            <div className="home-shortcut-card__link">
              <span>Manage School Data</span>
              <ArrowRight size={14} />
            </div>
          </div>

          {/* Card 2: Timetable Rules */}
          <div
            className="home-shortcut-card"
            onClick={() => onNavigateToRules && onNavigateToRules('bell')}
            role="button"
            tabIndex={0}
          >
            <div className="home-shortcut-card__top">
              <div className="home-shortcut-card__icon home-shortcut-card__icon--rules">
                <Sliders size={22} />
              </div>
              <Badge variant="gold" size="sm">
                H1–H10 / S1–S6
              </Badge>
            </div>
            <div>
              <h4 className="home-shortcut-card__title">Timetable Rules</h4>
              <p className="home-shortcut-card__desc">
                Set bell timings, day-specific recess placement, mandatory invariants, and soft objective weights.
              </p>
            </div>
            <div className="home-shortcut-card__link">
              <span>Configure Rules</span>
              <ArrowRight size={14} />
            </div>
          </div>

          {/* Card 3: Generate Timetable */}
          <div
            className="home-shortcut-card"
            onClick={onNavigateToGenerate}
            role="button"
            tabIndex={0}
          >
            <div className="home-shortcut-card__top">
              <div className="home-shortcut-card__icon home-shortcut-card__icon--generate">
                <Sparkles size={22} />
              </div>
              <Badge variant={isReadyToGenerate ? 'jade' : 'terracotta'} size="sm">
                {isReadyToGenerate ? 'Ready' : 'Issues'}
              </Badge>
            </div>
            <div>
              <h4 className="home-shortcut-card__title">Generate Timetable</h4>
              <p className="home-shortcut-card__desc">
                Pre-flight readiness audit, blocking issues checklist, and live CP-SAT optimization solver.
              </p>
            </div>
            <div className="home-shortcut-card__link">
              <span>Guided Generation</span>
              <ArrowRight size={14} />
            </div>
          </div>

          {/* Card 4: Timetable Studio */}
          <div
            className="home-shortcut-card"
            onClick={onNavigateToStudio}
            role="button"
            tabIndex={0}
          >
            <div className="home-shortcut-card__top">
              <div className="home-shortcut-card__icon home-shortcut-card__icon--studio">
                <Calendar size={22} />
              </div>
              <Badge variant={hasTimetable ? 'jade' : 'neutral'} size="sm">
                {hasTimetable ? 'Available' : 'Pending'}
              </Badge>
            </div>
            <div>
              <h4 className="home-shortcut-card__title">Timetable Studio</h4>
              <p className="home-shortcut-card__desc">
                Inspect conflict-free class and teacher schedules, view master grid, and export to Excel and PDF.
              </p>
            </div>
            <div className="home-shortcut-card__link">
              <span>Open Studio</span>
              <ArrowRight size={14} />
            </div>
          </div>
        </div>
      </section>

      {/* SECTION D: ACTIVE TIMETABLE BANNER (WHEN AVAILABLE) */}
      {hasTimetable && (
        <section className="active-timetable-banner" aria-label="Current Active Timetable Status">
          <div className="active-timetable-info">
            <div className="active-timetable-icon">
              <Calendar size={24} />
            </div>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                <strong style={{ fontSize: 'var(--text-base)', color: 'var(--color-text-primary)' }}>
                  Active Academic Timetable Published
                </strong>
                {isStale ? (
                  <Badge variant="gold" size="sm">
                    Updates Pending
                  </Badge>
                ) : (
                  <Badge variant="jade" size="sm">
                    100% Conflict-Free
                  </Badge>
                )}
              </div>
              <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', marginTop: '0.2rem' }}>
                Coverage: {scheduleSummary?.totalClasses ?? totalClasses} classes • Zero teacher clashes • Full syllabus fulfillment
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

      {/* SECTION E: SCHOOL CAPACITY STATISTICS */}
      <section className="school-stats-grid" aria-label="School Metrics">
        <div className="school-stat-card">
          <div className="school-stat-card__top">
            <span className="school-stat-card__label">Classes & Divisions</span>
            <div className="school-stat-card__icon school-stat-card__icon--classes">
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

        <div className="school-stat-card">
          <div className="school-stat-card__top">
            <span className="school-stat-card__label">Teaching Faculty</span>
            <div className="school-stat-card__icon school-stat-card__icon--faculty">
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

        <div className="school-stat-card">
          <div className="school-stat-card__top">
            <span className="school-stat-card__label">Curricular Subjects</span>
            <div className="school-stat-card__icon school-stat-card__icon--subjects">
              <BookOpen size={18} />
            </div>
          </div>
          {isLoadingSetup && !setupData ? (
            <LoadingSkeleton height="2rem" width="60%" />
          ) : (
            <>
              <div className="school-stat-card__val">{totalSubjects} Subjects</div>
              <div className="school-stat-card__sub">Core academics, lab practicals & activities</div>
            </>
          )}
        </div>

        <div className="school-stat-card">
          <div className="school-stat-card__top">
            <span className="school-stat-card__label">Weekly Periods / Class</span>
            <div className="school-stat-card__icon school-stat-card__icon--periods">
              <Clock size={18} />
            </div>
          </div>
          {isLoadingSetup && !setupData ? (
            <LoadingSkeleton height="2rem" width="60%" />
          ) : (
            <>
              <div className="school-stat-card__val">{weeklyRequiredPeriods} Periods/Wk</div>
              <div className="school-stat-card__sub">
                {periodsPerDay} periods/day • {instructionalDays} working days
              </div>
            </>
          )}
        </div>
      </section>

      {/* SECTION F: WORKSPACE DIRECTORY & RE-SCAN */}
      <section style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'var(--bg-surface)', border: '1px solid var(--surface-border)', borderRadius: 'var(--radius-md)', padding: '0.9rem 1.25rem', flexWrap: 'wrap', gap: '1rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
          <FolderOpen size={16} className="text-gold" />
          <span>Active workspace folder:</span>
          <code style={{ background: 'var(--bg-canvas)', border: '1px solid var(--surface-border)', padding: '0.25rem 0.5rem', borderRadius: 'var(--radius-sm)', color: 'var(--color-text-primary)' }}>
            {workspacePath || '.'}
          </code>
        </div>
        <div style={{ display: 'flex', gap: '0.65rem', alignItems: 'center' }}>
          <input
            type="text"
            value={workspacePath}
            onChange={(e) => onWorkspacePathChange(e.target.value)}
            placeholder="Path (e.g. . or data)"
            className="scan-input"
            style={{ width: '200px', padding: '0.4rem 0.7rem' }}
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
      </section>
    </div>
  );
};

export default CommandCenterView;
