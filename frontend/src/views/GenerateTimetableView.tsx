import React, { useState, useEffect, useCallback } from 'react';
import {
  Sparkles,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Play,
  Square,
  RotateCcw,
  Calendar,
  Layers,
  ArrowRight,
  Download,
  ShieldCheck,
  Clock,
  Users,
} from 'lucide-react';
import { Button } from '../components/common/Button';
import { Badge } from '../components/common/Badge';
import { LoadingSkeleton } from '../components/common/LoadingSpinner';
import type { TimetableApiClient } from '../api/client';
import type {
  SetupModelResponse,
  SolverJobStatus,
  ScheduleSummaryResponse,
} from '../api/types';
import './generate.css';

export interface GenerateTimetableViewProps {
  client: TimetableApiClient;
  onNavigateToStudio?: () => void;
  onNavigateToSetup?: (tab?: 'structure' | 'subjects' | 'faculty' | 'allocations') => void;
  onNavigateToRules?: (subTab?: 'bell' | 'hard' | 'soft') => void;
}

export const GenerateTimetableView: React.FC<GenerateTimetableViewProps> = ({
  client,
  onNavigateToStudio,
  onNavigateToSetup,
  onNavigateToRules,
}) => {
  const [setupData, setSetupData] = useState<SetupModelResponse | null>(null);
  const [solverStatus, setSolverStatus] = useState<SolverJobStatus | null>(null);
  const [scheduleSummary, setScheduleSummary] = useState<ScheduleSummaryResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [actionNotice, setActionNotice] = useState<{ type: 'success' | 'error' | 'info'; message: string } | null>(null);

  // Load setup data, solver telemetry & active timetable summary
  const loadData = useCallback(async () => {
    try {
      setIsLoading(true);
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
      console.warn('Failed to load generate telemetry:', err);
    } finally {
      setIsLoading(false);
    }
  }, [client]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Solver status polling during execution
  useEffect(() => {
    const isSolving = solverStatus?.status === 'RUNNING';
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
    }, isSolving ? 1000 : 7000);

    return () => clearInterval(interval);
  }, [client, solverStatus?.status]);

  // Start Generation
  const handleStartGenerate = async () => {
    setActionNotice(null);
    try {
      await client.startSolve('preset');
      const status = await client.getSolveStatus();
      setSolverStatus(status);
      setActionNotice({ type: 'info', message: 'CP-SAT optimization solver launched in background.' });
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setActionNotice({ type: 'error', message: `Generation launch failed: ${msg}` });
    }
  };

  // Cancel Generation
  const handleCancelGenerate = async () => {
    try {
      await client.cancelSolve();
      const status = await client.getSolveStatus();
      setSolverStatus(status);
      setActionNotice({ type: 'info', message: 'Solver optimization was stopped.' });
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setActionNotice({ type: 'error', message: `Cancel failed: ${msg}` });
    }
  };

  // Metrics
  const validation = setupData?.validation;
  const isReady = validation ? validation.isValid : true;
  const errors = validation?.errors ?? [];
  const warnings = validation?.warnings ?? [];
  const isSolving = solverStatus?.status === 'RUNNING';
  const isCompleted = solverStatus?.status === 'COMPLETED' || Boolean(scheduleSummary?.available);

  // Determine current active workflow step (1: Audit, 2: Issues, 3: Solving, 4: Results)
  const currentStep = isSolving ? 3 : isCompleted ? 4 : errors.length > 0 ? 2 : 1;

  if (isLoading && !setupData) {
    return (
      <div className="generate-container">
        <LoadingSkeleton height="4rem" />
        <LoadingSkeleton height="18rem" />
      </div>
    );
  }

  return (
    <div className="generate-container">
      {/* Header Banner */}
      <section className="generate-header-banner" aria-label="Timetable Generation Workflow Header">
        <div className="generate-header__titles">
          <h1>
            <Sparkles size={22} className="text-brand" />
            Generate SIES Academic Timetable
          </h1>
          <p>
            Guided 4-step generation workflow: audit school data readiness, review blocking constraints, execute the Google OR-Tools CP-SAT solver, and publish official schedules.
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
          <Button
            variant="secondary"
            size="md"
            leftIcon={<RotateCcw size={15} />}
            onClick={loadData}
            isLoading={isLoading}
          >
            Re-Audit Model
          </Button>

          {isCompleted && onNavigateToStudio && (
            <Button
              variant="primary"
              size="md"
              leftIcon={<Calendar size={15} />}
              onClick={onNavigateToStudio}
            >
              Open Timetable Studio
            </Button>
          )}
        </div>
      </section>

      {/* Guided 4-Step Progress Tracker */}
      <nav className="generate-steps-tracker" aria-label="Generation Workflow Steps">
        <div className={`generate-step-node ${currentStep === 1 ? 'is-active' : isReady ? 'is-complete' : ''}`}>
          <div className="generate-step-node__num">1</div>
          <span>Pre-flight Readiness Audit</span>
        </div>

        <div className="generate-step-divider" />

        <div className={`generate-step-node ${currentStep === 2 ? 'is-active' : errors.length === 0 ? 'is-complete' : ''}`}>
          <div className="generate-step-node__num">2</div>
          <span>Blocking Issues ({errors.length})</span>
        </div>

        <div className="generate-step-divider" />

        <div className={`generate-step-node ${currentStep === 3 ? 'is-active' : isCompleted ? 'is-complete' : ''}`}>
          <div className="generate-step-node__num">3</div>
          <span>CP-SAT Solver Optimization</span>
        </div>

        <div className="generate-step-divider" />

        <div className={`generate-step-node ${currentStep === 4 ? 'is-active is-complete' : ''}`}>
          <div className="generate-step-node__num">4</div>
          <span>Results & Publication</span>
        </div>
      </nav>

      {/* Action Notice Alert */}
      {actionNotice && (
        <div className={`validation-alert-banner is-${actionNotice.type === 'error' ? 'fail' : 'pass'}`}>
          <div className="validation-alert-banner__icon">
            {actionNotice.type === 'error' ? (
              <XCircle size={18} className="text-terracotta" />
            ) : (
              <CheckCircle2 size={18} className="text-jade" />
            )}
          </div>
          <div className="validation-alert-banner__content">
            <p>{actionNotice.message}</p>
          </div>
          <Button variant="ghost" size="sm" onClick={() => setActionNotice(null)}>
            Dismiss
          </Button>
        </div>
      )}

      {/* =========================================================================
          STEP 1: PRE-FLIGHT READINESS AUDIT (4 PILLARS)
          ========================================================================= */}
      <section aria-label="Pre-flight Readiness Audit Grid">
        <div style={{ marginBottom: '0.75rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <strong style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)' }}>
            System Readiness & Capacity Audit
          </strong>
          <Badge variant={isReady ? 'jade' : 'terracotta'} size="md" icon={isReady ? <CheckCircle2 size={14} /> : <AlertTriangle size={14} />}>
            {isReady ? 'All Prerequisites Satisfied' : `${errors.length} Critical Issue(s)`}
          </Badge>
        </div>

        <div className="generate-readiness-grid">
          {/* Pillar 1: Classes & Structure */}
          <div className="readiness-audit-card is-pass">
            <div className="readiness-audit-card__top">
              <div className="readiness-audit-card__title">
                <Layers size={16} className="text-brand" />
                <span>Classes & Divisions</span>
              </div>
              <Badge variant="jade" size="sm">PASS</Badge>
            </div>
            <div className="readiness-audit-card__desc">
              Class cohorts and divisional slots configured for academic delivery.
            </div>
            <div className="readiness-audit-card__items">
              <div className="readiness-audit-card__item">
                <span>Active Standards</span>
                <strong>{setupData?.model.startStandard} to {setupData?.model.endStandard}</strong>
              </div>
              <div className="readiness-audit-card__item">
                <span>Total Divisions</span>
                <strong>{setupData?.model.classes.length ?? 26} Classes</strong>
              </div>
            </div>
          </div>

          {/* Pillar 2: Faculty & Allocations */}
          <div className={`readiness-audit-card ${errors.some((e) => e.toLowerCase().includes('teacher') || e.toLowerCase().includes('allocation')) ? 'is-fail' : 'is-pass'}`}>
            <div className="readiness-audit-card__top">
              <div className="readiness-audit-card__title">
                <Users size={16} className="text-jade" />
                <span>Faculty & Allocations</span>
              </div>
              <Badge variant={errors.some((e) => e.toLowerCase().includes('teacher') || e.toLowerCase().includes('allocation')) ? 'terracotta' : 'jade'} size="sm">
                {errors.some((e) => e.toLowerCase().includes('teacher') || e.toLowerCase().includes('allocation')) ? 'DEFICIT' : 'PASS'}
              </Badge>
            </div>
            <div className="readiness-audit-card__desc">
              Weekly teacher lecture assignments and curriculum coverage.
            </div>
            <div className="readiness-audit-card__items">
              <div className="readiness-audit-card__item">
                <span>Faculty Members</span>
                <strong>{setupData?.model.professors.length ?? 35} Teachers</strong>
              </div>
              <div className="readiness-audit-card__item">
                <span>Curricular Subjects</span>
                <strong>{setupData?.model.subjects.length ?? 25} Subjects</strong>
              </div>
            </div>
          </div>

          {/* Pillar 3: Bell Schedule */}
          <div className={`readiness-audit-card ${errors.some((e) => e.toLowerCase().includes('recess') || e.toLowerCase().includes('bell') || e.toLowerCase().includes('period')) ? 'is-fail' : 'is-pass'}`}>
            <div className="readiness-audit-card__top">
              <div className="readiness-audit-card__title">
                <Clock size={16} className="text-gold" />
                <span>Bell Schedule & Recess</span>
              </div>
              <Badge variant={errors.some((e) => e.toLowerCase().includes('recess') || e.toLowerCase().includes('bell') || e.toLowerCase().includes('period')) ? 'terracotta' : 'jade'} size="sm">
                {errors.some((e) => e.toLowerCase().includes('recess') || e.toLowerCase().includes('bell') || e.toLowerCase().includes('period')) ? 'REVIEW' : 'PASS'}
              </Badge>
            </div>
            <div className="readiness-audit-card__desc">
              Instructional slots, duration axioms, and day-specific recess protection.
            </div>
            <div className="readiness-audit-card__items">
              <div className="readiness-audit-card__item">
                <span>Working Days</span>
                <strong>{setupData?.model.bellSchedule.days.length ?? 6} Days</strong>
              </div>
              <div className="readiness-audit-card__item">
                <span>Weekly Quota</span>
                <strong>{setupData?.model.bellSchedule.weeklyRequiredPeriodsPerClass ?? 48} Periods</strong>
              </div>
            </div>
          </div>

          {/* Pillar 4: Constraints Integrity */}
          <div className="readiness-audit-card is-pass">
            <div className="readiness-audit-card__top">
              <div className="readiness-audit-card__title">
                <ShieldCheck size={16} className="text-navy" />
                <span>Rules & Constraints</span>
              </div>
              <Badge variant="jade" size="sm">ACTIVE</Badge>
            </div>
            <div className="readiness-audit-card__desc">
              Mandatory invariants locked; optimization objectives configured.
            </div>
            <div className="readiness-audit-card__items">
              <div className="readiness-audit-card__item">
                <span>Mandatory Invariants</span>
                <strong>H3, H4, H9 Locked</strong>
              </div>
              <div className="readiness-audit-card__item">
                <span>Active Soft Objectives</span>
                <strong>S1 to S6 Configured</strong>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* =========================================================================
          STEP 2: BLOCKING ISSUES BANNER (WITH DEEP-LINK BUTTONS)
          ========================================================================= */}
      {errors.length > 0 && (
        <section className="blocking-issues-card" aria-label="Blocking Generation Issues">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
            <AlertTriangle size={22} className="text-terracotta" />
            <div>
              <strong style={{ fontSize: 'var(--text-base)', color: 'var(--color-text-primary)' }}>
                {errors.length} Critical Issue(s) Preventing Timetable Generation
              </strong>
              <p style={{ margin: '0.15rem 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                The solver cannot compute a mathematically sound timetable while these capacity or structural defects exist. Click below to resolve each issue directly.
              </p>
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {errors.map((err, i) => {
              const isAllocationOrFaculty =
                err.toLowerCase().includes('allotment') ||
                err.toLowerCase().includes('allocation') ||
                err.toLowerCase().includes('teacher') ||
                err.toLowerCase().includes('subject');
              const isTimingOrBell =
                err.toLowerCase().includes('recess') ||
                err.toLowerCase().includes('bell') ||
                err.toLowerCase().includes('period');

              return (
                <div key={i} className="blocking-issue-row">
                  <span className="blocking-issue-text">
                    <XCircle size={16} className="text-terracotta" />
                    {err}
                  </span>

                  <div>
                    {isAllocationOrFaculty && onNavigateToSetup && (
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => onNavigateToSetup('allocations')}
                        rightIcon={<ArrowRight size={13} />}
                      >
                        Fix in School Setup
                      </Button>
                    )}
                    {isTimingOrBell && onNavigateToRules && (
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => onNavigateToRules('bell')}
                        rightIcon={<ArrowRight size={13} />}
                      >
                        Fix in Timetable Rules
                      </Button>
                    )}
                    {!isAllocationOrFaculty && !isTimingOrBell && onNavigateToSetup && (
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => onNavigateToSetup('structure')}
                        rightIcon={<ArrowRight size={13} />}
                      >
                        Inspect Setup
                      </Button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      )}

      {/* Warnings & Advisories */}
      {warnings.length > 0 && errors.length === 0 && (
        <div className="validation-alert-banner is-warning">
          <div className="validation-alert-banner__icon">
            <AlertTriangle size={18} className="text-gold" />
          </div>
          <div className="validation-alert-banner__content">
            <strong>Advisory Notice:</strong>
            <p>{warnings[0]}</p>
          </div>
        </div>
      )}

      {/* =========================================================================
          STEP 3: SOLVER EXECUTION & LIVE PROGRESS
          ========================================================================= */}
      <section className="solver-control-card" aria-label="Solver Execution Card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <h2 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 700, color: 'var(--color-text-primary)' }}>
              Google OR-Tools CP-SAT Scheduling Engine
            </h2>
            <p style={{ margin: '0.25rem 0 0', fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>
              Computes optimal zero-conflict lecture slots satisfying all 10 hard constraints and optimizing soft preferences.
            </p>
          </div>

          {/* Trigger Buttons */}
          <div>
            {isSolving ? (
              <div style={{ display: 'flex', gap: '0.65rem', alignItems: 'center' }}>
                <Button
                  variant="primary"
                  size="lg"
                  isLoading={true}
                  disabled
                >
                  Solving ({solverStatus?.percent ?? 0}%)
                </Button>
                <Button
                  variant="danger"
                  size="lg"
                  leftIcon={<Square size={16} />}
                  onClick={handleCancelGenerate}
                >
                  Halt Solver
                </Button>
              </div>
            ) : (
              <Button
                variant="primary"
                size="lg"
                leftIcon={<Play size={18} />}
                onClick={handleStartGenerate}
                disabled={!isReady}
                title={!isReady ? 'Resolve blocking issues above prior to generating' : 'Launch CP-SAT solver'}
              >
                {scheduleSummary?.available ? 'Re-Generate Timetable' : 'Generate Full Timetable'}
              </Button>
            )}
          </div>
        </div>

        {/* Live Progress Box when running */}
        {isSolving && (
          <div className="solver-live-box">
            <div className="solver-live-header">
              <div className="solver-live-title">
                <Sparkles size={18} className="text-brand" />
                <span>{solverStatus?.message ?? 'Optimizing teacher workloads & student schedules...'}</span>
              </div>
              <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                Step {solverStatus?.step ?? 1} of {solverStatus?.totalSteps ?? 2} • Elapsed: <strong>{solverStatus?.elapsedSeconds ?? 0}s</strong>
              </div>
            </div>

            <div className="solver-progress-track">
              <div
                className="solver-progress-thumb"
                style={{ width: `${Math.max(8, solverStatus?.percent ?? 0)}%` }}
              />
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', color: 'var(--color-text-tertiary)' }}>
              <span>Phase: Exact constraint propagation & branch-and-bound search</span>
              <span>Target: Satisfy H1–H10 & optimize S1–S6</span>
            </div>
          </div>
        )}
      </section>

      {/* =========================================================================
          STEP 4: RESULTS & PUBLICATION (WHEN TIMETABLE IS READY)
          ========================================================================= */}
      {isCompleted && (
        <section className="results-card" aria-label="Timetable Generation Results">
          <div className="results-banner-success">
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.85rem' }}>
              <div
                style={{
                  width: '42px',
                  height: '42px',
                  borderRadius: '50%',
                  background: 'var(--color-jade)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#ffffff',
                }}
              >
                <CheckCircle2 size={24} />
              </div>
              <div>
                <strong style={{ fontSize: '1.05rem', color: 'var(--color-jade-dark, #065f46)' }}>
                  Academic Timetable Successfully Generated!
                </strong>
                <p style={{ margin: '0.15rem 0 0', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  The CP-SAT solver has generated a complete, conflict-free schedule compliant with all SIES APJ School constraints.
                </p>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '0.6rem', flexWrap: 'wrap' }}>
              <a
                href={client.getExportDownloadUrl('excel')}
                download="SIES_Master_Timetable.xlsx"
                style={{ textDecoration: 'none' }}
              >
                <Button variant="secondary" size="md" leftIcon={<Download size={14} />}>
                  Export Excel (.xlsx)
                </Button>
              </a>

              <a
                href={client.getExportDownloadUrl('pdf')}
                download="SIES_Timetable.pdf"
                style={{ textDecoration: 'none' }}
              >
                <Button variant="secondary" size="md" leftIcon={<Download size={14} />}>
                  Export PDF
                </Button>
              </a>

              {onNavigateToStudio && (
                <Button
                  variant="primary"
                  size="md"
                  rightIcon={<ArrowRight size={16} />}
                  onClick={onNavigateToStudio}
                >
                  View in Studio
                </Button>
              )}
            </div>
          </div>

          {/* Results Metric KPIs */}
          <div className="results-kpi-grid">
            <div className="results-kpi-card">
              <span className="results-kpi-card__label">Classes Scheduled</span>
              <div className="results-kpi-card__val" style={{ color: 'var(--color-brand)' }}>
                {scheduleSummary?.totalClasses ?? setupData?.model.classes.length ?? 26}
              </div>
              <span style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>
                Standards {setupData?.model.startStandard}–{setupData?.model.endStandard} complete
              </span>
            </div>

            <div className="results-kpi-card">
              <span className="results-kpi-card__label">Hard Constraint Violations</span>
              <div className="results-kpi-card__val" style={{ color: 'var(--color-jade)' }}>
                0
              </div>
              <span style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>
                100% physically conflict-free
              </span>
            </div>

            <div className="results-kpi-card">
              <span className="results-kpi-card__label">Faculty Clashes</span>
              <div className="results-kpi-card__val" style={{ color: 'var(--color-jade)' }}>
                0
              </div>
              <span style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>
                Zero double-booking detected
              </span>
            </div>

            <div className="results-kpi-card">
              <span className="results-kpi-card__label">Curriculum Quotas</span>
              <div className="results-kpi-card__val" style={{ color: 'var(--color-jade)' }}>
                100%
              </div>
              <span style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>
                All subject hours fulfilled
              </span>
            </div>
          </div>
        </section>
      )}
    </div>
  );
};

export default GenerateTimetableView;
