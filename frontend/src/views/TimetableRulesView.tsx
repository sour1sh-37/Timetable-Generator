import React, { useState, useEffect, useMemo, useCallback } from 'react';
import {
  Clock,
  Shield,
  SlidersHorizontal,
  Lock,
  RotateCcw,
  Save,
  CheckCircle2,
  AlertTriangle,
  ArrowRight,
  Coffee,
  Check,
  Calendar,
  Info,
} from 'lucide-react';
import { Button } from '../components/common/Button';
import { Badge } from '../components/common/Badge';
import { LoadingSkeleton } from '../components/common/LoadingSpinner';
import type { TimetableApiClient } from '../api/client';
import type {
  SetupModelDto,
  ConstraintsSetupDto,
} from '../api/types';
import { DEFAULT_CONSTRAINTS } from './SchoolSetupView';
import './rules.css';

export type RulesSubTab = 'bell' | 'hard' | 'soft';

export interface TimetableRulesViewProps {
  client: TimetableApiClient;
  activeSubTab?: RulesSubTab;
  onSubTabChange?: (tab: RulesSubTab) => void;
  onNavigateToGenerate?: () => void;
}

export const TimetableRulesView: React.FC<TimetableRulesViewProps> = ({
  client,
  activeSubTab: controlledTab,
  onSubTabChange,
  onNavigateToGenerate,
}) => {
  const [internalTab, setInternalTab] = useState<RulesSubTab>('bell');
  const activeTab = controlledTab ?? internalTab;

  const handleSelectTab = (tab: RulesSubTab) => {
    if (onSubTabChange) {
      onSubTabChange(tab);
    }
    setInternalTab(tab);
  };

  const [model, setModel] = useState<SetupModelDto | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [isDirty, setIsDirty] = useState<boolean>(false);
  const [feedbackMsg, setFeedbackMsg] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

  // Load Setup Model from backend
  const loadData = useCallback(async () => {
    setIsLoading(true);
    setFeedbackMsg(null);
    try {
      const res = await client.getSetupModel();
      const serverConstraints = res.model.constraints;
      const mergedConstraints: ConstraintsSetupDto = {
        hardConstraints: {
          ...DEFAULT_CONSTRAINTS.hardConstraints,
          ...(serverConstraints?.hardConstraints || {}),
        },
        softConstraints: {
          ...DEFAULT_CONSTRAINTS.softConstraints,
          ...(serverConstraints?.softConstraints || {}),
        },
      };
      setModel({
        ...res.model,
        constraints: mergedConstraints,
      });
      setIsDirty(false);
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setFeedbackMsg({ text: 'Failed to load school timing and constraints: ' + msg, type: 'error' });
    } finally {
      setIsLoading(false);
    }
  }, [client]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Current constraints with defaults
  const currentConstraints = useMemo<ConstraintsSetupDto>(() => {
    if (!model || !model.constraints) return DEFAULT_CONSTRAINTS;
    return {
      hardConstraints: {
        ...DEFAULT_CONSTRAINTS.hardConstraints,
        ...model.constraints.hardConstraints,
      },
      softConstraints: {
        ...DEFAULT_CONSTRAINTS.softConstraints,
        ...model.constraints.softConstraints,
      },
    };
  }, [model]);

  // Constraint counts
  const activeHardCount = useMemo(() => {
    return Object.values(currentConstraints.hardConstraints).filter((c) => c.mandatory || c.enabled).length;
  }, [currentConstraints]);

  const mandatoryCount = useMemo(() => {
    return Object.values(currentConstraints.hardConstraints).filter((c) => c.mandatory).length;
  }, [currentConstraints]);

  const activeSoftCount = useMemo(() => {
    return Object.values(currentConstraints.softConstraints).filter((c) => c.enabled && c.weight > 0).length;
  }, [currentConstraints]);

  const totalSoftWeight = useMemo(() => {
    return Object.values(currentConstraints.softConstraints)
      .filter((c) => c.enabled)
      .reduce((sum, c) => sum + (c.weight || 0), 0);
  }, [currentConstraints]);

  // Hard constraint toggle
  const handleToggleHardConstraint = (id: string) => {
    if (!model) return;
    const target = currentConstraints.hardConstraints[id];
    if (!target || target.mandatory) return; // Locked mandatory invariant
    const updatedConstraints: ConstraintsSetupDto = {
      ...currentConstraints,
      hardConstraints: {
        ...currentConstraints.hardConstraints,
        [id]: {
          ...target,
          enabled: !target.enabled,
        },
      },
    };
    setModel({
      ...model,
      constraints: updatedConstraints,
    });
    setIsDirty(true);
  };

  // Hard constraint parameter change
  const handleHardParamChange = (id: string, paramKey: string, val: number) => {
    if (!model) return;
    const target = currentConstraints.hardConstraints[id];
    if (!target) return;
    const updatedConstraints: ConstraintsSetupDto = {
      ...currentConstraints,
      hardConstraints: {
        ...currentConstraints.hardConstraints,
        [id]: {
          ...target,
          [paramKey]: val,
        },
      },
    };
    setModel({
      ...model,
      constraints: updatedConstraints,
    });
    setIsDirty(true);
  };

  // Soft constraint toggle
  const handleToggleSoftConstraint = (id: string) => {
    if (!model) return;
    const target = currentConstraints.softConstraints[id];
    if (!target) return;
    const updatedConstraints: ConstraintsSetupDto = {
      ...currentConstraints,
      softConstraints: {
        ...currentConstraints.softConstraints,
        [id]: {
          ...target,
          enabled: !target.enabled,
        },
      },
    };
    setModel({
      ...model,
      constraints: updatedConstraints,
    });
    setIsDirty(true);
  };

  // Soft constraint weight change
  const handleSoftWeightChange = (id: string, weight: number) => {
    if (!model) return;
    const target = currentConstraints.softConstraints[id];
    if (!target) return;
    const clampedWeight = Math.max(0, Math.min(500, Math.round(weight)));
    const updatedConstraints: ConstraintsSetupDto = {
      ...currentConstraints,
      softConstraints: {
        ...currentConstraints.softConstraints,
        [id]: {
          ...target,
          weight: clampedWeight,
          enabled: clampedWeight > 0 ? target.enabled : false,
        },
      },
    };
    setModel({
      ...model,
      constraints: updatedConstraints,
    });
    setIsDirty(true);
  };

  // Reset constraints to defaults
  const handleResetConstraints = () => {
    if (!model) return;
    if (!window.confirm('Reset all hard and soft constraints to official SIES APJ School default settings?')) {
      return;
    }
    setModel({
      ...model,
      constraints: JSON.parse(JSON.stringify(DEFAULT_CONSTRAINTS)),
    });
    setIsDirty(true);
    setFeedbackMsg({
      text: 'Constraints reset to official SIES APJ School defaults. Click "Save Configuration" to persist.',
      type: 'success',
    });
  };

  // Toggle active day
  const handleToggleDay = (day: string) => {
    if (!model) return;
    const activeDays = model.bellSchedule.days;
    let nextDays: string[];
    if (activeDays.includes(day)) {
      if (activeDays.length <= 1) {
        setFeedbackMsg({ text: 'At least one working instructional day must remain active.', type: 'error' });
        return;
      }
      nextDays = activeDays.filter((d) => d !== day);
    } else {
      const order = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
      nextDays = [...activeDays, day].sort((a, b) => order.indexOf(a) - order.indexOf(b));
    }
    const nextDaily = { ...model.bellSchedule.dailyPeriods };
    if (!nextDaily[day] || nextDaily[day] === 0) {
      nextDaily[day] = day === 'SATURDAY' ? 4 : 8;
    }
    setModel({
      ...model,
      bellSchedule: {
        ...model.bellSchedule,
        days: nextDays,
        dailyPeriods: nextDaily,
      },
    });
    setIsDirty(true);
  };

  // Daily periods change
  const handleDailyPeriodChange = (day: string, count: number) => {
    if (!model) return;
    const val = Math.max(0, Math.min(12, count));
    const nextDaily = { ...model.bellSchedule.dailyPeriods, [day]: val };
    const nextDailyRecess = { ...(model.bellSchedule.dailyRecessAfterPeriod || {}) };
    if (val > 1) {
      const currentRec = nextDailyRecess[day] || model.bellSchedule.recessAfterPeriod || 4;
      if (currentRec >= val) {
        nextDailyRecess[day] = Math.max(1, val - 1);
      }
    }
    setModel({
      ...model,
      bellSchedule: {
        ...model.bellSchedule,
        dailyPeriods: nextDaily,
        dailyRecessAfterPeriod: nextDailyRecess,
      },
    });
    setIsDirty(true);
  };

  // Recess position change
  const handleRecessPeriodChange = (day: string, periodNumber: number) => {
    if (!model) return;
    const pCount = model.bellSchedule.dailyPeriods[day] || 8;
    const safePeriod = Math.max(1, Math.min(pCount - 1, periodNumber));
    const nextDailyRecess = { ...(model.bellSchedule.dailyRecessAfterPeriod || {}) };
    nextDailyRecess[day] = safePeriod;

    setModel({
      ...model,
      bellSchedule: {
        ...model.bellSchedule,
        dailyRecessAfterPeriod: nextDailyRecess,
      },
    });
    setIsDirty(true);
  };

  // Recess toggle per day
  const handleToggleDailyRecess = (day: string) => {
    if (!model) return;
    const currentEnabled = model.bellSchedule.dailyRecessEnabled
      ? model.bellSchedule.dailyRecessEnabled[day] !== false
      : day !== 'SATURDAY';

    const nextRecessEnabled = {
      ...(model.bellSchedule.dailyRecessEnabled || {}),
      [day]: !currentEnabled,
    };

    const nextDailyRecess = { ...(model.bellSchedule.dailyRecessAfterPeriod || {}) };
    if (!currentEnabled) {
      const dPeriods = model.bellSchedule.dailyPeriods[day] || (day === 'SATURDAY' ? 4 : 8);
      const safeRec = day === 'SATURDAY' ? 2 : Math.min(4, Math.max(1, dPeriods - 1));
      nextDailyRecess[day] = safeRec;
    }

    setModel({
      ...model,
      bellSchedule: {
        ...model.bellSchedule,
        dailyRecessEnabled: nextRecessEnabled,
        dailyRecessAfterPeriod: nextDailyRecess,
      },
    });
    setIsDirty(true);
  };

  // Save changes
  const handleSaveModel = async () => {
    if (!model) return;
    const activeDays = model.bellSchedule.days;
    if (activeDays.length === 0) {
      setFeedbackMsg({ text: 'Error: At least one working instructional day must be selected.', type: 'error' });
      return;
    }

    // Pre-flight recess placement verification
    for (const d of activeDays) {
      const isEnabled = model.bellSchedule.dailyRecessEnabled
        ? model.bellSchedule.dailyRecessEnabled[d] !== false
        : d !== 'SATURDAY';
      if (isEnabled) {
        const dPeriods = model.bellSchedule.dailyPeriods[d] || 0;
        const rawRec = model.bellSchedule.dailyRecessAfterPeriod?.[d];
        const dRec = (rawRec && rawRec > 0) ? rawRec : (model.bellSchedule.recessAfterPeriod ?? 4);
        if (dPeriods <= 1) {
          setFeedbackMsg({
            text: `Invalid bell schedule for ${d}: Recess cannot be scheduled when daily periods is ${dPeriods}.`,
            type: 'error',
          });
          return;
        }
        if (dRec < 1 || dRec >= dPeriods) {
          setFeedbackMsg({
            text: `Invalid recess placement for ${d}: Recess after period ${dRec} must be between 1 and ${dPeriods - 1} (daily periods: ${dPeriods}).`,
            type: 'error',
          });
          return;
        }
      }
    }

    setIsSaving(true);
    setFeedbackMsg(null);
    try {
      const maxDaily = Math.max(...activeDays.map((d) => model.bellSchedule.dailyPeriods[d] || 8));
      const cleanDailyRecessAfterPeriod: Record<string, number> = { ...(model.bellSchedule.dailyRecessAfterPeriod || {}) };
      for (const d of activeDays) {
        const cur = cleanDailyRecessAfterPeriod[d];
        const dPeriods = model.bellSchedule.dailyPeriods[d] || 8;
        if (!cur || cur < 1) {
          cleanDailyRecessAfterPeriod[d] = Math.max(1, Math.min(model.bellSchedule.recessAfterPeriod || 4, dPeriods > 1 ? dPeriods - 1 : 1));
        }
      }

      const modelToSave: SetupModelDto = {
        ...model,
        bellSchedule: {
          ...model.bellSchedule,
          dailyRecessAfterPeriod: cleanDailyRecessAfterPeriod,
          periodsPerDay: maxDaily,
        },
      };

      const res = await client.updateSetupModel(modelToSave);
      const serverConstraints = res.model.constraints;
      const mergedConstraints: ConstraintsSetupDto = {
        hardConstraints: {
          ...DEFAULT_CONSTRAINTS.hardConstraints,
          ...(serverConstraints?.hardConstraints || {}),
        },
        softConstraints: {
          ...DEFAULT_CONSTRAINTS.softConstraints,
          ...(serverConstraints?.softConstraints || {}),
        },
      };
      setModel({
        ...res.model,
        constraints: mergedConstraints,
      });
      setIsDirty(false);
      setFeedbackMsg({ text: 'Timetable rules & optimization constraints successfully saved!', type: 'success' });
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setFeedbackMsg({ text: 'Error saving rules: ' + msg, type: 'error' });
    } finally {
      setIsSaving(false);
    }
  };

  // Preview timeline helper for Monday or Saturday
  const timelinePreview = useMemo(() => {
    if (!model) return [];
    const targetDay = 'MONDAY';
    const periodCount = model.bellSchedule.dailyPeriods[targetDay] ?? 8;
    const startTimeStr = model.bellSchedule.schoolStartTime || '07:10';
    const [startH, startM] = startTimeStr.split(':').map(Number);
    let curMinutes = (startH || 7) * 60 + (startM || 10);

    const recessEnabled = model.bellSchedule.dailyRecessEnabled
      ? model.bellSchedule.dailyRecessEnabled[targetDay] !== false
      : true;
    const recessAfter = model.bellSchedule.dailyRecessAfterPeriod?.[targetDay] ?? model.bellSchedule.recessAfterPeriod ?? 4;
    const recessDur = model.bellSchedule.recessDuration || 20;
    const defaultDur = model.bellSchedule.defaultPeriodDuration || 35;
    const p1Override = model.bellSchedule.periodDurationOverrides?.['1'] ?? (defaultDur === 35 ? 40 : defaultDur);

    const slots = [];
    for (let p = 1; p <= periodCount; p++) {
      const dur = p === 1 ? p1Override : defaultDur;
      const endMinutes = curMinutes + dur;
      const formatTime = (mins: number) => {
        const h = Math.floor(mins / 60) % 24;
        const m = mins % 60;
        return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
      };
      slots.push({
        type: 'period',
        num: p,
        label: `Period ${p}`,
        time: `${formatTime(curMinutes)} – ${formatTime(endMinutes)}`,
        dur: `${dur}m`,
      });
      curMinutes = endMinutes;

      if (recessEnabled && p === recessAfter) {
        const recEnd = curMinutes + recessDur;
        slots.push({
          type: 'recess',
          num: 0,
          label: 'Recess',
          time: `${formatTime(curMinutes)} – ${formatTime(recEnd)}`,
          dur: `${recessDur}m`,
        });
        curMinutes = recEnd;
      }
    }
    return slots;
  }, [model, activeTab]);

  if (isLoading || !model) {
    return (
      <div className="rules-container">
        <LoadingSkeleton height="4rem" />
        <LoadingSkeleton height="20rem" />
      </div>
    );
  }

  const allDays = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

  return (
    <div className="rules-container">
      {/* Header Banner */}
      <section className="rules-header-banner" aria-label="Timetable Rules & Optimization Banner">
        <div className="rules-header__titles">
          <h1>
            <SlidersHorizontal size={22} className="text-brand" />
            Timetable Rules & Constraints
          </h1>
          <p>
            Configure bell schedule parameters, day-specific recess timings, mandatory CP-SAT operational invariants (H1–H10), and soft objective priorities (S1–S6).
          </p>
        </div>

        <div className="rules-header__actions">
          <Button
            variant="secondary"
            size="md"
            onClick={handleResetConstraints}
            disabled={isSaving}
          >
            <RotateCcw size={15} /> Reset Defaults
          </Button>

          <Button
            variant="primary"
            size="md"
            onClick={handleSaveModel}
            isLoading={isSaving}
          >
            <Save size={15} /> Save Rules
          </Button>

          {onNavigateToGenerate && (
            <Button
              variant="secondary"
              size="md"
              onClick={onNavigateToGenerate}
              rightIcon={<ArrowRight size={15} />}
            >
              Generate Timetable
            </Button>
          )}
        </div>
      </section>

      {/* Summary KPI Bar */}
      <section className="rules-summary-bar" aria-label="Rules and Constraints Summary">
        <div className="rules-stat-card">
          <span className="rules-stat-card__label">Bell Schedule</span>
          <div className="rules-stat-card__val">
            {model.bellSchedule.days.length} Days Active
          </div>
          <span className="rules-stat-card__sub">
            {model.bellSchedule.weeklyRequiredPeriodsPerClass} weekly lectures required per class
          </span>
        </div>

        <div className="rules-stat-card">
          <span className="rules-stat-card__label">Hard Constraints</span>
          <div className="rules-stat-card__val">
            {activeHardCount} <span style={{ fontSize: '0.9rem', color: 'var(--color-text-secondary)' }}>/ 10 Active</span>
          </div>
          <span className="rules-stat-card__sub">
            {mandatoryCount} mandatory invariants locked
          </span>
        </div>

        <div className="rules-stat-card">
          <span className="rules-stat-card__label">Soft Objectives</span>
          <div className="rules-stat-card__val">
            {activeSoftCount} <span style={{ fontSize: '0.9rem', color: 'var(--color-text-secondary)' }}>/ 6 Weighted</span>
          </div>
          <span className="rules-stat-card__sub">
            Total solver priority weight: {totalSoftWeight}
          </span>
        </div>
      </section>

      {/* Feedback Messages */}
      {feedbackMsg && (
        <div
          className={`validation-alert-banner is-${feedbackMsg.type === 'error' ? 'fail' : 'pass'}`}
          style={{ marginBottom: '0.5rem' }}
        >
          <div className="validation-alert-banner__icon">
            {feedbackMsg.type === 'error' ? (
              <AlertTriangle size={18} className="text-terracotta" />
            ) : (
              <CheckCircle2 size={18} className="text-jade" />
            )}
          </div>
          <div className="validation-alert-banner__content">
            <p>{feedbackMsg.text}</p>
          </div>
          <Button variant="ghost" size="sm" onClick={() => setFeedbackMsg(null)}>
            Dismiss
          </Button>
        </div>
      )}

      {/* Sub-Navigation Tabs */}
      <nav className="rules-nav-tabs" aria-label="Timetable Rules Sub-sections">
        <button
          type="button"
          className={`rules-nav-tab ${activeTab === 'bell' ? 'is-active' : ''}`}
          onClick={() => handleSelectTab('bell')}
        >
          <Clock size={16} />
          <span>School Timings & Bell Schedule</span>
          <span className="rules-nav-tab__badge">{model.bellSchedule.days.length}d</span>
        </button>

        <button
          type="button"
          className={`rules-nav-tab ${activeTab === 'hard' ? 'is-active' : ''}`}
          onClick={() => handleSelectTab('hard')}
        >
          <Shield size={16} />
          <span>Hard Constraints (H1–H10)</span>
          <span className="rules-nav-tab__badge">{activeHardCount}/10</span>
        </button>

        <button
          type="button"
          className={`rules-nav-tab ${activeTab === 'soft' ? 'is-active' : ''}`}
          onClick={() => handleSelectTab('soft')}
        >
          <SlidersHorizontal size={16} />
          <span>Soft Objectives (S1–S6)</span>
          <span className="rules-nav-tab__badge">{activeSoftCount}/6</span>
        </button>
      </nav>

      {/* =========================================================================
          TAB 1: TIMINGS & BELL SCHEDULE
          ========================================================================= */}
      {activeTab === 'bell' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          {/* Card 1: Working Days & Daily Periods */}
          <div className="rules-card">
            <div className="rules-card__header">
              <div>
                <h2 className="rules-card__title">
                  <Calendar size={18} className="text-brand" /> Working Days & Daily Teaching Periods
                </h2>
                <p className="rules-card__subtitle">
                  Activate teaching days and configure how many instruction periods are scheduled each day.
                </p>
              </div>
            </div>

            {/* Active Day Pills */}
            <div>
              <label style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--color-text-secondary)', display: 'block', marginBottom: '0.5rem' }}>
                Active School Days:
              </label>
              <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                {allDays.map((day) => {
                  const isSelected = model.bellSchedule.days.includes(day);
                  return (
                    <button
                      key={day}
                      type="button"
                      onClick={() => handleToggleDay(day)}
                      style={{
                        padding: '0.5rem 1rem',
                        borderRadius: 'var(--radius-md)',
                        border: isSelected ? '1px solid var(--color-brand)' : '1px solid var(--surface-border-strong)',
                        backgroundColor: isSelected ? 'var(--color-brand-subtle)' : 'var(--bg-canvas)',
                        color: isSelected ? 'var(--color-brand)' : 'var(--color-text-secondary)',
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '0.45rem',
                        fontSize: '0.82rem',
                        fontWeight: isSelected ? 700 : 500,
                        transition: 'all var(--transition-fast)',
                      }}
                    >
                      {isSelected && <Check size={14} className="text-brand" />}
                      <span>{day.charAt(0) + day.slice(1).toLowerCase()}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Daily Periods Grid */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '0.75rem', marginTop: '0.5rem' }}>
              {allDays.map((day) => {
                const isActive = model.bellSchedule.days.includes(day);
                const pCount = model.bellSchedule.dailyPeriods[day] ?? (isActive ? (day === 'SATURDAY' ? 4 : 8) : 0);
                const recessEnabled = model.bellSchedule.dailyRecessEnabled
                  ? model.bellSchedule.dailyRecessEnabled[day] !== false
                  : day !== 'SATURDAY';
                const recessAfter = model.bellSchedule.dailyRecessAfterPeriod?.[day] ?? (day === 'SATURDAY' ? 2 : 4);

                return (
                  <div
                    key={day}
                    style={{
                      padding: '0.85rem 1rem',
                      background: isActive ? 'var(--bg-canvas)' : 'var(--surface-border-subtle)',
                      border: '1px solid var(--surface-border)',
                      borderRadius: 'var(--radius-md)',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '0.6rem',
                      opacity: isActive ? 1 : 0.6,
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <strong style={{ fontSize: '0.85rem', color: 'var(--color-text-primary)' }}>
                        {day.charAt(0) + day.slice(1).toLowerCase()}
                      </strong>
                      <Badge variant={isActive ? 'jade' : 'neutral'} size="sm">
                        {isActive ? 'Active' : 'Off'}
                      </Badge>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <span style={{ fontSize: '0.78rem', color: 'var(--color-text-secondary)' }}>Daily Periods:</span>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                        <input
                          type="number"
                          min={isActive ? 1 : 0}
                          max={12}
                          disabled={!isActive}
                          value={isActive ? pCount : 0}
                          onChange={(e) => handleDailyPeriodChange(day, parseInt(e.target.value, 10) || 0)}
                          style={{
                            width: '60px',
                            padding: '0.3rem 0.45rem',
                            border: '1px solid var(--surface-border-strong)',
                            borderRadius: 'var(--radius-xs)',
                            textAlign: 'center',
                            fontWeight: 700,
                          }}
                        />
                        <span style={{ fontSize: '0.75rem', color: 'var(--color-text-tertiary)' }}>slots</span>
                      </div>
                    </div>

                    {/* Day-specific Recess */}
                    {isActive && (
                      <div style={{ borderTop: '1px solid var(--surface-border-subtle)', paddingTop: '0.5rem', display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <label style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: '0.35rem', cursor: 'pointer' }}>
                            <input
                              type="checkbox"
                              checked={recessEnabled}
                              onChange={() => handleToggleDailyRecess(day)}
                            />
                            <span>Recess break</span>
                          </label>
                          {recessEnabled && (
                            <span style={{ fontSize: '0.72rem', color: 'var(--color-brand)', fontWeight: 600 }}>
                              After Period {recessAfter}
                            </span>
                          )}
                        </div>

                        {recessEnabled && pCount > 1 && (
                          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                            <span style={{ fontSize: '0.72rem', color: 'var(--color-text-tertiary)' }}>Recess after period:</span>
                            <select
                              value={recessAfter}
                              onChange={(e) => handleRecessPeriodChange(day, parseInt(e.target.value, 10) || 1)}
                              className="setup-select"
                              style={{ width: '90px', padding: '0.25rem 0.4rem', fontSize: '0.75rem' }}
                            >
                              {Array.from({ length: Math.max(1, pCount - 1) }, (_, i) => i + 1).map((num) => (
                                <option key={num} value={num}>
                                  Period {num}
                                </option>
                              ))}
                            </select>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Card 2: Master Bell Timings */}
          <div className="rules-card">
            <div className="rules-card__header">
              <div>
                <h2 className="rules-card__title">
                  <Coffee size={18} className="text-brand" /> School Start Times & Period Durations
                </h2>
                <p className="rules-card__subtitle">
                  Define master bell timing parameters. The engine auto-computes accurate clock times for the schedule view.
                </p>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '1rem' }}>
              <div className="setup-field">
                <label>Monday–Friday School Start</label>
                <input
                  type="time"
                  value={model.bellSchedule.schoolStartTime || '07:10'}
                  onChange={(e) => {
                    setModel({
                      ...model,
                      bellSchedule: {
                        ...model.bellSchedule,
                        schoolStartTime: e.target.value,
                      },
                    });
                    setIsDirty(true);
                  }}
                  className="setup-input"
                  style={{ width: '130px', fontWeight: 600 }}
                />
                <span style={{ fontSize: '0.72rem', color: 'var(--color-text-tertiary)' }}>
                  Opening bell time (SIES default 07:10 AM).
                </span>
              </div>

              <div className="setup-field">
                <label>Saturday School Start</label>
                <input
                  type="time"
                  value={model.bellSchedule.saturdayStartTime || '07:30'}
                  onChange={(e) => {
                    setModel({
                      ...model,
                      bellSchedule: {
                        ...model.bellSchedule,
                        saturdayStartTime: e.target.value,
                      },
                    });
                    setIsDirty(true);
                  }}
                  className="setup-input"
                  style={{ width: '130px', fontWeight: 600 }}
                />
                <span style={{ fontSize: '0.72rem', color: 'var(--color-text-tertiary)' }}>
                  Saturday opening time (SIES default 07:30 AM).
                </span>
              </div>

              <div className="setup-field">
                <label>Standard Period Duration</label>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <input
                    type="number"
                    min={20}
                    max={90}
                    value={model.bellSchedule.defaultPeriodDuration || 35}
                    onChange={(e) => {
                      const dur = parseInt(e.target.value, 10) || 35;
                      setModel({
                        ...model,
                        bellSchedule: {
                          ...model.bellSchedule,
                          defaultPeriodDuration: dur,
                        },
                      });
                      setIsDirty(true);
                    }}
                    className="setup-input"
                    style={{ width: '80px', fontWeight: 700 }}
                  />
                  <span style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary)' }}>minutes</span>
                </div>
                <span style={{ fontSize: '0.72rem', color: 'var(--color-text-tertiary)' }}>
                  Base instructional length for all standard lectures (35 mins).
                </span>
              </div>

              <div className="setup-field">
                <label>Recess Duration</label>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <input
                    type="number"
                    min={10}
                    max={60}
                    value={model.bellSchedule.recessDuration || 20}
                    onChange={(e) => {
                      const dur = parseInt(e.target.value, 10) || 20;
                      setModel({
                        ...model,
                        bellSchedule: {
                          ...model.bellSchedule,
                          recessDuration: dur,
                        },
                      });
                      setIsDirty(true);
                    }}
                    className="setup-input"
                    style={{ width: '80px', fontWeight: 700 }}
                  />
                  <span style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary)' }}>minutes</span>
                </div>
                <span style={{ fontSize: '0.72rem', color: 'var(--color-text-tertiary)' }}>
                  Break duration between lecture blocks (20 mins).
                </span>
              </div>
            </div>

            {/* Interactive Timeline Preview */}
            <div style={{ marginTop: '0.75rem' }}>
              <span style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--color-text-secondary)', display: 'block', marginBottom: '0.5rem' }}>
                Timeline Preview (Monday Schedule):
              </span>
              <div className="timeline-strip">
                {timelinePreview.map((slot, i) => (
                  <div
                    key={i}
                    className={`timeline-slot ${slot.type === 'recess' ? 'is-recess' : ''}`}
                  >
                    <span className="timeline-slot__num">{slot.label}</span>
                    <span className="timeline-slot__time">{slot.time}</span>
                    <span className="timeline-slot__dur">{slot.dur}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Advanced Overrides Disclosure */}
            <details className="rules-disclosure">
              <summary>
                <Info size={15} /> Advanced Overrides (Assembly & Homeroom Duration)
              </summary>
              <div className="rules-disclosure__content">
                <div className="setup-field">
                  <label>Period 1 Assembly / Homeroom Duration Override</label>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <input
                      type="number"
                      min={15}
                      max={90}
                      value={
                        model.bellSchedule.periodDurationOverrides?.['1'] ??
                        (model.bellSchedule.defaultPeriodDuration === 35 ? 40 : model.bellSchedule.defaultPeriodDuration || 35)
                      }
                      onChange={(e) => {
                        const dur = parseInt(e.target.value, 10) || 40;
                        const nextOverrides = { ...(model.bellSchedule.periodDurationOverrides || {}) };
                        nextOverrides['1'] = dur;
                        setModel({
                          ...model,
                          bellSchedule: {
                            ...model.bellSchedule,
                            periodDurationOverrides: nextOverrides,
                          },
                        });
                        setIsDirty(true);
                      }}
                      className="setup-input"
                      style={{ width: '80px', fontWeight: 700 }}
                    />
                    <span style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary)' }}>minutes (Period 1)</span>
                  </div>
                  <span style={{ fontSize: '0.72rem', color: 'var(--color-text-tertiary)' }}>
                    Morning assembly / prayer / roll call typically requires 40 minutes at SIES.
                  </span>
                </div>
              </div>
            </details>
          </div>
        </div>
      )}

      {/* =========================================================================
          TAB 2: HARD CONSTRAINTS (H1–H10)
          ========================================================================= */}
      {activeTab === 'hard' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          <div className="rules-card">
            <div className="rules-card__header">
              <div>
                <h2 className="rules-card__title">
                  <Shield size={18} className="text-jade" /> Hard Operational Constraints (H1–H10)
                </h2>
                <p className="rules-card__subtitle">
                  Hard constraints must be strictly satisfied for a valid schedule. Mandatory rules (H3, H4, H9) are mathematically locked to guarantee conflict-free timetables.
                </p>
              </div>
              <Badge variant={activeHardCount === 10 ? 'jade' : 'gold'} size="md">
                {activeHardCount} of 10 Enabled
              </Badge>
            </div>

            <div className="rules-constraints-grid">
              {['H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'H7', 'H8', 'H9', 'H10'].map((id) => {
                const c = currentConstraints.hardConstraints[id];
                if (!c) return null;
                const isMandatory = Boolean(c.mandatory);
                const isEnabled = isMandatory ? true : Boolean(c.enabled);

                return (
                  <div
                    key={id}
                    className={`rule-card ${isMandatory ? 'is-mandatory' : ''} ${!isEnabled ? 'is-disabled' : ''}`}
                  >
                    <div>
                      <div className="rule-card__top">
                        <div className="rule-card__title-row">
                          <span className="rule-card__id-badge">{c.id}</span>
                          <h4 className="rule-card__name">{c.name}</h4>
                        </div>

                        {isMandatory ? (
                          <span className="rule-card__locked-tag" title="Mandatory rule required for schedule integrity">
                            <Lock size={11} /> Mandatory
                          </span>
                        ) : (
                          <label className="sies-toggle-label" title={isEnabled ? 'Click to disable' : 'Click to enable'}>
                            <input
                              type="checkbox"
                              checked={isEnabled}
                              onChange={() => handleToggleHardConstraint(id)}
                            />
                            <span className="sies-toggle-track" />
                          </label>
                        )}
                      </div>

                      <p className="rule-card__desc">{c.description}</p>
                    </div>

                    {/* Numerical Parameters for Configurable Constraints */}
                    {id === 'H2' && isEnabled && (
                      <div className="rule-card__param-box">
                        <div className="rule-card__param-row">
                          <span>Max Continuous Lectures:</span>
                          <input
                            type="number"
                            min={1}
                            max={4}
                            value={c.maxContinuousLectures ?? 2}
                            onChange={(e) =>
                              handleHardParamChange('H2', 'maxContinuousLectures', parseInt(e.target.value, 10) || 2)
                            }
                            className="rule-card__param-input"
                          />
                        </div>
                        <span style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)' }}>
                          Standard workload: max <strong>{c.maxContinuousLectures ?? 2}</strong> back-to-back lectures.
                        </span>
                      </div>
                    )}

                    {id === 'H5' && isEnabled && (
                      <div className="rule-card__param-box">
                        <div className="rule-card__param-row">
                          <span>Max Simultaneous Lab Classes:</span>
                          <input
                            type="number"
                            min={1}
                            max={5}
                            value={c.maxConcurrentLabClasses ?? 1}
                            onChange={(e) =>
                              handleHardParamChange('H5', 'maxConcurrentLabClasses', parseInt(e.target.value, 10) || 1)
                            }
                            className="rule-card__param-input"
                          />
                        </div>
                        <span style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)' }}>
                          Enforces dedicated single computer lab exclusivity across Standards 5 to 10.
                        </span>
                      </div>
                    )}

                    {id === 'H6' && isEnabled && (
                      <div className="rule-card__param-box">
                        <div className="rule-card__param-row">
                          <span>Prohibited PE Period:</span>
                          <select
                            value={c.prohibitedPeriod ?? 1}
                            onChange={(e) =>
                              handleHardParamChange('H6', 'prohibitedPeriod', parseInt(e.target.value, 10) || 1)
                            }
                            className="setup-select"
                            style={{ width: '100px', padding: '0.3rem 0.5rem', fontSize: '0.78rem' }}
                          >
                            <option value={1}>Period 1</option>
                            <option value={2}>Period 2</option>
                            <option value={3}>Period 3</option>
                          </select>
                        </div>
                        <span style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)' }}>
                          Physical education sessions will not be scheduled in Period {c.prohibitedPeriod ?? 1}.
                        </span>
                      </div>
                    )}

                    {id === 'H8' && isEnabled && (
                      <div className="rule-card__param-box">
                        <div className="rule-card__param-row">
                          <span>Max Activity Periods / Day:</span>
                          <input
                            type="number"
                            min={1}
                            max={4}
                            value={c.maxActivityPeriodsPerDay ?? 2}
                            onChange={(e) =>
                              handleHardParamChange('H8', 'maxActivityPeriodsPerDay', parseInt(e.target.value, 10) || 2)
                            }
                            className="rule-card__param-input"
                          />
                        </div>
                        <span style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)' }}>
                          Caps non-academic subjects (Drawing, Craft, PE, Library) per division per day.
                        </span>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* =========================================================================
          TAB 3: SOFT CONSTRAINTS (S1–S6)
          ========================================================================= */}
      {activeTab === 'soft' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          <div className="rules-card">
            <div className="rules-card__header">
              <div>
                <h2 className="rules-card__title">
                  <SlidersHorizontal size={18} className="text-brand" /> Soft Optimization Objectives (S1–S6)
                </h2>
                <p className="rules-card__subtitle">
                  Preferences optimized by the Google OR-Tools CP-SAT solver. Higher weight values give the objective greater priority during schedule optimization.
                </p>
              </div>
              <Badge variant={activeSoftCount > 0 ? 'jade' : 'neutral'} size="md">
                {activeSoftCount} of 6 Active
              </Badge>
            </div>

            <div className="rules-constraints-grid">
              {['S1', 'S2', 'S3', 'S4', 'S5', 'S6'].map((id) => {
                const c = currentConstraints.softConstraints[id];
                if (!c) return null;
                const isEnabled = Boolean(c.enabled && c.weight > 0);

                return (
                  <div
                    key={id}
                    className={`rule-card ${!isEnabled ? 'is-disabled' : ''}`}
                  >
                    <div>
                      <div className="rule-card__top">
                        <div className="rule-card__title-row">
                          <span className="rule-card__id-badge is-soft">{c.id}</span>
                          <h4 className="rule-card__name">{c.name}</h4>
                        </div>

                        <label className="sies-toggle-label" title={isEnabled ? 'Disable objective' : 'Enable objective'}>
                          <input
                            type="checkbox"
                            checked={isEnabled}
                            onChange={() => handleToggleSoftConstraint(id)}
                          />
                          <span className="sies-toggle-track" />
                        </label>
                      </div>

                      <p className="rule-card__desc">{c.description}</p>
                    </div>

                    <div className="rule-card__param-box">
                      <div className="rule-card__param-row">
                        <span>Solver Priority Weight:</span>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                          <input
                            type="number"
                            min={0}
                            max={500}
                            step={5}
                            value={c.weight}
                            onChange={(e) =>
                              handleSoftWeightChange(id, parseInt(e.target.value, 10) || 0)
                            }
                            className="rule-card__param-input"
                          />
                          <span className="rule-card__weight-pill">
                            {c.weight === 0 ? 'Off' : c.weight >= 100 ? 'High' : c.weight >= 50 ? 'Med' : 'Low'}
                          </span>
                        </div>
                      </div>

                      <input
                        type="range"
                        min={0}
                        max={300}
                        step={5}
                        value={c.weight}
                        onChange={(e) =>
                          handleSoftWeightChange(id, parseInt(e.target.value, 10) || 0)
                        }
                        style={{ width: '100%', accentColor: 'var(--color-brand)' }}
                      />

                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.72rem', color: 'var(--color-text-secondary)' }}>
                        <span>SIES Default: {c.defaultWeight}</span>
                        <span>{isEnabled ? 'Active in Optimization' : 'Disabled'}</span>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* Persistent Bottom Action Bar */}
      <div className="rules-footer-bar">
        <div className="rules-footer-bar__status">
          <span>
            Rules status: <strong>{activeHardCount} hard rules active</strong> • <strong>{activeSoftCount} soft objectives enabled</strong>
          </span>
          {isDirty && (
            <Badge variant="gold" size="sm" icon={<AlertTriangle size={12} />}>
              Unsaved Changes
            </Badge>
          )}
        </div>

        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <Button variant="secondary" size="sm" onClick={handleResetConstraints} disabled={isSaving}>
            <RotateCcw size={14} /> Reset
          </Button>

          <Button variant="primary" size="sm" onClick={handleSaveModel} isLoading={isSaving}>
            <Save size={14} /> Save Rules
          </Button>

          {onNavigateToGenerate && (
            <Button variant="secondary" size="sm" onClick={onNavigateToGenerate} rightIcon={<ArrowRight size={14} />}>
              Proceed to Generate
            </Button>
          )}
        </div>
      </div>
    </div>
  );
};

export default TimetableRulesView;
