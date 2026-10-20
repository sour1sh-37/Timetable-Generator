import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  SlidersHorizontal,
  Save,
  RotateCcw,
  CheckCircle2,
  AlertTriangle,
  AlertOctagon,
  ArrowRight,
  Plus,
  Trash2,
  Layers,
  BookOpen,
  Users,
  Grid,
  Clock,
  Check,
  Calendar,
  Coffee,
  Edit3,
  X,
  Sliders,
  Lock,
  Shield,
} from 'lucide-react';
import { Button, Badge, LoadingSpinner, Modal } from '../components/common';
import type { TimetableApiClient } from '../api/client';
import type {
  SetupModelDto,
  SetupValidationDto,
  SubjectSetupItem,
  ProfessorSetupItem,
  ConstraintsSetupDto,
} from '../api/types';
import './setup.css';

export interface SchoolSetupViewProps {
  client: TimetableApiClient;
  onNavigateToStudio?: () => void;
  onNavigateToRules?: (subTab?: 'bell' | 'hard' | 'soft') => void;
  onNavigateToGenerate?: () => void;
  activeTab?: SetupTab;
  onTabChange?: (tab: SetupTab) => void;
}

export type SetupTab = 'structure' | 'subjects' | 'faculty' | 'allocations' | 'bell' | 'constraints' | 'review';

export const DEFAULT_CONSTRAINTS: ConstraintsSetupDto = {
  hardConstraints: {
    H1: {
      id: 'H1',
      name: 'Daily Teacher Workload Limit',
      description: 'Caps faculty to at most 7 teaching periods in any single working day to prevent teacher burnout.',
      mandatory: false,
      enabled: true,
    },
    H2: {
      id: 'H2',
      name: 'Maximum Consecutive Lectures',
      description: 'Limits continuous back-to-back lectures without a break (default 2 for standard workload; heavy workloads allow up to 3).',
      mandatory: false,
      enabled: true,
      maxContinuousLectures: 2,
    },
    H3: {
      id: 'H3',
      name: 'Class Slot Capacity & Structural Integrity',
      description: 'Ensures exactly one instructional activity per class in every active timetable slot. Mandatory structural invariant.',
      mandatory: true,
      enabled: true,
    },
    H4: {
      id: 'H4',
      name: 'No Teacher Double-Booking / Room Conflicts',
      description: 'Prevents any faculty member or specialized room from being scheduled in two classes simultaneously. Mandatory operational invariant.',
      mandatory: true,
      enabled: true,
    },
    H5: {
      id: 'H5',
      name: 'Computer Lab Facility Exclusivity',
      description: 'Restricts simultaneous computer lab sessions across the entire school to prevent hardware room conflicts.',
      mandatory: false,
      enabled: true,
      maxConcurrentLabClasses: 1,
    },
    H6: {
      id: 'H6',
      name: 'Physical Education Prohibited Period',
      description: 'Prohibits Physical Education / Sports during specified early assembly or study periods (Period 1 by default).',
      mandatory: false,
      enabled: true,
      prohibitedPeriod: 1,
    },
    H7: {
      id: 'H7',
      name: 'Class Teacher Morning Lecture Placement',
      description: 'Prefers scheduling the designated class teacher during the opening period (Period 1) or morning block of each day.',
      mandatory: false,
      enabled: true,
    },
    H8: {
      id: 'H8',
      name: 'Maximum Activity / Co-Curricular Periods',
      description: 'Caps total non-academic activity periods (Drawing, Craft, PE, Library) to at most 2 per class per day.',
      mandatory: false,
      enabled: true,
      maxActivityPeriodsPerDay: 2,
    },
    H9: {
      id: 'H9',
      name: 'Exact Curriculum Allotment Fulfillment',
      description: 'Guarantees that every subject receives exactly its weekly required lecture quota across all divisions. Mandatory syllabus invariant.',
      mandatory: true,
      enabled: true,
    },
    H10: {
      id: 'H10',
      name: 'Saturday Half-Day Schedule Integrity',
      description: 'Enforces Saturday half-day instructional caps and recess omissions matching the configured bell schedule.',
      mandatory: false,
      enabled: true,
    },
  },
  softConstraints: {
    S1: {
      id: 'S1',
      name: 'Workload Uniformity Across Week',
      description: 'Evenly balances weekly teaching periods across days so faculty do not face extreme peaks and valleys.',
      enabled: true,
      weight: 100,
      defaultWeight: 100,
    },
    S2: {
      id: 'S2',
      name: 'Minimize Faculty Idle Gaps',
      description: 'Minimizes unpaid or unproductive idle waiting gaps between morning and afternoon lectures for faculty.',
      enabled: true,
      weight: 80,
      defaultWeight: 80,
    },
    S3: {
      id: 'S3',
      name: 'Teacher Period Preference Alignment',
      description: 'Encourages assigning faculty members to their preferred instructional periods when specified.',
      enabled: true,
      weight: 50,
      defaultWeight: 50,
    },
    S4: {
      id: 'S4',
      name: 'Optimal Lab Session Placement',
      description: 'Avoids scheduling lab practicals during the final period of the day or immediately prior to dismissal.',
      enabled: true,
      weight: 40,
      defaultWeight: 40,
    },
    S5: {
      id: 'S5',
      name: 'Balanced Distribution of Core Subjects',
      description: 'Evenly distributes heavy subjects (Mathematics, Science) throughout the week rather than clustered consecutively.',
      enabled: true,
      weight: 30,
      defaultWeight: 30,
    },
    S6: {
      id: 'S6',
      name: 'Minimize Single-Period Work Days',
      description: 'Penalizes scheduling faculty to report to school for only one isolated period on any given day.',
      enabled: true,
      weight: 20,
      defaultWeight: 20,
    },
  },
};

export const SchoolSetupView: React.FC<SchoolSetupViewProps> = ({
  client,
  onNavigateToStudio,
  onNavigateToRules,
  onNavigateToGenerate,
  activeTab: controlledTab,
  onTabChange,
}) => {
  const [internalTab, setInternalTab] = useState<SetupTab>('structure');
  const activeTab = controlledTab ?? internalTab;

  const handleSelectTab = (tab: SetupTab) => {
    if (onTabChange) {
      onTabChange(tab);
    }
    setInternalTab(tab);
  };
  const [model, setModel] = useState<SetupModelDto | null>(null);
  const [validation, setValidation] = useState<SetupValidationDto | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [isDirty, setIsDirty] = useState<boolean>(false);
  const [feedbackMsg, setFeedbackMsg] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

  // Form States for New Items
  const [newSubjCode, setNewSubjCode] = useState('');
  const [newSubjName, setNewSubjName] = useState('');
  const [newSubjCategory, setNewSubjCategory] = useState<'CORE_ACADEMIC' | 'ACTIVITY' | 'SPECIAL_CURRICULAR'>('CORE_ACADEMIC');
  const [newSubjIsLab, setNewSubjIsLab] = useState(false);

  const [newProfCode, setNewProfCode] = useState('');
  const [newProfName, setNewProfName] = useState('');
  const [newProfCtOf, setNewProfCtOf] = useState<string>('');
  const [newProfFree, setNewProfFree] = useState(false);

  const [allocClassFilter, setAllocClassFilter] = useState<string>('');
  const [newAllocSubject, setNewAllocSubject] = useState<string>('');
  const [newAllocTeacher, setNewAllocTeacher] = useState<string>('');
  const [newAllocLectures, setNewAllocLectures] = useState<number>(4);

  // Editing States for Subjects, Faculty & Allocations
  const [editingSubject, setEditingSubject] = useState<{
    originalCode: string;
    code: string;
    name: string;
    category: 'CORE_ACADEMIC' | 'ACTIVITY' | 'SPECIAL_CURRICULAR';
    isLab: boolean;
  } | null>(null);
  const [editSubjectError, setEditSubjectError] = useState<string | null>(null);

  const [editingProfessor, setEditingProfessor] = useState<{
    originalCode: string;
    code: string;
    name: string;
    freeTeacher: boolean;
    classTeacherOf: string;
  } | null>(null);
  const [editProfessorError, setEditProfessorError] = useState<string | null>(null);

  const [editingAllocation, setEditingAllocation] = useState<{
    originalTeacherCode: string;
    originalSubjectCode: string;
    originalWeekly: number;
    classId: string;
    teacherCode: string;
    subjectCode: string;
    weekly: number;
  } | null>(null);
  const [editAllocationError, setEditAllocationError] = useState<string | null>(null);

  // Timing Manager & Bell Schedule Preview States
  const [previewDay, setPreviewDay] = useState<'MONDAY' | 'SATURDAY'>('MONDAY');
  const [overrideModalPeriod, setOverrideModalPeriod] = useState<number | null>(null);
  const [overrideStartTime, setOverrideStartTime] = useState<string>('');
  const [overrideEndTime, setOverrideEndTime] = useState<string>('');
  const [overrideDuration, setOverrideDuration] = useState<number>(35);

  // Load Model from Server
  const loadSetupModel = useCallback(async () => {
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
      setValidation(res.validation);
      setIsDirty(false);
      if (res.model.classes.length > 0 && !allocClassFilter) {
        setAllocClassFilter(res.model.classes[0]);
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setFeedbackMsg({ text: 'Failed to load school configuration: ' + msg, type: 'error' });
    } finally {
      setIsLoading(false);
    }
  }, [client, allocClassFilter]);

  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect
    loadSetupModel();
  }, [loadSetupModel]);

  // Save changes to backend
  const handleSaveModel = async () => {
    if (!model) return;
    const activeDays = model.bellSchedule.days;
    if (activeDays.length === 0) {
      setFeedbackMsg({
        text: 'At least one working day must be configured in the timetable structure.',
        type: 'error',
      });
      return;
    }

    for (const d of activeDays) {
      const p = model.bellSchedule.dailyPeriods[d] ?? 0;
      if (p < 1 || p > 12) {
        setFeedbackMsg({
          text: `Invalid period count for ${d} (${p}): Active working days must have between 1 and 12 periods.`,
          type: 'error',
        });
        return;
      }
    }

    const available = activeDays.reduce((sum, d) => sum + (model.bellSchedule.dailyPeriods[d] || 0), 0);
    const required = model.bellSchedule.weeklyRequiredPeriodsPerClass;
    if (required < 1 || required > 60) {
      setFeedbackMsg({
        text: `Invalid Weekly Required Periods (${required}): Must be between 1 and 60 periods/class.`,
        type: 'error',
      });
      return;
    }

    if (available < required) {
      const deficit = required - available;
      setFeedbackMsg({
        text: `Infeasible Timetable Capacity: Total available periods (${available}) is less than required class periods (${required}). Deficit: ${deficit} periods. Increase daily periods or decrease required class load.`,
        type: 'error',
      });
      return;
    }

    for (const d of activeDays) {
      const isEnabled = model.bellSchedule.dailyRecessEnabled
        ? model.bellSchedule.dailyRecessEnabled[d] !== false
        : (d !== 'SATURDAY');
      if (isEnabled) {
        const dPeriods = model.bellSchedule.dailyPeriods[d] || 0;
        const rawRec = model.bellSchedule.dailyRecessAfterPeriod?.[d];
        const dRec = (rawRec && rawRec > 0) ? rawRec : (model.bellSchedule.recessAfterPeriod ?? 4);
        if (dPeriods <= 1) {
          setFeedbackMsg({
            text: `Invalid Recess Placement for ${d}: Day has only ${dPeriods} period, so recess cannot be scheduled. Please disable recess for ${d}.`,
            type: 'error',
          });
          return;
        }
        if (dRec < 1 || dRec >= dPeriods) {
          setFeedbackMsg({
            text: `Invalid Recess Placement for ${d} (after Period ${dRec}): Recess on ${d} must be placed between Period 1 and ${dPeriods - 1}. Alternatively, disable recess for ${d}.`,
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
      setValidation(res.validation);
      setIsDirty(false);
      setFeedbackMsg({ text: 'Timetable structure & capacity configuration successfully saved and persisted!', type: 'success' });
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setFeedbackMsg({ text: 'Error saving configuration: ' + msg, type: 'error' });
    } finally {
      setIsSaving(false);
    }
  };

  // Reset to default SIES preset
  const handleResetModel = async () => {
    if (!window.confirm('Reset school structure to the official SIES APJ High School preset (Standards 5 to 10)?')) {
      return;
    }
    setIsSaving(true);
    setFeedbackMsg(null);
    try {
      const res = await client.resetSetupModel();
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
      setValidation(res.validation);
      setIsDirty(false);
      if (res.model.classes.length > 0) {
        setAllocClassFilter(res.model.classes[0]);
      }
      setFeedbackMsg({ text: 'Reset to default SIES APJ School preset.', type: 'success' });
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setFeedbackMsg({ text: 'Error resetting preset: ' + msg, type: 'error' });
    } finally {
      setIsSaving(false);
    }
  };

  // -----------------------------------------------------------------------
  // Constraint Configuration State & Handlers
  // -----------------------------------------------------------------------
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

  const handleToggleHardConstraint = (id: string) => {
    if (!model) return;
    const target = currentConstraints.hardConstraints[id];
    if (!target || target.mandatory) return; // Immutable / Locked invariant
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

  // Helper to recompute generated classes locally for instant preview
  const recomputeClassesLocally = (start: number, end: number, divMap: Record<string, number>): string[] => {
    const list: string[] = [];
    for (let s = start; s <= end; s++) {
      const count = divMap[String(s)] || 2;
      for (let i = 0; i < count; i++) {
        const div = String.fromCharCode('A'.charCodeAt(0) + i);
        list.push(`${s}-${div}`);
      }
    }
    return list;
  };

  // 1. Structure Handlers
  const handleStandardRangeChange = (start: number, end: number) => {
    if (!model || start > end || start < 1 || end > 12) return;
    const newDivs = { ...model.standardDivisions };
    for (let s = start; s <= end; s++) {
      if (!newDivs[String(s)]) newDivs[String(s)] = 2;
    }
    // Clean up outside
    Object.keys(newDivs).forEach((key) => {
      const s = parseInt(key, 10);
      if (s < start || s > end) delete newDivs[key];
    });
    const newClasses = recomputeClassesLocally(start, end, newDivs);
    setModel({
      ...model,
      startStandard: start,
      endStandard: end,
      standardDivisions: newDivs,
      classes: newClasses,
    });
    setIsDirty(true);
  };

  const handleDivisionCountChange = (standard: number, delta: number) => {
    if (!model) return;
    const current = model.standardDivisions[String(standard)] || 2;
    const next = Math.max(1, Math.min(10, current + delta));
    if (next === current) return;
    const newDivs = { ...model.standardDivisions, [String(standard)]: next };
    const newClasses = recomputeClassesLocally(model.startStandard, model.endStandard, newDivs);
    setModel({
      ...model,
      standardDivisions: newDivs,
      classes: newClasses,
    });
    setIsDirty(true);
  };

  const applyPresetStructure = (start: number, end: number, defaultDivs: number) => {
    if (!model) return;
    const newDivs: Record<string, number> = {};
    for (let s = start; s <= end; s++) {
      newDivs[String(s)] = defaultDivs;
    }
    const newClasses = recomputeClassesLocally(start, end, newDivs);
    setModel({
      ...model,
      startStandard: start,
      endStandard: end,
      standardDivisions: newDivs,
      classes: newClasses,
    });
    setIsDirty(true);
  };

  // 2. Subjects Handlers
  const handleAddSubject = (e: React.FormEvent) => {
    e.preventDefault();
    if (!model || !newSubjCode.trim() || !newSubjName.trim()) return;
    const code = newSubjCode.trim().toUpperCase();
    if (model.subjects.some((s) => s.code === code)) {
      alert(`Subject with code "${code}" already exists.`);
      return;
    }
    const newSubj: SubjectSetupItem = {
      code,
      name: newSubjName.trim(),
      category: newSubjCategory,
      isLab: newSubjIsLab,
      weeklyPeriods: 4,
    };
    setModel({
      ...model,
      subjects: [...model.subjects, newSubj],
    });
    setNewSubjCode('');
    setNewSubjName('');
    setIsDirty(true);
  };

  const handleDeleteSubject = (code: string) => {
    if (!model) return;
    if (!window.confirm(`Delete subject ${code}? Allocations using this subject will also be removed.`)) return;
    const updatedSubjects = model.subjects.filter((s) => s.code !== code);
    const updatedProfessors = model.professors.map((p) => ({
      ...p,
      allocations: p.allocations.filter((a) => a.subjectCode !== code),
    }));
    setModel({
      ...model,
      subjects: updatedSubjects,
      professors: updatedProfessors,
    });
    setIsDirty(true);
  };

  const handleOpenEditSubject = (subj: SubjectSetupItem) => {
    setEditingSubject({
      originalCode: subj.code,
      code: subj.code,
      name: subj.name,
      category: subj.category as any,
      isLab: subj.isLab,
    });
    setEditSubjectError(null);
  };

  const handleSaveEditSubject = (e: React.FormEvent) => {
    e.preventDefault();
    if (!model || !editingSubject) return;

    const newCode = editingSubject.code.trim().toUpperCase();
    const newName = editingSubject.name.trim();

    if (!newCode || !newName) {
      setEditSubjectError('Subject code and display name are required.');
      return;
    }

    if (newCode !== editingSubject.originalCode && model.subjects.some((s) => s.code === newCode)) {
      setEditSubjectError(`A subject with code "${newCode}" already exists.`);
      return;
    }

    const updatedSubjects = model.subjects.map((s) =>
      s.code === editingSubject.originalCode
        ? {
            ...s,
            code: newCode,
            name: newName,
            category: editingSubject.category,
            isLab: editingSubject.isLab,
          }
        : s
    );

    // Cascade to professor allocations if code or name changed
    const updatedProfessors = model.professors.map((p) => ({
      ...p,
      allocations: p.allocations.map((a) =>
        a.subjectCode === editingSubject.originalCode
          ? {
              ...a,
              subjectCode: newCode,
              subjectName: newName,
            }
          : a
      ),
    }));

    setModel({
      ...model,
      subjects: updatedSubjects,
      professors: updatedProfessors,
    });
    setIsDirty(true);
    setEditingSubject(null);
    setFeedbackMsg({
      text: `Subject "${newCode}" (${newName}) successfully updated. Click "Save Configuration" to persist changes.`,
      type: 'success',
    });
  };

  // 3. Faculty Handlers
  const handleAddProfessor = (e: React.FormEvent) => {
    e.preventDefault();
    if (!model || !newProfCode.trim() || !newProfName.trim()) return;
    const code = newProfCode.trim().toUpperCase();
    if (model.professors.some((p) => p.code === code)) {
      alert(`Faculty member with code "${code}" already exists.`);
      return;
    }
    const newProf: ProfessorSetupItem = {
      code,
      name: newProfName.trim(),
      freeTeacher: newProfFree,
      classTeacherOf: newProfCtOf || null,
      totalWorkload: 0,
      allocations: [],
    };
    setModel({
      ...model,
      professors: [...model.professors, newProf],
    });
    setNewProfCode('');
    setNewProfName('');
    setNewProfCtOf('');
    setNewProfFree(false);
    setIsDirty(true);
  };

  const handleDeleteProfessor = (code: string) => {
    if (!model) return;
    if (!window.confirm(`Delete faculty ${code}?`)) return;
    setModel({
      ...model,
      professors: model.professors.filter((p) => p.code !== code),
    });
    setIsDirty(true);
  };

  const handleOpenEditProfessor = (prof: ProfessorSetupItem) => {
    setEditingProfessor({
      originalCode: prof.code,
      code: prof.code,
      name: prof.name,
      freeTeacher: prof.freeTeacher,
      classTeacherOf: prof.classTeacherOf || '',
    });
    setEditProfessorError(null);
  };

  const handleSaveEditProfessor = (e: React.FormEvent) => {
    e.preventDefault();
    if (!model || !editingProfessor) return;

    const newCode = editingProfessor.code.trim().toUpperCase();
    const newName = editingProfessor.name.trim();

    if (!newCode || !newName) {
      setEditProfessorError('Teacher code and full name are required.');
      return;
    }

    if (newCode !== editingProfessor.originalCode && model.professors.some((p) => p.code === newCode)) {
      setEditProfessorError(`A faculty member with code "${newCode}" already exists.`);
      return;
    }

    const targetClass = editingProfessor.classTeacherOf || null;

    const updatedProfessors = model.professors.map((p) => {
      if (p.code === editingProfessor.originalCode) {
        return {
          ...p,
          code: newCode,
          name: newName,
          freeTeacher: editingProfessor.freeTeacher,
          classTeacherOf: targetClass,
        };
      }
      if (targetClass && p.classTeacherOf === targetClass) {
        return { ...p, classTeacherOf: null };
      }
      return p;
    });

    setModel({
      ...model,
      professors: updatedProfessors,
    });
    setIsDirty(true);
    setEditingProfessor(null);
    setFeedbackMsg({
      text: `Faculty member "${newCode}" (${newName}) successfully updated. Click "Save Configuration" to persist.`,
      type: 'success',
    });
  };

  const handleUpdateClassTeacher = (teacherCode: string, targetClass: string | null) => {
    if (!model) return;
    const updated = model.professors.map((p) => {
      if (p.code === teacherCode) {
        return { ...p, classTeacherOf: targetClass };
      }
      if (targetClass && p.classTeacherOf === targetClass) {
        return { ...p, classTeacherOf: null };
      }
      return p;
    });
    setModel({ ...model, professors: updated });
    setIsDirty(true);
  };

  // 4. Allocations Handlers
  const activeClassAllocations = useMemo(() => {
    if (!model || !allocClassFilter) return [];
    const list: { teacherCode: string; teacherName: string; subjectCode: string; subjectName: string; weekly: number }[] = [];
    model.professors.forEach((p) => {
      p.allocations.forEach((a) => {
        if (a.classes.includes(allocClassFilter)) {
          list.push({
            teacherCode: p.code,
            teacherName: p.name,
            subjectCode: a.subjectCode,
            subjectName: a.subjectName || a.subjectCode,
            weekly: a.weeklyLectures,
          });
        }
      });
    });
    return list;
  }, [model, allocClassFilter]);

  const activeClassTotalAllocated = useMemo(() => {
    return activeClassAllocations.reduce((sum, item) => sum + item.weekly, 0);
  }, [activeClassAllocations]);

  const handleAddAllocation = (e: React.FormEvent) => {
    e.preventDefault();
    if (!model || !allocClassFilter || !newAllocSubject || !newAllocTeacher) return;

    const prof = model.professors.find((p) => p.code === newAllocTeacher);
    if (!prof) return;

    const subj = model.subjects.find((s) => s.code === newAllocSubject);
    const subjName = subj ? subj.name : newAllocSubject;

    const updatedProfessors = model.professors.map((p) => {
      if (p.code !== newAllocTeacher) return p;
      const existingAlloc = p.allocations.find((a) => a.subjectCode === newAllocSubject && a.weeklyLectures === newAllocLectures);
      let newAllocs = [...p.allocations];
      if (existingAlloc) {
        if (!existingAlloc.classes.includes(allocClassFilter)) {
          newAllocs = newAllocs.map((a) =>
            a === existingAlloc ? { ...a, classes: [...a.classes, allocClassFilter] } : a
          );
        }
      } else {
        newAllocs.push({
          subjectCode: newAllocSubject,
          subjectName: subjName,
          weeklyLectures: newAllocLectures,
          classes: [allocClassFilter],
        });
      }
      return {
        ...p,
        allocations: newAllocs,
        totalWorkload: newAllocs.reduce((sum, a) => sum + a.classes.length * a.weeklyLectures, 0),
      };
    });

    setModel({ ...model, professors: updatedProfessors });
    setIsDirty(true);
  };

  const handleRemoveAllocation = (teacherCode: string, subjectCode: string) => {
    if (!model || !allocClassFilter) return;
    const updated = model.professors.map((p) => {
      if (p.code !== teacherCode) return p;
      const newAllocs = p.allocations
        .map((a) => {
          if (a.subjectCode === subjectCode) {
            return {
              ...a,
              classes: a.classes.filter((c) => c !== allocClassFilter),
            };
          }
          return a;
        })
        .filter((a) => a.classes.length > 0);
      return {
        ...p,
        allocations: newAllocs,
        totalWorkload: newAllocs.reduce((sum, a) => sum + a.classes.length * a.weeklyLectures, 0),
      };
    });
    setModel({ ...model, professors: updated });
    setIsDirty(true);
  };

  const handleOpenEditAllocation = (item: {
    teacherCode: string;
    teacherName: string;
    subjectCode: string;
    subjectName: string;
    weekly: number;
  }) => {
    setEditingAllocation({
      originalTeacherCode: item.teacherCode,
      originalSubjectCode: item.subjectCode,
      originalWeekly: item.weekly,
      classId: allocClassFilter,
      teacherCode: item.teacherCode,
      subjectCode: item.subjectCode,
      weekly: item.weekly,
    });
    setEditAllocationError(null);
  };

  const handleSaveEditAllocation = (e: React.FormEvent) => {
    e.preventDefault();
    if (!model || !editingAllocation) return;

    const { originalTeacherCode, originalSubjectCode, classId, teacherCode, subjectCode, weekly } = editingAllocation;

    if (!teacherCode || !subjectCode) {
      setEditAllocationError('Please select both a subject and an assigned instructor.');
      return;
    }

    if (weekly < 1 || weekly > 12) {
      setEditAllocationError('Weekly periods must be between 1 and 12.');
      return;
    }

    // Check if changing to a subject/teacher combination that already exists in this class
    if (
      (teacherCode !== originalTeacherCode || subjectCode !== originalSubjectCode) &&
      activeClassAllocations.some((a) => a.teacherCode === teacherCode && a.subjectCode === subjectCode)
    ) {
      setEditAllocationError(`An allocation for subject "${subjectCode}" and teacher "${teacherCode}" already exists for class ${classId}.`);
      return;
    }

    const subj = model.subjects.find((s) => s.code === subjectCode);
    const subjName = subj ? subj.name : subjectCode;

    // 1. Remove classId from original allocation
    let updatedProfessors = model.professors.map((p) => {
      if (p.code !== originalTeacherCode) return p;
      const newAllocs = p.allocations
        .map((a) => {
          if (a.subjectCode === originalSubjectCode) {
            return {
              ...a,
              classes: a.classes.filter((c) => c !== classId),
            };
          }
          return a;
        })
        .filter((a) => a.classes.length > 0);

      return {
        ...p,
        allocations: newAllocs,
        totalWorkload: newAllocs.reduce((sum, a) => sum + a.classes.length * a.weeklyLectures, 0),
      };
    });

    // 2. Add classId to target teacher and subject allocation
    updatedProfessors = updatedProfessors.map((p) => {
      if (p.code !== teacherCode) return p;

      const existingAlloc = p.allocations.find((a) => a.subjectCode === subjectCode && a.weeklyLectures === weekly);
      let newAllocs = [...p.allocations];
      if (existingAlloc) {
        if (!existingAlloc.classes.includes(classId)) {
          newAllocs = newAllocs.map((a) =>
            a === existingAlloc ? { ...a, classes: [...a.classes, classId] } : a
          );
        }
      } else {
        newAllocs.push({
          subjectCode,
          subjectName: subjName,
          weeklyLectures: weekly,
          classes: [classId],
        });
      }

      return {
        ...p,
        allocations: newAllocs,
        totalWorkload: newAllocs.reduce((sum, a) => sum + a.classes.length * a.weeklyLectures, 0),
      };
    });

    setModel({
      ...model,
      professors: updatedProfessors,
    });
    setIsDirty(true);
    setEditingAllocation(null);
    setFeedbackMsg({
      text: `Allocation for class ${classId} (${subjectCode} • ${teacherCode}) updated. Click "Save Configuration" to persist.`,
      type: 'success',
    });
  };

  // 5. Bell Schedule Handlers
  const capacitySummary = useMemo(() => {
    if (!model) return null;
    const allDays = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];
    const workingDays = model.bellSchedule.days;
    const dailyPeriods = model.bellSchedule.dailyPeriods || {};

    let available = 0;
    const breakdownParts: number[] = [];
    for (const d of workingDays) {
      const p = dailyPeriods[d] ?? 8;
      available += p;
      breakdownParts.push(p);
    }

    const required = model.bellSchedule.weeklyRequiredPeriodsPerClass || 48;
    const spare = available - required;
    const isValid = available >= required;
    const deficit = required - available;
    const breakdown = breakdownParts.join(' + ');

    const positivePeriods = workingDays.map((d) => dailyPeriods[d] ?? 8).filter((p) => p > 0);
    const minPeriod = positivePeriods.length > 0 ? Math.min(...positivePeriods) : 8;
    const maxPeriod = positivePeriods.length > 0 ? Math.max(...positivePeriods) : 8;

    return {
      allDays,
      workingDays,
      dailyPeriods,
      available,
      required,
      spare,
      deficit,
      isValid,
      breakdown,
      minPeriod,
      maxPeriod,
    };
  }, [model]);

  const handleToggleDay = (day: string) => {
    if (!model) return;
    const currentDays = model.bellSchedule.days;
    let nextDays: string[];
    const nextDaily = { ...model.bellSchedule.dailyPeriods };

    if (currentDays.includes(day)) {
      if (currentDays.length <= 1) {
        alert('Timetable requires at least one working day.');
        return;
      }
      nextDays = currentDays.filter((d) => d !== day);
      nextDaily[day] = 0;
    } else {
      nextDays = [...currentDays, day];
      if (!nextDaily[day] || nextDaily[day] === 0) {
        nextDaily[day] = day === 'SATURDAY' ? 4 : 8;
      }
    }

    const positivePeriods = nextDays.map((d) => nextDaily[d] || 8);
    const maxPeriod = positivePeriods.length > 0 ? Math.max(...positivePeriods) : 8;

    setModel({
      ...model,
      bellSchedule: {
        ...model.bellSchedule,
        days: nextDays,
        dailyPeriods: nextDaily,
        periodsPerDay: maxPeriod,
        saturdayEnabled: nextDays.includes('SATURDAY'),
      },
    });
    setIsDirty(true);
  };

  const handleDailyPeriodChange = (day: string, count: number) => {
    if (!model) return;
    const clampedCount = Math.max(0, Math.min(12, count));
    const nextDaily = {
      ...model.bellSchedule.dailyPeriods,
      [day]: clampedCount,
    };
    const activeDays = model.bellSchedule.days;
    const positivePeriods = activeDays
      .map((d) => (d === day ? clampedCount : (nextDaily[d] ?? 8)))
      .filter((p) => p > 0);
    const maxPeriod = positivePeriods.length > 0 ? Math.max(...positivePeriods) : 8;
    const minPeriod = positivePeriods.length > 0 ? Math.min(...positivePeriods) : 8;

    let currentRec = model.bellSchedule.recessAfterPeriod;
    if (currentRec >= minPeriod && minPeriod > 1) {
      currentRec = minPeriod - 1;
    }

    const nextRecessAfter = { ...(model.bellSchedule.dailyRecessAfterPeriod || {}) };
    const curDayRec = nextRecessAfter[day] || currentRec;
    if (clampedCount > 1 && curDayRec >= clampedCount) {
      nextRecessAfter[day] = clampedCount - 1;
    } else if (clampedCount <= 1) {
      nextRecessAfter[day] = 1;
    }

    const nextRecessEnabled = { ...(model.bellSchedule.dailyRecessEnabled || {}) };
    if (clampedCount <= 1) {
      nextRecessEnabled[day] = false;
    }

    setModel({
      ...model,
      bellSchedule: {
        ...model.bellSchedule,
        dailyPeriods: nextDaily,
        dailyRecessAfterPeriod: nextRecessAfter,
        dailyRecessEnabled: nextRecessEnabled,
        periodsPerDay: maxPeriod,
        recessAfterPeriod: currentRec,
      },
    });
    setIsDirty(true);
  };

  const applyStructurePreset = (preset: 'all8' | 'sat4' | 'all7' | 'fiveDay') => {
    if (!model) return;
    const allDays = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];
    let nextDays: string[];
    const nextDaily: Record<string, number> = {};
    const nextRecessEnabled: Record<string, boolean> = { ...(model.bellSchedule.dailyRecessEnabled || {}) };
    let req: number;
    let rec = 4;

    switch (preset) {
      case 'all8':
        nextDays = [...allDays];
        allDays.forEach((d) => {
          nextDaily[d] = 8;
          nextRecessEnabled[d] = true;
        });
        req = 48;
        rec = 4;
        break;
      case 'sat4':
        nextDays = [...allDays];
        allDays.forEach((d) => {
          nextDaily[d] = d === 'SATURDAY' ? 4 : 8;
          nextRecessEnabled[d] = d !== 'SATURDAY'; // Saturday recess disabled
        });
        req = 44;
        rec = 4;
        break;
      case 'all7':
        nextDays = [...allDays];
        allDays.forEach((d) => {
          nextDaily[d] = 7;
          nextRecessEnabled[d] = true;
        });
        req = 42;
        rec = 3;
        break;
      case 'fiveDay':
        nextDays = allDays.filter((d) => d !== 'SATURDAY');
        allDays.forEach((d) => {
          nextDaily[d] = d === 'SATURDAY' ? 0 : 8;
          nextRecessEnabled[d] = true;
        });
        req = 40;
        rec = 4;
        break;
    }

    const maxPeriod = Math.max(...nextDays.map((d) => nextDaily[d] || 8));
    setModel({
      ...model,
      bellSchedule: {
        ...model.bellSchedule,
        days: nextDays,
        dailyPeriods: nextDaily,
        dailyRecessEnabled: nextRecessEnabled,
        periodsPerDay: maxPeriod,
        weeklyRequiredPeriodsPerClass: req,
        recessAfterPeriod: rec,
        saturdayEnabled: nextDays.includes('SATURDAY'),
      },
    });
    setIsDirty(true);
  };

  const formatMinutesToTime = (totalMinutes: number): string => {
    const h = Math.floor(totalMinutes / 60) % 24;
    const m = totalMinutes % 60;
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
  };

  const parseTimeToMinutes = (timeStr: string): number => {
    try {
      const parts = (timeStr || '').trim().split(':');
      if (parts.length >= 2) {
        return parseInt(parts[0], 10) * 60 + parseInt(parts[1], 10);
      }
    } catch {}
    return 7 * 60 + 10;
  };

  const getTimelineSlotsForDay = useCallback((day: string) => {
    if (!model) return [];
    const bell = model.bellSchedule;
    const isSat = (day === 'SATURDAY');
    const startStr = (isSat && bell.saturdayStartTime) ? bell.saturdayStartTime : (bell.schoolStartTime || '07:10');
    let currentMin = parseTimeToMinutes(startStr);
    const periodsCount = bell.dailyPeriods[day] ?? (isSat ? 4 : 8);
    const recessOn = bell.dailyRecessEnabled
      ? (bell.dailyRecessEnabled[day] !== false)
      : (day !== 'SATURDAY');
    const recPeriod = bell.dailyRecessAfterPeriod?.[day] ?? bell.recessAfterPeriod ?? 4;
    const recDur = bell.dailyRecessDurations?.[day] ?? bell.recessDuration ?? 20;
    const defaultDur = bell.defaultPeriodDuration || 35;
    const passTime = bell.passingTime || 0;
    const shortBreakOn = Boolean(bell.shortBreakEnabled);
    const shortBreakPeriod = bell.shortBreakAfterPeriod || 2;
    const shortBreakDur = bell.shortBreakDuration || 10;

    const slots: Array<{
      type: 'period' | 'short_break' | 'recess';
      periodNumber?: number;
      label: string;
      startTime: string;
      endTime: string;
      durationMinutes: number;
      isOverridden: boolean;
    }> = [];

    for (let p = 1; p <= periodsCount; p++) {
      const pKey = String(p);
      const overrideTime = bell.periodTimeOverrides?.[pKey];
      let pStart = '';
      let pEnd = '';
      const pDur = bell.periodDurationOverrides?.[pKey] ?? (p === 1 && defaultDur === 35 ? 40 : defaultDur);
      let isOverridden = false;

      if (overrideTime && overrideTime.includes('-')) {
        const parts = overrideTime.split('-');
        pStart = parts[0].trim();
        pEnd = parts[1].trim();
        currentMin = parseTimeToMinutes(pEnd);
        isOverridden = true;
      } else {
        pStart = formatMinutesToTime(currentMin);
        currentMin += pDur;
        pEnd = formatMinutesToTime(currentMin);
      }

      slots.push({
        type: 'period',
        periodNumber: p,
        label: `Period ${p}`,
        startTime: pStart,
        endTime: pEnd,
        durationMinutes: pDur,
        isOverridden,
      });

      // Short break
      if (shortBreakOn && p === shortBreakPeriod && p < periodsCount) {
        const sbStart = formatMinutesToTime(currentMin);
        currentMin += shortBreakDur;
        const sbEnd = formatMinutesToTime(currentMin);
        slots.push({
          type: 'short_break',
          label: 'Short Break / Mid-Morning Pause',
          startTime: sbStart,
          endTime: sbEnd,
          durationMinutes: shortBreakDur,
          isOverridden: false,
        });
      }

      // Recess
      if (recessOn && p === recPeriod && p < periodsCount) {
        const rStart = formatMinutesToTime(currentMin);
        currentMin += recDur;
        const rEnd = formatMinutesToTime(currentMin);
        slots.push({
          type: 'recess',
          label: 'Recess Break',
          startTime: rStart,
          endTime: rEnd,
          durationMinutes: recDur,
          isOverridden: false,
        });
      } else if (passTime > 0 && p < periodsCount) {
        currentMin += passTime;
      }
    }

    return slots;
  }, [model]);

  const handleOpenOverride = (p: number, currentStart: string, currentEnd: string, currentDur: number) => {
    setOverrideModalPeriod(p);
    setOverrideStartTime(currentStart);
    setOverrideEndTime(currentEnd);
    setOverrideDuration(currentDur);
  };

  const handleApplyOverride = () => {
    if (!model || overrideModalPeriod === null) return;
    const pKey = String(overrideModalPeriod);
    const nextOverrides = { ...(model.bellSchedule.periodTimeOverrides || {}) };
    if (overrideStartTime && overrideEndTime) {
      nextOverrides[pKey] = `${overrideStartTime.trim()} - ${overrideEndTime.trim()}`;
    }
    const nextDurOverrides = { ...(model.bellSchedule.periodDurationOverrides || {}) };
    if (overrideDuration > 0) {
      nextDurOverrides[pKey] = overrideDuration;
    }
    setModel({
      ...model,
      bellSchedule: {
        ...model.bellSchedule,
        periodTimeOverrides: nextOverrides,
        periodDurationOverrides: nextDurOverrides,
      },
    });
    setIsDirty(true);
    setOverrideModalPeriod(null);
  };

  const handleResetOverride = (p: number) => {
    if (!model) return;
    const pKey = String(p);
    const nextOverrides = { ...(model.bellSchedule.periodTimeOverrides || {}) };
    delete nextOverrides[pKey];
    const nextDurOverrides = { ...(model.bellSchedule.periodDurationOverrides || {}) };
    if (p !== 1) {
      delete nextDurOverrides[pKey];
    }
    setModel({
      ...model,
      bellSchedule: {
        ...model.bellSchedule,
        periodTimeOverrides: nextOverrides,
        periodDurationOverrides: nextDurOverrides,
      },
    });
    setIsDirty(true);
  };

  const hardList = useMemo(() => Object.values(currentConstraints.hardConstraints), [currentConstraints]);
  const softList = useMemo(() => Object.values(currentConstraints.softConstraints), [currentConstraints]);
  const activeHardCount = useMemo(() => hardList.filter((c) => c.enabled).length, [hardList]);
  const mandatoryCount = useMemo(() => hardList.filter((c) => c.mandatory).length, [hardList]);
  const activeSoftCount = useMemo(() => softList.filter((c) => c.enabled && c.weight > 0).length, [softList]);
  const totalSoftWeight = useMemo(() => softList.reduce((acc, c) => acc + (c.enabled ? c.weight : 0), 0), [softList]);

  if (isLoading || !model) {
    return (
      <div className="setup-container" style={{ padding: '4rem 0', alignItems: 'center', textAlign: 'center' }}>
        <LoadingSpinner size="lg" />
        <p style={{ marginTop: '1rem', color: 'var(--color-ivory-dim)', fontSize: 'var(--text-sm)' }}>
          Loading active school configuration from backend...
        </p>
      </div>
    );
  }

  return (
    <div className="setup-container">
      {/* Top Header & Global Actions */}
      <div className="setup-header-banner">
        <div className="setup-header__title-group">
          <div className="setup-header__icon">
            <SlidersHorizontal size={24} />
          </div>
          <div className="setup-header__titles">
            <h1>School Setup & Input Configuration</h1>
            <p>
              Authoritative parameters for standards, divisions, subjects, faculty roster, and bell schedule.
            </p>
          </div>
        </div>

        <div className="setup-header__actions">
          {isDirty && (
            <span className="setup-unsaved-badge">
              <AlertTriangle size={14} /> Unsaved Changes
            </span>
          )}

          <Button
            variant="secondary"
            size="sm"
            onClick={handleResetModel}
            disabled={isSaving}
          >
            <RotateCcw size={14} /> Reset Preset
          </Button>

          <Button
            variant="primary"
            size="sm"
            onClick={handleSaveModel}
            isLoading={isSaving}
          >
            <Save size={14} /> Save Configuration
          </Button>

          <Button
            variant="secondary"
            size="sm"
            onClick={onNavigateToStudio}
            rightIcon={<ArrowRight size={14} />}
          >
            Timetable Studio
          </Button>
        </div>
      </div>

      {feedbackMsg && (
        <div
          className={`setup-validation-box ${feedbackMsg.type === 'success' ? 'is-valid' : 'is-invalid'}`}
          style={{ animation: 'fadeIn 0.2s ease' }}
        >
          <div className="setup-validation-header">
            {feedbackMsg.type === 'success' ? <CheckCircle2 size={18} /> : <AlertOctagon size={18} />}
            <span>{feedbackMsg.text}</span>
          </div>
        </div>
      )}

      {/* Navigation Tabs */}
      <div className="setup-nav-tabs" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
          <button
            className={`setup-nav-tab ${activeTab === 'structure' ? 'is-active' : ''}`}
            onClick={() => handleSelectTab('structure')}
          >
            <span className="setup-nav-tab__num">1</span>
            <Layers size={14} />
            <span>Classes & Divisions</span>
          </button>

          <button
            className={`setup-nav-tab ${activeTab === 'subjects' ? 'is-active' : ''}`}
            onClick={() => handleSelectTab('subjects')}
          >
            <span className="setup-nav-tab__num">2</span>
            <BookOpen size={14} />
            <span>Curricular Subjects ({model.subjects.length})</span>
          </button>

          <button
            className={`setup-nav-tab ${activeTab === 'faculty' ? 'is-active' : ''}`}
            onClick={() => handleSelectTab('faculty')}
          >
            <span className="setup-nav-tab__num">3</span>
            <Users size={14} />
            <span>Faculty & Teachers ({model.professors.length})</span>
          </button>

          <button
            className={`setup-nav-tab ${activeTab === 'allocations' ? 'is-active' : ''}`}
            onClick={() => handleSelectTab('allocations')}
          >
            <span className="setup-nav-tab__num">4</span>
            <Grid size={14} />
            <span>Weekly Allocations</span>
          </button>
        </div>

        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          {onNavigateToRules && (
            <Button
              variant="secondary"
              size="sm"
              leftIcon={<Sliders size={13} />}
              onClick={() => onNavigateToRules('bell')}
            >
              Timetable Rules
            </Button>
          )}

          {onNavigateToGenerate && (
            <Button
              variant="secondary"
              size="sm"
              leftIcon={<CheckCircle2 size={13} />}
              onClick={onNavigateToGenerate}
            >
              Generate Timetable
            </Button>
          )}
        </div>
      </div>

      {/* Tab 1: Academic Structure */}
      {activeTab === 'structure' && (
        <div className="setup-step-panel">
          <div className="setup-card">
            <div className="setup-card__header">
              <div>
                <h2 className="setup-card__title">
                  <Layers size={18} className="text-terracotta" /> Standards & Grade Boundaries
                </h2>
                <p className="setup-card__subtitle">
                  Configure the grade range and the number of divisions per standard. Classes are dynamically computed.
                </p>
              </div>

              <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                <Button variant="secondary" size="sm" onClick={() => applyPresetStructure(5, 10, 4)}>
                  SIES APJ (5–10)
                </Button>
                <Button variant="secondary" size="sm" onClick={() => applyPresetStructure(1, 5, 3)}>
                  Primary (1–5)
                </Button>
                <Button variant="secondary" size="sm" onClick={() => applyPresetStructure(6, 8, 4)}>
                  Middle (6–8)
                </Button>
                <Button variant="secondary" size="sm" onClick={() => applyPresetStructure(9, 10, 4)}>
                  Secondary (9–10)
                </Button>
              </div>
            </div>

            <div className="setup-form-grid" style={{ marginBottom: '1.5rem' }}>
              <div className="setup-field">
                <label>Starting Standard (Grade)</label>
                <input
                  type="number"
                  min={1}
                  max={model.endStandard}
                  value={model.startStandard}
                  onChange={(e) => handleStandardRangeChange(parseInt(e.target.value, 10) || 1, model.endStandard)}
                  className="setup-input"
                />
              </div>

              <div className="setup-field">
                <label>Ending Standard (Grade)</label>
                <input
                  type="number"
                  min={model.startStandard}
                  max={12}
                  value={model.endStandard}
                  onChange={(e) => handleStandardRangeChange(model.startStandard, parseInt(e.target.value, 10) || 10)}
                  className="setup-input"
                />
              </div>

              <div className="setup-field">
                <label>Total Generated Classes</label>
                <div style={{ padding: '0.55rem 0', fontWeight: 700, color: 'var(--color-gold)', fontSize: '1.1rem' }}>
                  {model.classes.length} Classes
                </div>
              </div>
            </div>

            <h3 style={{ fontSize: 'var(--text-sm)', color: 'var(--color-ivory)', marginBottom: '0.5rem' }}>
              Divisions per Standard (Customized per Grade)
            </h3>
            <div className="setup-divisions-grid">
              {Array.from({ length: model.endStandard - model.startStandard + 1 }, (_, i) => {
                const std = model.startStandard + i;
                const count = model.standardDivisions[String(std)] || 2;
                const divNames = Array.from({ length: count }, (_, dIdx) => String.fromCharCode(65 + dIdx)).join(', ');
                return (
                  <div key={std} className="setup-division-card">
                    <div className="setup-division-card__title">
                      <span>Standard {std}</span>
                      <span style={{ fontSize: '0.7rem', color: 'var(--color-ivory-dim)' }}>Divs: {divNames}</span>
                    </div>
                    <div className="setup-counter-control">
                      <button
                        className="setup-counter-btn"
                        onClick={() => handleDivisionCountChange(std, -1)}
                        disabled={count <= 1}
                      >
                        -
                      </button>
                      <span className="setup-counter-value">{count} Divisions</span>
                      <button
                        className="setup-counter-btn"
                        onClick={() => handleDivisionCountChange(std, 1)}
                        disabled={count >= 8}
                      >
                        +
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>

            <h3 style={{ fontSize: 'var(--text-sm)', color: 'var(--color-ivory)', marginTop: '1.5rem', marginBottom: '0.5rem' }}>
              Live Class Roster Preview ({model.classes.length} Classes)
            </h3>
            <div className="setup-roster-preview">
              {model.classes.map((cls) => (
                <span key={cls} className="setup-class-pill">
                  {cls}
                </span>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Tab 2: Subjects & Categories */}
      {activeTab === 'subjects' && (
        <div className="setup-step-panel">
          <div className="setup-card">
            <div className="setup-card__header">
              <div>
                <h2 className="setup-card__title">
                  <BookOpen size={18} className="text-terracotta" /> Academic Curriculum & Subjects
                </h2>
                <p className="setup-card__subtitle">
                  Configure subjects, categorizations, and facility requirements (such as computer lab exclusivity).
                </p>
              </div>
            </div>

            {/* Add Subject Form */}
            <form onSubmit={handleAddSubject} style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', alignItems: 'flex-end', marginBottom: '1.5rem', padding: '1rem', backgroundColor: 'var(--surface-ground)', borderRadius: 'var(--radius-md)' }}>
              <div className="setup-field" style={{ minWidth: '120px' }}>
                <label>Code (e.g. ENG)</label>
                <input
                  type="text"
                  required
                  value={newSubjCode}
                  onChange={(e) => setNewSubjCode(e.target.value)}
                  placeholder="MATH"
                  className="setup-input"
                />
              </div>

              <div className="setup-field" style={{ minWidth: '180px', flex: 1 }}>
                <label>Display Name</label>
                <input
                  type="text"
                  required
                  value={newSubjName}
                  onChange={(e) => setNewSubjName(e.target.value)}
                  placeholder="Mathematics"
                  className="setup-input"
                />
              </div>

              <div className="setup-field" style={{ minWidth: '160px' }}>
                <label>Category</label>
                <select
                  value={newSubjCategory}
                  onChange={(e) => setNewSubjCategory(e.target.value as any)}
                  className="setup-select"
                >
                  <option value="CORE_ACADEMIC">Core Academic</option>
                  <option value="ACTIVITY">Activity / Co-curricular</option>
                  <option value="SPECIAL_CURRICULAR">Special Curricular</option>
                </select>
              </div>

              <div className="setup-field" style={{ justifyContent: 'center' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={newSubjIsLab}
                    onChange={(e) => setNewSubjIsLab(e.target.checked)}
                  />
                  <span>Requires Lab Facility</span>
                </label>
              </div>

              <Button variant="primary" size="sm" type="submit">
                <Plus size={14} /> Add Subject
              </Button>
            </form>

            {/* Subjects Table */}
            <div className="setup-table-container">
              <table className="setup-table">
                <thead>
                  <tr>
                    <th>Subject Code</th>
                    <th>Full Name</th>
                    <th>Category</th>
                    <th>Facility Requirement</th>
                    <th style={{ textAlign: 'right' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {model.subjects.map((subj) => (
                    <tr key={subj.code}>
                      <td>
                        <strong style={{ color: 'var(--color-ivory)' }}>{subj.code}</strong>
                      </td>
                      <td>{subj.name}</td>
                      <td>
                        <Badge
                          variant={
                            subj.category === 'CORE_ACADEMIC'
                              ? 'terracotta'
                              : subj.category === 'ACTIVITY'
                              ? 'jade'
                              : 'gold'
                          }
                          size="sm"
                        >
                          {subj.category.replace('_', ' ')}
                        </Badge>
                      </td>
                      <td>
                        {subj.isLab ? (
                          <span style={{ color: 'var(--color-gold)' }}>Exclusive Lab Resource</span>
                        ) : (
                          <span style={{ color: 'var(--color-ivory-subtle)' }}>Standard Classroom</span>
                        )}
                      </td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <button
                          type="button"
                          className="setup-action-btn"
                          onClick={() => handleOpenEditSubject(subj)}
                          title="Edit Subject"
                          style={{ marginRight: '0.4rem' }}
                        >
                          <Edit3 size={13} />
                          <span>Edit</span>
                        </button>
                        <button
                          type="button"
                          className="setup-action-btn setup-action-btn--delete"
                          onClick={() => handleDeleteSubject(subj.code)}
                          title="Delete Subject"
                        >
                          <Trash2 size={13} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* Tab 3: Faculty & Class Teachers */}
      {activeTab === 'faculty' && (
        <div className="setup-step-panel">
          <div className="setup-card">
            <div className="setup-card__header">
              <div>
                <h2 className="setup-card__title">
                  <Users size={18} className="text-terracotta" /> Faculty Roster & Class Teachers
                </h2>
                <p className="setup-card__subtitle">
                  Manage teachers, designate Class Teachers (enforces Period 1 allocation), and monitor workload.
                </p>
              </div>
            </div>

            {/* Add Faculty Form */}
            <form onSubmit={handleAddProfessor} style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', alignItems: 'flex-end', marginBottom: '1.5rem', padding: '1rem', backgroundColor: 'var(--surface-ground)', borderRadius: 'var(--radius-md)' }}>
              <div className="setup-field" style={{ minWidth: '120px' }}>
                <label>Teacher Code (e.g. T01)</label>
                <input
                  type="text"
                  required
                  value={newProfCode}
                  onChange={(e) => setNewProfCode(e.target.value)}
                  placeholder="T45"
                  className="setup-input"
                />
              </div>

              <div className="setup-field" style={{ minWidth: '180px', flex: 1 }}>
                <label>Full Name</label>
                <input
                  type="text"
                  required
                  value={newProfName}
                  onChange={(e) => setNewProfName(e.target.value)}
                  placeholder="Teacher Name"
                  className="setup-input"
                />
              </div>

              <div className="setup-field" style={{ minWidth: '140px' }}>
                <label>Class Teacher Of</label>
                <select
                  value={newProfCtOf}
                  onChange={(e) => setNewProfCtOf(e.target.value)}
                  className="setup-select"
                >
                  <option value="">None</option>
                  {model.classes.map((c) => (
                    <option key={c} value={c}>{c}</option>
                  ))}
                </select>
              </div>

              <div className="setup-field" style={{ justifyContent: 'center' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={newProfFree}
                    onChange={(e) => setNewProfFree(e.target.checked)}
                  />
                  <span>Free / Substitute</span>
                </label>
              </div>

              <Button variant="primary" size="sm" type="submit">
                <Plus size={14} /> Add Faculty
              </Button>
            </form>

            {/* Faculty Table */}
            <div className="setup-table-container">
              <table className="setup-table">
                <thead>
                  <tr>
                    <th>Code</th>
                    <th>Faculty Name</th>
                    <th>Designated Class Teacher</th>
                    <th>Current Workload</th>
                    <th>Continuous Limit</th>
                    <th style={{ textAlign: 'right' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {model.professors.map((prof) => (
                    <tr key={prof.code}>
                      <td>
                        <strong style={{ color: 'var(--color-gold)' }}>{prof.code}</strong>
                      </td>
                      <td>{prof.name}</td>
                      <td>
                        <select
                          value={prof.classTeacherOf || ''}
                          onChange={(e) => handleUpdateClassTeacher(prof.code, e.target.value || null)}
                          className="setup-select"
                          style={{ padding: '0.3rem 0.5rem', fontSize: '0.75rem' }}
                        >
                          <option value="">None</option>
                          {model.classes.map((c) => (
                            <option key={c} value={c}>{c}</option>
                          ))}
                        </select>
                      </td>
                      <td>
                        <span style={{ fontWeight: 600, color: prof.totalWorkload > 30 ? 'var(--color-terracotta)' : 'var(--color-jade)' }}>
                          {prof.totalWorkload} periods/wk
                        </span>
                      </td>
                      <td>
                        <span style={{ color: 'var(--color-ivory-dim)' }}>
                          {prof.totalWorkload <= 30 ? '≤ 2 consecutive' : '≤ 3 consecutive'}
                        </span>
                      </td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <button
                          type="button"
                          className="setup-action-btn"
                          onClick={() => handleOpenEditProfessor(prof)}
                          title="Edit Faculty Member"
                          style={{ marginRight: '0.4rem' }}
                        >
                          <Edit3 size={13} />
                          <span>Edit</span>
                        </button>
                        <button
                          type="button"
                          className="setup-action-btn setup-action-btn--delete"
                          onClick={() => handleDeleteProfessor(prof.code)}
                          title="Delete Faculty"
                        >
                          <Trash2 size={13} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* Tab 4: Weekly Allocations */}
      {activeTab === 'allocations' && (
        <div className="setup-step-panel">
          <div className="setup-card">
            <div className="setup-card__header">
              <div>
                <h2 className="setup-card__title">
                  <Grid size={18} className="text-terracotta" /> Class-Subject Teaching Allocations
                </h2>
                <p className="setup-card__subtitle">
                  Define which teacher instructs each subject for a specific class division, and the weekly lecture count.
                </p>
              </div>

              {/* Class Selector */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-ivory-dim)' }}>Selected Class:</span>
                <select
                  value={allocClassFilter}
                  onChange={(e) => setAllocClassFilter(e.target.value)}
                  className="setup-select"
                  style={{ fontWeight: 600, color: 'var(--color-gold)' }}
                >
                  {model.classes.map((cls) => (
                    <option key={cls} value={cls}>{cls}</option>
                  ))}
                </select>
              </div>
            </div>

            {/* Class Capacity Progress Bar */}
            <div style={{ marginBottom: '1.25rem', padding: '0.85rem 1rem', backgroundColor: 'var(--surface-ground)', borderRadius: 'var(--radius-md)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <strong style={{ color: 'var(--color-ivory)' }}>Class {allocClassFilter} Allocated Periods: </strong>
                <span style={{ color: activeClassTotalAllocated === model.bellSchedule.weeklyRequiredPeriodsPerClass ? 'var(--color-jade-light)' : 'var(--color-gold)' }}>
                  {activeClassTotalAllocated} / {model.bellSchedule.weeklyRequiredPeriodsPerClass} required periods/week
                </span>
              </div>
              <Badge
                variant={activeClassTotalAllocated === model.bellSchedule.weeklyRequiredPeriodsPerClass ? 'jade' : 'gold'}
                size="sm"
              >
                {activeClassTotalAllocated === model.bellSchedule.weeklyRequiredPeriodsPerClass
                  ? 'Capacity Met (100%)'
                  : activeClassTotalAllocated < model.bellSchedule.weeklyRequiredPeriodsPerClass
                  ? `${model.bellSchedule.weeklyRequiredPeriodsPerClass - activeClassTotalAllocated} Spares Available`
                  : 'Over Allocated'}
              </Badge>
            </div>

            {/* Add Allocation Form */}
            <form onSubmit={handleAddAllocation} style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', alignItems: 'flex-end', marginBottom: '1.5rem', padding: '1rem', backgroundColor: 'var(--surface-ground)', borderRadius: 'var(--radius-md)' }}>
              <div className="setup-field" style={{ minWidth: '160px', flex: 1 }}>
                <label>Subject</label>
                <select
                  required
                  value={newAllocSubject}
                  onChange={(e) => setNewAllocSubject(e.target.value)}
                  className="setup-select"
                >
                  <option value="">Select Subject...</option>
                  {model.subjects.map((s) => (
                    <option key={s.code} value={s.code}>{s.code} — {s.name}</option>
                  ))}
                </select>
              </div>

              <div className="setup-field" style={{ minWidth: '180px', flex: 1 }}>
                <label>Assigned Faculty</label>
                <select
                  required
                  value={newAllocTeacher}
                  onChange={(e) => setNewAllocTeacher(e.target.value)}
                  className="setup-select"
                >
                  <option value="">Select Faculty...</option>
                  {model.professors.map((p) => (
                    <option key={p.code} value={p.code}>{p.code} — {p.name}</option>
                  ))}
                </select>
              </div>

              <div className="setup-field" style={{ width: '130px' }}>
                <label>Weekly Periods</label>
                <input
                  type="number"
                  min={1}
                  max={12}
                  required
                  value={newAllocLectures}
                  onChange={(e) => setNewAllocLectures(parseInt(e.target.value, 10) || 1)}
                  className="setup-input"
                />
              </div>

              <Button variant="primary" size="sm" type="submit">
                <Plus size={14} /> Assign to {allocClassFilter}
              </Button>
            </form>

            {/* Active Class Allocations Table */}
            <div className="setup-table-container">
              <table className="setup-table">
                <thead>
                  <tr>
                    <th>Subject Code</th>
                    <th>Subject Name</th>
                    <th>Assigned Faculty</th>
                    <th>Weekly Lecture Count</th>
                    <th style={{ textAlign: 'right' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {activeClassAllocations.length === 0 ? (
                    <tr>
                      <td colSpan={5} style={{ textAlign: 'center', padding: '2rem', color: 'var(--color-ivory-subtle)' }}>
                        No allocations currently configured for class {allocClassFilter}. Use the form above to assign subjects.
                      </td>
                    </tr>
                  ) : (
                    activeClassAllocations.map((item) => (
                      <tr key={`${item.subjectCode}-${item.teacherCode}`}>
                        <td><strong style={{ color: 'var(--color-ivory)' }}>{item.subjectCode}</strong></td>
                        <td>{item.subjectName}</td>
                        <td>
                          <span style={{ color: 'var(--color-gold)' }}>{item.teacherName} ({item.teacherCode})</span>
                        </td>
                        <td><strong>{item.weekly} periods / week</strong></td>
                        <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                          <button
                            type="button"
                            className="setup-action-btn"
                            onClick={() => handleOpenEditAllocation(item)}
                            title="Edit Allocation"
                            style={{ marginRight: '0.4rem' }}
                          >
                            <Edit3 size={13} />
                            <span>Edit</span>
                          </button>
                          <button
                            type="button"
                            className="setup-action-btn setup-action-btn--delete"
                            onClick={() => handleRemoveAllocation(item.teacherCode, item.subjectCode)}
                            title="Remove Allocation"
                          >
                            <Trash2 size={13} />
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* Tab 5: Bell Schedule, Smart Timing Manager & Day-Specific Recess */}
      {activeTab === 'bell' && (
        <div className="setup-step-panel">
          {/* Header Card */}
          <div className="setup-card" style={{ marginBottom: '1.25rem' }}>
            <div className="setup-card__header" style={{ marginBottom: 0, paddingBottom: 0, borderBottom: 'none' }}>
              <div>
                <h2 className="setup-card__title">
                  <Clock size={18} className="text-terracotta" /> Smart Timing Manager, Bell Schedule & Day-Specific Recess
                </h2>
                <p className="setup-card__subtitle">
                  Configure working week structure, dynamic auto-calculated bell timings, day-specific recess rules, and live preview.
                </p>
              </div>
            </div>
          </div>

          {/* Section 1: Working Week Structure */}
          <div className="timing-section-card">
            <div className="timing-section-header">
              <div>
                <h3 className="timing-section-title">
                  <Calendar size={16} className="text-brand" /> Section 1: Working Week Structure
                </h3>
                <p className="timing-section-subtitle">
                  Activate or deactivate teaching days. College schedules support 5-day (Mon–Fri) or 6-day (Mon–Sat) configurations.
                </p>
              </div>
              <Badge variant="neutral" size="md">
                {capacitySummary?.workingDays.length} Working Days Active
              </Badge>
            </div>

            <div>
              <label style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--color-text-secondary)', display: 'block', marginBottom: '0.5rem' }}>
                Active Working Days:
              </label>
              <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                {capacitySummary?.allDays.map((day) => {
                  const isSelected = capacitySummary.workingDays.includes(day);
                  return (
                    <button
                      key={day}
                      type="button"
                      onClick={() => handleToggleDay(day)}
                      style={{
                        padding: '0.45rem 0.9rem',
                        borderRadius: 'var(--radius-md)',
                        border: isSelected ? '1px solid var(--color-brand)' : '1px solid var(--surface-border-strong)',
                        backgroundColor: isSelected ? 'var(--color-brand-subtle)' : 'var(--bg-surface)',
                        color: isSelected ? 'var(--color-brand)' : 'var(--color-text-secondary)',
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '0.4rem',
                        fontSize: '0.8rem',
                        fontWeight: isSelected ? 600 : 500,
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
          </div>

          {/* Section 2: Daily Periods & Master Timing Engine */}
          <div className="timing-section-card">
            <div className="timing-section-header">
              <div>
                <h3 className="timing-section-title">
                  <SlidersHorizontal size={16} className="text-brand" /> Section 2: Daily Period Counts & Master Bell Timings
                </h3>
                <p className="timing-section-subtitle">
                  Set daily teaching period capacities individually and define master school bell timing parameters.
                </p>
              </div>
            </div>

            <div className="structure-two-column-grid" style={{ marginBottom: 0 }}>
              {/* Left Column: Daily Period Counts & Presets */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                <strong style={{ fontSize: '0.8rem', color: 'var(--color-text-primary)' }}>
                  Individual Daily Period Allocations:
                </strong>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                  {capacitySummary?.allDays.map((day) => {
                    const isActive = capacitySummary.workingDays.includes(day);
                    const pCount = model.bellSchedule.dailyPeriods[day] ?? (isActive ? 8 : 0);
                    const displayName = day.charAt(0) + day.slice(1).toLowerCase();

                    return (
                      <div key={day} className={`day-period-row ${!isActive ? 'is-inactive' : ''}`}>
                        <div className="day-period-row__label">
                          <span>{displayName}:</span>
                          <span style={{
                            fontSize: '0.65rem',
                            padding: '0.1rem 0.4rem',
                            borderRadius: 'var(--radius-xs)',
                            backgroundColor: isActive ? 'var(--color-brand-subtle)' : 'var(--surface-border-subtle)',
                            color: isActive ? 'var(--color-brand)' : 'var(--color-text-tertiary)',
                            fontWeight: 600,
                          }}>
                            {isActive ? 'Active' : 'Off'}
                          </span>
                        </div>
                        <div className="day-period-row__controls">
                          <input
                            type="number"
                            min={isActive ? 1 : 0}
                            max={12}
                            disabled={!isActive}
                            value={isActive ? pCount : 0}
                            onChange={(e) => {
                              const val = parseInt(e.target.value, 10);
                              handleDailyPeriodChange(day, isNaN(val) ? 0 : val);
                            }}
                            className="day-period-row__input"
                          />
                          <span className="day-period-row__suffix">periods / day</span>
                        </div>
                      </div>
                    );
                  })}
                </div>

                {/* Quick Presets */}
                <div>
                  <span style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--color-text-secondary)', display: 'block', marginBottom: '0.4rem' }}>
                    Quick Preset Configurations:
                  </span>
                  <div className="preset-buttons-row">
                    <button type="button" className="preset-pill-btn" onClick={() => applyStructurePreset('all8')}>
                      Set All 8 (48 Slots)
                    </button>
                    <button type="button" className="preset-pill-btn" onClick={() => applyStructurePreset('sat4')}>
                      Mon–Fri 8, Sat 4 (44 Slots, No Sat Recess)
                    </button>
                    <button type="button" className="preset-pill-btn" onClick={() => applyStructurePreset('all7')}>
                      All 7 (42 Slots)
                    </button>
                    <button type="button" className="preset-pill-btn" onClick={() => applyStructurePreset('fiveDay')}>
                      5-Day Week (40 Slots)
                    </button>
                  </div>
                </div>
              </div>

              {/* Right Column: Master Timing Engine Parameters */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', borderLeft: '1px solid var(--surface-border-subtle)', paddingLeft: '1.25rem' }}>
                <strong style={{ fontSize: '0.8rem', color: 'var(--color-text-primary)' }}>
                  Dynamic Bell Calculation Engine:
                </strong>

                <div className="setup-field">
                  <label>Weekday School Start Time (Mon–Fri)</label>
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
                  <span style={{ fontSize: '0.7rem', color: 'var(--color-text-tertiary)' }}>
                    First period start time for standard Monday through Friday schedule.
                  </span>
                </div>

                <div className="setup-field">
                  <label>Saturday School Start Time</label>
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
                  <span style={{ fontSize: '0.7rem', color: 'var(--color-text-tertiary)' }}>
                    Separate start time for Saturday (e.g., 07:30 AM).
                  </span>
                </div>

                <div className="setup-field">
                  <label>Default Period Duration</label>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <input
                      type="number"
                      min={15}
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
                      style={{ width: '85px', fontWeight: 600 }}
                    />
                    <span style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary)' }}>minutes per lecture</span>
                  </div>
                  <span style={{ fontSize: '0.7rem', color: 'var(--color-text-tertiary)' }}>
                    Base instructional length for all standard periods (SIES default is 35m).
                  </span>
                </div>

                <div className="setup-field">
                  <label>Period 1 Assembly / Homeroom Duration Override</label>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <input
                      type="number"
                      min={15}
                      max={90}
                      value={model.bellSchedule.periodDurationOverrides?.['1'] ?? (model.bellSchedule.defaultPeriodDuration === 35 ? 40 : (model.bellSchedule.defaultPeriodDuration || 35))}
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
                      style={{ width: '85px', fontWeight: 600 }}
                    />
                    <span style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary)' }}>minutes (Period 1)</span>
                  </div>
                  <span style={{ fontSize: '0.7rem', color: 'var(--color-text-tertiary)' }}>
                    Morning assembly / attendance period often requires 40 minutes at SIES.
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Section 3: Breaks and Smart Day-Specific Recess */}
          <div className="timing-section-card">
            <div className="timing-section-header">
              <div>
                <h3 className="timing-section-title">
                  <Coffee size={16} className="text-brand" /> Section 3: Breaks & Smart Day-Specific Recess
                </h3>
                <p className="timing-section-subtitle">
                  Configure passing time, optional short breaks, and independent recess placement & duration per working day.
                </p>
              </div>
            </div>

            {/* Passing Time and Short Break Grid */}
            <div className="timing-grid-row" style={{ paddingBottom: '1rem', borderBottom: '1px solid var(--surface-border-subtle)' }}>
              <div className="setup-field">
                <label>Passing Time Between Lectures</label>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <input
                    type="number"
                    min={0}
                    max={15}
                    value={model.bellSchedule.passingTime || 0}
                    onChange={(e) => {
                      const pt = parseInt(e.target.value, 10) || 0;
                      setModel({
                        ...model,
                        bellSchedule: {
                          ...model.bellSchedule,
                          passingTime: pt,
                        },
                      });
                      setIsDirty(true);
                    }}
                    className="setup-input"
                    style={{ width: '85px', fontWeight: 600 }}
                  />
                  <span style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary)' }}>minutes buffer</span>
                </div>
                <span style={{ fontSize: '0.7rem', color: 'var(--color-text-tertiary)' }}>
                  Transit interval between consecutive classes (0 for immediate transition).
                </span>
              </div>

              <div className="setup-field">
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={Boolean(model.bellSchedule.shortBreakEnabled)}
                    onChange={(e) => {
                      setModel({
                        ...model,
                        bellSchedule: {
                          ...model.bellSchedule,
                          shortBreakEnabled: e.target.checked,
                        },
                      });
                      setIsDirty(true);
                    }}
                  />
                  <span>Enable Short Break / Homeroom Pause</span>
                </label>
                {model.bellSchedule.shortBreakEnabled && (
                  <div style={{ display: 'flex', gap: '1rem', marginTop: '0.4rem' }}>
                    <div>
                      <span style={{ fontSize: '0.7rem', color: 'var(--color-text-secondary)' }}>After Period:</span>
                      <input
                        type="number"
                        min={1}
                        max={4}
                        value={model.bellSchedule.shortBreakAfterPeriod || 2}
                        onChange={(e) => {
                          const p = parseInt(e.target.value, 10) || 2;
                          setModel({
                            ...model,
                            bellSchedule: { ...model.bellSchedule, shortBreakAfterPeriod: p },
                          });
                          setIsDirty(true);
                        }}
                        className="setup-input"
                        style={{ width: '65px', marginLeft: '0.35rem' }}
                      />
                    </div>
                    <div>
                      <span style={{ fontSize: '0.7rem', color: 'var(--color-text-secondary)' }}>Duration (min):</span>
                      <input
                        type="number"
                        min={5}
                        max={30}
                        value={model.bellSchedule.shortBreakDuration || 10}
                        onChange={(e) => {
                          const d = parseInt(e.target.value, 10) || 10;
                          setModel({
                            ...model,
                            bellSchedule: { ...model.bellSchedule, shortBreakDuration: d },
                          });
                          setIsDirty(true);
                        }}
                        className="setup-input"
                        style={{ width: '65px', marginLeft: '0.35rem' }}
                      />
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Smart Day-Specific Recess Table */}
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                <strong style={{ fontSize: '0.8rem', color: 'var(--color-text-primary)' }}>
                  Day-Specific Recess Configuration:
                </strong>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-text-tertiary)' }}>
                  Recess is completely excluded from teaching period capacity.
                </span>
              </div>

              <div style={{ overflowX: 'auto' }}>
                <table className="recess-matrix-table">
                  <thead>
                    <tr>
                      <th style={{ width: '130px' }}>Day</th>
                      <th style={{ width: '150px' }}>Recess Status</th>
                      <th style={{ width: '170px' }}>Recess Placement</th>
                      <th style={{ width: '160px' }}>Duration (Minutes)</th>
                      <th>Solver & Schedule Behavior</th>
                    </tr>
                  </thead>
                  <tbody>
                    {capacitySummary?.workingDays.map((day) => {
                      const isRecessOn = model.bellSchedule.dailyRecessEnabled
                        ? (model.bellSchedule.dailyRecessEnabled[day] !== false)
                        : (day !== 'SATURDAY');
                      const dayPeriods = model.bellSchedule.dailyPeriods[day] ?? 8;
                      const rawRec = model.bellSchedule.dailyRecessAfterPeriod?.[day];
                      let recPos = (rawRec && rawRec > 0) ? rawRec : (model.bellSchedule.recessAfterPeriod ?? 4);
                      if (dayPeriods > 1 && recPos >= dayPeriods) {
                        recPos = Math.max(1, dayPeriods - 1);
                      }
                      const recDur = model.bellSchedule.dailyRecessDurations?.[day] ?? model.bellSchedule.recessDuration ?? 20;
                      const displayName = day.charAt(0) + day.slice(1).toLowerCase();

                      return (
                        <tr key={day} className={!isRecessOn ? 'is-disabled' : ''}>
                          <td>
                            <strong>{displayName}</strong>
                            <div style={{ fontSize: '0.7rem', color: 'var(--color-text-tertiary)' }}>
                              {dayPeriods} periods
                            </div>
                          </td>
                          <td>
                            <label style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem', cursor: dayPeriods <= 1 ? 'not-allowed' : 'pointer' }}>
                              <input
                                type="checkbox"
                                checked={isRecessOn && dayPeriods > 1}
                                disabled={dayPeriods <= 1}
                                onChange={(e) => {
                                  const nextEnabled = { ...(model.bellSchedule.dailyRecessEnabled || {}) };
                                  nextEnabled[day] = e.target.checked;
                                  const nextRecAfter = { ...(model.bellSchedule.dailyRecessAfterPeriod || {}) };
                                  if (!nextRecAfter[day] || nextRecAfter[day] < 1 || (dayPeriods > 1 && nextRecAfter[day] >= dayPeriods)) {
                                    nextRecAfter[day] = Math.max(1, Math.min(model.bellSchedule.recessAfterPeriod || 4, dayPeriods > 1 ? dayPeriods - 1 : 1));
                                  }
                                  setModel({
                                    ...model,
                                    bellSchedule: {
                                      ...model.bellSchedule,
                                      dailyRecessEnabled: nextEnabled,
                                      dailyRecessAfterPeriod: nextRecAfter,
                                    },
                                  });
                                  setIsDirty(true);
                                }}
                              />
                              <span style={{ fontWeight: 600, color: (isRecessOn && dayPeriods > 1) ? 'var(--color-brand)' : 'var(--color-text-tertiary)' }}>
                                {(isRecessOn && dayPeriods > 1) ? 'Enabled' : 'Disabled'}
                              </span>
                            </label>
                          </td>
                          <td>
                            <select
                              disabled={!isRecessOn || dayPeriods <= 1}
                              value={recPos}
                              onChange={(e) => {
                                const pos = parseInt(e.target.value, 10);
                                const nextMap = { ...(model.bellSchedule.dailyRecessAfterPeriod || {}) };
                                nextMap[day] = pos;
                                setModel({
                                  ...model,
                                  bellSchedule: {
                                    ...model.bellSchedule,
                                    dailyRecessAfterPeriod: nextMap,
                                  },
                                });
                                setIsDirty(true);
                              }}
                              className="setup-select"
                              style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
                            >
                              {Array.from({ length: Math.max(1, dayPeriods - 1) }, (_, i) => i + 1).map((p) => (
                                <option key={p} value={p}>
                                  After Period #{p}
                                </option>
                              ))}
                            </select>
                          </td>
                          <td>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                              <input
                                type="number"
                                min={10}
                                max={60}
                                disabled={!isRecessOn}
                                value={recDur}
                                onChange={(e) => {
                                  const d = parseInt(e.target.value, 10) || 20;
                                  const nextMap = { ...(model.bellSchedule.dailyRecessDurations || {}) };
                                  nextMap[day] = d;
                                  setModel({
                                    ...model,
                                    bellSchedule: {
                                      ...model.bellSchedule,
                                      dailyRecessDurations: nextMap,
                                    },
                                  });
                                  setIsDirty(true);
                                }}
                                className="setup-input"
                                style={{ width: '65px', padding: '0.25rem 0.4rem', fontSize: '0.75rem' }}
                              />
                              <span style={{ fontSize: '0.75rem', color: 'var(--color-text-tertiary)' }}>min</span>
                            </div>
                          </td>
                          <td>
                            {isRecessOn ? (
                              <span style={{ color: 'var(--color-text-secondary)', fontSize: '0.75rem' }}>
                                Partitions day into Pre-Recess (P1–P{recPos}) and Post-Recess (P{recPos + 1}–P{dayPeriods}). H7 prohibits PE after recess.
                              </span>
                            ) : (
                              <span style={{ color: 'var(--color-jade)', fontWeight: 600, fontSize: '0.75rem' }}>
                                ✓ Single continuous session ({dayPeriods} periods). No break slot or phantom recess.
                              </span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          {/* Section 4: Live Bell Timing Preview & Timeline Overrides */}
          <div className="timing-section-card">
            <div className="timing-section-header">
              <div>
                <h3 className="timing-section-title">
                  <Clock size={16} className="text-brand" /> Section 4: Live Bell Timing Preview & Timeline Overrides
                </h3>
                <p className="timing-section-subtitle">
                  Inspect the dynamically calculated timeline and provide explicit start/end overrides for unusual periods.
                </p>
              </div>

              {/* Day Selector Tabs */}
              <div style={{ display: 'flex', gap: '0.35rem' }}>
                <button
                  type="button"
                  className="preset-pill-btn"
                  style={{
                    backgroundColor: previewDay === 'MONDAY' ? 'var(--color-brand)' : 'var(--bg-surface)',
                    color: previewDay === 'MONDAY' ? '#ffffff' : 'var(--color-text-secondary)',
                    borderColor: previewDay === 'MONDAY' ? 'var(--color-brand)' : 'var(--surface-border-strong)',
                  }}
                  onClick={() => setPreviewDay('MONDAY')}
                >
                  Regular Weekday (Mon–Fri)
                </button>
                {capacitySummary?.workingDays.includes('SATURDAY') && (
                  <button
                    type="button"
                    className="preset-pill-btn"
                    style={{
                      backgroundColor: previewDay === 'SATURDAY' ? 'var(--color-brand)' : 'var(--bg-surface)',
                      color: previewDay === 'SATURDAY' ? '#ffffff' : 'var(--color-text-secondary)',
                      borderColor: previewDay === 'SATURDAY' ? 'var(--color-brand)' : 'var(--surface-border-strong)',
                    }}
                    onClick={() => setPreviewDay('SATURDAY')}
                  >
                    Saturday Schedule
                  </button>
                )}
              </div>
            </div>

            {/* Timeline Table */}
            <div style={{ overflowX: 'auto' }}>
              <table className="timeline-preview-table">
                <thead>
                  <tr>
                    <th style={{ width: '160px' }}>Slot / Event</th>
                    <th style={{ width: '130px' }}>Start Time</th>
                    <th style={{ width: '130px' }}>End Time</th>
                    <th style={{ width: '120px' }}>Duration</th>
                    <th style={{ width: '150px' }}>Calculation Status</th>
                    <th style={{ textAlign: 'right' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {getTimelineSlotsForDay(previewDay).map((slot, idx) => {
                    if (slot.type === 'recess') {
                      return (
                        <tr key={`recess-${idx}`} className="is-recess-row">
                          <td>
                            <strong>☕ RECESS BREAK</strong>
                          </td>
                          <td><strong>{slot.startTime}</strong></td>
                          <td><strong>{slot.endTime}</strong></td>
                          <td>{slot.durationMinutes} min break</td>
                          <td>
                            <span className="override-pill-auto">Non-Teaching</span>
                          </td>
                          <td style={{ textAlign: 'right', color: 'var(--color-text-tertiary)', fontSize: '0.7rem' }}>
                            Break Interval
                          </td>
                        </tr>
                      );
                    }

                    if (slot.type === 'short_break') {
                      return (
                        <tr key={`sb-${idx}`} className="is-break-row">
                          <td>
                            <strong>SHORT BREAK</strong>
                          </td>
                          <td><strong>{slot.startTime}</strong></td>
                          <td><strong>{slot.endTime}</strong></td>
                          <td>{slot.durationMinutes} min pause</td>
                          <td>
                            <span className="override-pill-auto">Mid-Day Pause</span>
                          </td>
                          <td style={{ textAlign: 'right', color: 'var(--color-text-tertiary)', fontSize: '0.7rem' }}>
                            Non-Instructional
                          </td>
                        </tr>
                      );
                    }

                    return (
                      <tr key={`p-${slot.periodNumber}`}>
                        <td>
                          <strong>{slot.label}</strong>
                          {slot.periodNumber === 1 && (
                            <div style={{ fontSize: '0.7rem', color: 'var(--color-brand)', fontWeight: 600 }}>
                              Class Teacher Slot (H1)
                            </div>
                          )}
                        </td>
                        <td>
                          <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>{slot.startTime}</span>
                        </td>
                        <td>
                          <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>{slot.endTime}</span>
                        </td>
                        <td>{slot.durationMinutes} min</td>
                        <td>
                          {slot.isOverridden ? (
                            <span className="override-pill-manual">Manual Override</span>
                          ) : (
                            <span className="override-pill-auto">Auto-Calculated</span>
                          )}
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.4rem' }}>
                            <Button
                              variant="secondary"
                              size="sm"
                              leftIcon={<Edit3 size={12} />}
                              onClick={() => slot.periodNumber && handleOpenOverride(slot.periodNumber, slot.startTime, slot.endTime, slot.durationMinutes)}
                              title="Set manual timing override"
                            >
                              Override
                            </Button>
                            {slot.isOverridden && slot.periodNumber && (
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => handleResetOverride(slot.periodNumber!)}
                                title="Reset to auto-calculated timing"
                              >
                                Reset
                              </Button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Saturday No-Recess Note Banner */}
            {previewDay === 'SATURDAY' && !model.bellSchedule.dailyRecessEnabled?.['SATURDAY'] && (
              <div style={{ padding: '0.65rem 0.85rem', backgroundColor: 'var(--bg-canvas)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--surface-border-subtle)', display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>
                <CheckCircle2 size={15} className="text-jade" />
                <span>
                  <strong>Saturday Recess is Disabled:</strong> The 4 periods run continuously without any phantom recess slot. Exporters and solver treat Saturday as a single 4-lecture morning block.
                </span>
              </div>
            )}
          </div>

          {/* Section 5: Academic Capacity & Curriculum Balance */}
          <div className="timing-section-card">
            <div className="timing-section-header">
              <div>
                <h3 className="timing-section-title">
                  <Grid size={16} className="text-brand" /> Section 5: Academic Capacity & Curriculum Balance
                </h3>
                <p className="timing-section-subtitle">
                  Verify available teaching periods against curriculum lecture requirements, ensuring schedule feasibility.
                </p>
              </div>
            </div>

            {/* Capacity Banner */}
            {capacitySummary && (
              <div className={`capacity-banner ${capacitySummary.isValid ? 'is-valid' : 'is-invalid'}`}>
                <div className="capacity-banner__icon">
                  {capacitySummary.isValid ? '✓' : '✗'}
                </div>
                <div className="capacity-banner__content">
                  <h4 className="capacity-banner__title">
                    {capacitySummary.isValid
                      ? (capacitySummary.spare > 0
                          ? `FEASIBLE CAPACITY: ${capacitySummary.spare} spare period(s) per class division`
                          : `EXACT MATCH: 0 spare periods (100% capacity utilization)`)
                      : `CAPACITY DEFICIT: Missing ${capacitySummary.deficit} periods per class`}
                  </h4>
                  <p className="capacity-banner__formula">
                    {capacitySummary.isValid
                      ? `Available: ${capacitySummary.available} periods (${capacitySummary.breakdown}) >= Required: ${capacitySummary.required} periods/class (Recess excluded)`
                      : `Available: ${capacitySummary.available} periods (${capacitySummary.breakdown}) < Required: ${capacitySummary.required} periods/class. Please adjust daily periods or weekly curriculum requirements.`}
                  </p>
                </div>
              </div>
            )}

            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginTop: '0.5rem' }}>
              <div className="setup-field">
                <label style={{ fontWeight: 600 }}>Weekly Required Teaching Periods per Class</label>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                  <input
                    type="number"
                    min={20}
                    max={60}
                    value={model.bellSchedule.weeklyRequiredPeriodsPerClass}
                    onChange={(e) => {
                      const req = parseInt(e.target.value, 10) || 48;
                      setModel({
                        ...model,
                        bellSchedule: {
                          ...model.bellSchedule,
                          weeklyRequiredPeriodsPerClass: req,
                        },
                      });
                      setIsDirty(true);
                    }}
                    className="setup-input"
                    style={{ width: '90px', fontWeight: 700 }}
                  />
                  <span style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary)', fontWeight: 500 }}>
                    periods / class / week
                  </span>
                </div>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)', marginTop: '0.35rem', lineHeight: 1.4 }}>
                  The exact number of teaching periods each class division must be scheduled per week. Subject allocations must sum to this value.
                </span>
              </div>
            </div>

            {/* Axioms */}
            <h3 style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)', margin: '1rem 0 0.5rem' }}>
              Hard Constraint Axioms Enforced (H1–H10)
            </h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '0.75rem' }}>
              <div style={{ padding: '0.75rem 1rem', backgroundColor: 'var(--bg-canvas)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--surface-border)' }}>
                <strong style={{ color: 'var(--color-text-primary)', display: 'block', fontSize: 'var(--text-xs)', marginBottom: '0.15rem' }}>H1: Class Teacher First Period</strong>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>Designated class teachers fill Period 1 slots.</span>
              </div>
              <div style={{ padding: '0.75rem 1rem', backgroundColor: 'var(--bg-canvas)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--surface-border)' }}>
                <strong style={{ color: 'var(--color-text-primary)', display: 'block', fontSize: 'var(--text-xs)', marginBottom: '0.15rem' }}>H2: Consecutive Lecture Limits</strong>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>Max 2 for load ≤ 30; Max 3 for load &gt; 30 (enforced seamlessly on both partitioned and break-free days).</span>
              </div>
              <div style={{ padding: '0.75rem 1rem', backgroundColor: 'var(--bg-canvas)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--surface-border)' }}>
                <strong style={{ color: 'var(--color-text-primary)', display: 'block', fontSize: 'var(--text-xs)', marginBottom: '0.15rem' }}>H5: Computer Lab Exclusivity</strong>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>No simultaneous computer lab sessions school-wide.</span>
              </div>
              <div style={{ padding: '0.75rem 1rem', backgroundColor: 'var(--bg-canvas)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--surface-border)' }}>
                <strong style={{ color: 'var(--color-text-primary)', display: 'block', fontSize: 'var(--text-xs)', marginBottom: '0.15rem' }}>H6 & H7: Physical Education Rules</strong>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>P.E. prohibited in Period 1 and immediately post-recess (only on days where recess is active).</span>
              </div>
              <div style={{ padding: '0.75rem 1rem', backgroundColor: 'var(--bg-canvas)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--surface-border)' }}>
                <strong style={{ color: 'var(--color-text-primary)', display: 'block', fontSize: 'var(--text-xs)', marginBottom: '0.15rem' }}>H8: Daily Activity Cap</strong>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>At most 2 activity/sports periods per class per day.</span>
              </div>
              <div style={{ padding: '0.75rem 1rem', backgroundColor: 'var(--bg-canvas)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--surface-border)' }}>
                <strong style={{ color: 'var(--color-text-primary)', display: 'block', fontSize: 'var(--text-xs)', marginBottom: '0.15rem' }}>H10: Lab & Activity Partition</strong>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>Computer and PE partitioned across recess; on short break-free days, max 1 activity is allowed.</span>
              </div>
            </div>
          </div>

          {/* Inline Override Modal */}
          {overrideModalPeriod !== null && (
            <div style={{
              position: 'fixed',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              backgroundColor: 'rgba(0, 0, 0, 0.5)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              zIndex: 9999,
              backdropFilter: 'blur(2px)',
            }}>
              <div style={{
                backgroundColor: 'var(--bg-surface)',
                border: '1px solid var(--surface-border)',
                borderRadius: 'var(--radius-lg)',
                padding: '1.5rem',
                maxWidth: '450px',
                width: '100%',
                boxShadow: 'var(--shadow-lg)',
                display: 'flex',
                flexDirection: 'column',
                gap: '1rem',
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <h4 style={{ margin: 0, fontSize: 'var(--text-sm)', fontWeight: 700, color: 'var(--color-text-primary)' }}>
                    Override Timing for Period {overrideModalPeriod}
                  </h4>
                  <button
                    onClick={() => setOverrideModalPeriod(null)}
                    style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--color-text-tertiary)' }}
                  >
                    <X size={18} />
                  </button>
                </div>

                <p style={{ margin: 0, fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Set an explicit start time, end time, and instructional duration for Period {overrideModalPeriod}.
                </p>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                  <div className="setup-field">
                    <label>Start Time (HH:MM)</label>
                    <input
                      type="time"
                      value={overrideStartTime}
                      onChange={(e) => setOverrideStartTime(e.target.value)}
                      className="setup-input"
                    />
                  </div>
                  <div className="setup-field">
                    <label>End Time (HH:MM)</label>
                    <input
                      type="time"
                      value={overrideEndTime}
                      onChange={(e) => setOverrideEndTime(e.target.value)}
                      className="setup-input"
                    />
                  </div>
                </div>

                <div className="setup-field">
                  <label>Duration (Minutes)</label>
                  <input
                    type="number"
                    min={15}
                    max={120}
                    value={overrideDuration}
                    onChange={(e) => setOverrideDuration(parseInt(e.target.value, 10) || 35)}
                    className="setup-input"
                  />
                </div>

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem' }}>
                  <Button variant="ghost" size="sm" onClick={() => setOverrideModalPeriod(null)}>
                    Cancel
                  </Button>
                  <Button variant="primary" size="sm" onClick={handleApplyOverride} leftIcon={<Check size={14} />}>
                    Apply Override
                  </Button>
                </div>
              </div>
            </div>
          )}

          {/* Navigation to Next Step */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '1.5rem', paddingTop: '1rem', borderTop: '1px solid var(--surface-border)' }}>
            <Button
              variant="primary"
              size="md"
              onClick={() => handleSelectTab('constraints')}
              rightIcon={<ArrowRight size={16} />}
            >
              Continue to Review Constraints (Step 6)
            </Button>
          </div>
        </div>
      )}

      {/* Tab 6: Review & Configure Constraints */}
      {activeTab === 'constraints' && (
        <div className="setup-step-panel">
          {/* Summary Banner */}
          <div className="constraints-summary-banner">
            <div className="constraint-stat-box">
              <span className="constraint-stat-box__label">Hard Rules</span>
              <div className="constraint-stat-box__val">
                {activeHardCount} <span style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>/ 10 Active</span>
              </div>
              <span className="constraint-stat-box__sub">
                {activeHardCount === 10 ? 'All operational rules enabled' : `${10 - activeHardCount} optional rule(s) bypassed`}
              </span>
            </div>

            <div className="constraint-stat-box">
              <span className="constraint-stat-box__label">Mandatory Invariants</span>
              <div className="constraint-stat-box__val" style={{ color: 'var(--color-jade)' }}>
                {mandatoryCount} <span style={{ fontSize: 'var(--text-sm)' }}>Locked</span>
              </div>
              <span className="constraint-stat-box__sub">H3, H4, H9 strictly enforced</span>
            </div>

            <div className="constraint-stat-box">
              <span className="constraint-stat-box__label">Soft Objectives</span>
              <div className="constraint-stat-box__val" style={{ color: 'var(--color-brand)' }}>
                {activeSoftCount} <span style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>/ 6 Active</span>
              </div>
              <span className="constraint-stat-box__sub">Total Priority Weight: {totalSoftWeight}</span>
            </div>

            <div className="constraint-stat-box">
              <span className="constraint-stat-box__label">Next Step</span>
              <div style={{ marginTop: '0.25rem' }}>
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => handleSelectTab('review')}
                  rightIcon={<ArrowRight size={14} />}
                >
                  Pre-flight Audit
                </Button>
              </div>
            </div>
          </div>

          {/* Group 1: Hard Constraints */}
          <section className="constraints-section">
            <div className="constraints-section__header">
              <div className="constraints-section__title-group">
                <h3>
                  <Shield size={18} className="text-jade" /> Hard Constraints (Operational Rules)
                </h3>
                <p>
                  Rules that must be satisfied for a mathematically valid, conflict-free timetable. Mandatory rules (H3, H4, H9) cannot be disabled.
                </p>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Badge variant={activeHardCount === 10 ? 'jade' : 'gold'} size="sm">
                  {activeHardCount} / 10 Active
                </Badge>
              </div>
            </div>

            <div className="constraints-grid">
              {['H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'H7', 'H8', 'H9', 'H10'].map((id) => {
                const c = currentConstraints.hardConstraints[id];
                if (!c) return null;
                const isMandatory = Boolean(c.mandatory);
                const isEnabled = isMandatory ? true : Boolean(c.enabled);

                return (
                  <div
                    key={id}
                    className={`constraint-card ${isMandatory ? 'is-mandatory' : ''} ${!isEnabled ? 'is-disabled' : ''}`}
                  >
                    <div>
                      <div className="constraint-card__header">
                        <div className="constraint-card__title-row">
                          <span className="constraint-id-badge">{c.id}</span>
                          <h4 className="constraint-card__name">{c.name}</h4>
                        </div>
                        {isMandatory ? (
                          <div className="constraint-locked-pill" title="Mandatory constraint required for timetable integrity">
                            <Lock size={11} /> Locked
                          </div>
                        ) : (
                          <label className="sies-toggle-label" title={isEnabled ? 'Click to disable constraint' : 'Click to enable constraint'}>
                            <input
                              type="checkbox"
                              checked={isEnabled}
                              onChange={() => handleToggleHardConstraint(id)}
                            />
                            <span className="sies-toggle-track" />
                          </label>
                        )}
                      </div>

                      <p className="constraint-card__desc">{c.description}</p>
                    </div>

                    {/* Configurable Numerical Parameters */}
                    {id === 'H2' && isEnabled && (
                      <div className="constraint-card__params">
                        <div className="constraint-param-row">
                          <label>Max Continuous Periods (Base):</label>
                          <input
                            type="number"
                            min={1}
                            max={4}
                            value={c.maxContinuousLectures ?? 2}
                            onChange={(e) =>
                              handleHardParamChange('H2', 'maxContinuousLectures', parseInt(e.target.value, 10) || 2)
                            }
                            className="constraint-param-input"
                          />
                        </div>
                        <span style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)', lineHeight: 1.35 }}>
                          Standard workload (≤30): max <strong>{c.maxContinuousLectures ?? 2}</strong> lectures. High workload (&gt;30) allows <strong>{(c.maxContinuousLectures ?? 2) + 1}</strong>.
                        </span>
                      </div>
                    )}

                    {id === 'H5' && isEnabled && (
                      <div className="constraint-card__params">
                        <div className="constraint-param-row">
                          <label>Max Concurrent Lab Classes:</label>
                          <input
                            type="number"
                            min={1}
                            max={5}
                            value={c.maxConcurrentLabClasses ?? 1}
                            onChange={(e) =>
                              handleHardParamChange('H5', 'maxConcurrentLabClasses', parseInt(e.target.value, 10) || 1)
                            }
                            className="constraint-param-input"
                          />
                        </div>
                        <span style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)', lineHeight: 1.35 }}>
                          Enforces dedicated single computer lab exclusivity across Standards 5 to 10.
                        </span>
                      </div>
                    )}

                    {id === 'H6' && isEnabled && (
                      <div className="constraint-card__params">
                        <div className="constraint-param-row">
                          <label>Prohibited PE Period:</label>
                          <select
                            value={c.prohibitedPeriod ?? 1}
                            onChange={(e) =>
                              handleHardParamChange('H6', 'prohibitedPeriod', parseInt(e.target.value, 10) || 1)
                            }
                            className="setup-select"
                            style={{ width: '110px', padding: '0.3rem 0.5rem', fontSize: 'var(--text-xs)' }}
                          >
                            <option value={1}>Period 1</option>
                            <option value={2}>Period 2</option>
                            <option value={3}>Period 3</option>
                            <option value={4}>Period 4</option>
                          </select>
                        </div>
                        <span style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)', lineHeight: 1.35 }}>
                          Physical education sessions will not be scheduled in Period {c.prohibitedPeriod ?? 1}.
                        </span>
                      </div>
                    )}

                    {id === 'H8' && isEnabled && (
                      <div className="constraint-card__params">
                        <div className="constraint-param-row">
                          <label>Max Activity Periods / Day:</label>
                          <input
                            type="number"
                            min={1}
                            max={4}
                            value={c.maxActivityPeriodsPerDay ?? 2}
                            onChange={(e) =>
                              handleHardParamChange('H8', 'maxActivityPeriodsPerDay', parseInt(e.target.value, 10) || 2)
                            }
                            className="constraint-param-input"
                          />
                        </div>
                        <span style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)', lineHeight: 1.35 }}>
                          Caps non-academic subjects (Drawing, Craft, PE, Library) per division per day.
                        </span>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </section>

          {/* Group 2: Soft Constraints */}
          <section className="constraints-section">
            <div className="constraints-section__header">
              <div className="constraints-section__title-group">
                <h3>
                  <SlidersHorizontal size={18} className="text-brand" /> Soft Constraints (Optimization Objectives)
                </h3>
                <p>
                  Preferences that the CP-SAT solver attempts to maximize or minimize. Higher weights assign higher priority to satisfying the objective.
                </p>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Badge variant={activeSoftCount > 0 ? 'gold' : 'neutral'} size="sm">
                  {activeSoftCount} / 6 Active
                </Badge>
              </div>
            </div>

            <div className="constraints-grid">
              {['S1', 'S2', 'S3', 'S4', 'S5', 'S6'].map((id) => {
                const c = currentConstraints.softConstraints[id];
                if (!c) return null;
                const isEnabled = Boolean(c.enabled && c.weight > 0);

                return (
                  <div
                    key={id}
                    className={`constraint-card ${!isEnabled ? 'is-disabled' : ''}`}
                  >
                    <div>
                      <div className="constraint-card__header">
                        <div className="constraint-card__title-row">
                          <span className="constraint-id-badge is-soft">{c.id}</span>
                          <h4 className="constraint-card__name">{c.name}</h4>
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

                      <p className="constraint-card__desc">{c.description}</p>
                    </div>

                    <div className="constraint-card__params">
                      <div className="constraint-param-row">
                        <label>Optimization Weight:</label>
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
                            className="constraint-param-input"
                          />
                          <span className="constraint-weight-val">
                            {c.weight === 0 ? 'Off' : c.weight >= 100 ? 'High' : c.weight >= 50 ? 'Med' : 'Low'}
                          </span>
                        </div>
                      </div>

                      <div className="constraint-slider-row">
                        <input
                          type="range"
                          min={0}
                          max={300}
                          step={5}
                          value={c.weight}
                          onChange={(e) =>
                            handleSoftWeightChange(id, parseInt(e.target.value, 10) || 0)
                          }
                        />
                      </div>

                      <span style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)', display: 'flex', justifyContent: 'space-between' }}>
                        <span>SIES Default: {c.defaultWeight}</span>
                        <span>{isEnabled ? 'Active in Solver' : 'Ignored by Solver'}</span>
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </section>

          {/* Bottom Action Bar inside constraints */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '1.5rem', flexWrap: 'wrap', gap: '1rem' }}>
            <Button
              variant="secondary"
              size="sm"
              onClick={handleResetConstraints}
            >
              <RotateCcw size={14} /> Reset Constraints to Defaults
            </Button>

            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <Button
                variant="primary"
                size="sm"
                onClick={handleSaveModel}
                isLoading={isSaving}
              >
                <Save size={14} /> Save Configuration
              </Button>

              <Button
                variant="secondary"
                size="sm"
                onClick={() => handleSelectTab('review')}
                rightIcon={<ArrowRight size={14} />}
              >
                Proceed to Pre-flight Audit
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Tab 7: Review & Validation */}
      {activeTab === 'review' && (
        <div className="setup-step-panel">
          {/* Constraints Readiness Summary Card */}
          <div className="setup-validation-box" style={{ backgroundColor: 'var(--bg-canvas)', border: '1px solid var(--surface-border)', marginBottom: '0.5rem' }}>
            <div className="setup-validation-header" style={{ justifyContent: 'space-between', width: '100%' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Sliders size={18} className="text-brand" />
                <span style={{ color: 'var(--color-text-primary)', fontWeight: 600 }}>
                  Active Solver Constraints Configuration
                </span>
              </div>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => handleSelectTab('constraints')}
                rightIcon={<ArrowRight size={12} />}
              >
                Modify Constraints
              </Button>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '0.75rem', marginTop: '0.75rem' }}>
              <div style={{ padding: '0.5rem 0.75rem', background: 'var(--bg-surface)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--surface-border-subtle)' }}>
                <div style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)', fontWeight: 600 }}>HARD CONSTRAINTS</div>
                <div style={{ fontSize: 'var(--text-sm)', fontWeight: 700, color: 'var(--color-jade)', marginTop: '0.15rem' }}>
                  {activeHardCount} of 10 Enabled
                </div>
                <div style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)' }}>
                  Mandatory: H3, H4, H9 Locked
                </div>
              </div>

              <div style={{ padding: '0.5rem 0.75rem', background: 'var(--bg-surface)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--surface-border-subtle)' }}>
                <div style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)', fontWeight: 600 }}>SOFT OBJECTIVES</div>
                <div style={{ fontSize: 'var(--text-sm)', fontWeight: 700, color: 'var(--color-brand)', marginTop: '0.15rem' }}>
                  {activeSoftCount} Active (Weight: {totalSoftWeight})
                </div>
                <div style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)' }}>
                  S1–S6 Tradeoffs Weighted
                </div>
              </div>

              <div style={{ padding: '0.5rem 0.75rem', background: 'var(--bg-surface)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--surface-border-subtle)' }}>
                <div style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)', fontWeight: 600 }}>KEY PARAMETERS</div>
                <div style={{ fontSize: '0.75rem', color: 'var(--color-text-primary)', marginTop: '0.15rem' }}>
                  H2 Max Cont: {currentConstraints.hardConstraints['H2']?.maxContinuousLectures ?? 2} • H5 Lab: {currentConstraints.hardConstraints['H5']?.maxConcurrentLabClasses ?? 1}
                </div>
                <div style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)' }}>
                  H6 PE Prohibited: P{currentConstraints.hardConstraints['H6']?.prohibitedPeriod ?? 1} • H8 Act: {currentConstraints.hardConstraints['H8']?.maxActivityPeriodsPerDay ?? 2}/day
                </div>
              </div>
            </div>
          </div>

          <div className="setup-card">
            <div className="setup-card__header">
              <div>
                <h2 className="setup-card__title">
                  <CheckCircle2 size={18} className="text-jade" /> Mathematical Feasibility Audit
                </h2>
                <p className="setup-card__subtitle">
                  Inspect scheduling axioms, capacity checks, and warnings prior to launching generation.
                </p>
              </div>

              <Button
                variant="primary"
                size="sm"
                onClick={onNavigateToStudio}
                style={{ backgroundColor: 'var(--color-jade)' }}
              >
                Launch Timetable Studio <ArrowRight size={14} />
              </Button>
            </div>

            {validation && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                <div className={`setup-validation-box ${validation.isValid ? 'is-valid' : 'is-invalid'}`}>
                  <div className="setup-validation-header">
                    {validation.isValid ? <CheckCircle2 size={20} /> : <AlertOctagon size={20} />}
                    <span>
                      {validation.isValid
                        ? 'Timetable Feasibility Confirmed — Ready for CP-SAT Solver'
                        : `${validation.errors.length} Critical Feasibility Deficit(s) Detected`}
                    </span>
                  </div>

                  {validation.errors.length > 0 && (
                    <ul className="setup-validation-list errors">
                      {validation.errors.map((err, i) => (
                        <li key={i}>{err}</li>
                      ))}
                    </ul>
                  )}
                </div>

                {validation.warnings.length > 0 && (
                  <div className="setup-validation-box" style={{ backgroundColor: 'var(--color-gold-subtle)', border: '1px solid var(--color-gold-border)' }}>
                    <div className="setup-validation-header" style={{ color: 'var(--color-gold)' }}>
                      <AlertTriangle size={18} />
                      <span>Configuration Notices & Warnings ({validation.warnings.length})</span>
                    </div>
                    <ul className="setup-validation-list warnings">
                      {validation.warnings.map((w, i) => (
                        <li key={i}>{w}</li>
                      ))}
                    </ul>
                  </div>
                )}

                {validation.notices.length > 0 && (
                  <div className="setup-validation-box is-valid">
                    <div className="setup-validation-header">
                      <CheckCircle2 size={18} />
                      <span>Capacity & Metric Verifications</span>
                    </div>
                    <ul className="setup-validation-list notices">
                      {validation.notices.map((n, i) => (
                        <li key={i}>{n}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Persistent Bottom Action Bar */}
      <div className="setup-footer-bar">
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-ivory-dim)' }}>
            Active Structure: <strong>Standards {model.startStandard}–{model.endStandard}</strong> ({model.classes.length} classes, {model.professors.length} teachers, {model.subjects.length} subjects)
          </span>
          {isDirty && (
            <span className="setup-unsaved-badge">
              <AlertTriangle size={12} /> Pending Changes
            </span>
          )}
        </div>

        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <Button variant="secondary" size="sm" onClick={handleResetModel} disabled={isSaving}>
            <RotateCcw size={14} /> Reset
          </Button>
          <Button variant="primary" size="sm" onClick={handleSaveModel} isLoading={isSaving}>
            <Save size={14} /> Save Configuration
          </Button>
        </div>
      </div>

      {/* MODAL 1: EDIT CURRICULAR SUBJECT */}
      {editingSubject && (
        <Modal
          isOpen={true}
          onClose={() => setEditingSubject(null)}
          title={`Edit Curricular Subject (${editingSubject.originalCode})`}
          description="Update subject code, formal name, curriculum category, and facility requirements."
          size="md"
        >
          <form onSubmit={handleSaveEditSubject} className="setup-edit-form">
            {editSubjectError && (
              <div className="validation-alert-banner is-fail" style={{ marginBottom: '1rem' }}>
                <div className="validation-alert-banner__icon">
                  <AlertTriangle size={16} className="text-terracotta" />
                </div>
                <div className="validation-alert-banner__content">
                  <p>{editSubjectError}</p>
                </div>
              </div>
            )}

            <div className="setup-field" style={{ marginBottom: '1rem' }}>
              <label className="form-label">Subject Code (e.g. ENG, MATH)</label>
              <input
                type="text"
                required
                value={editingSubject.code}
                onChange={(e) => setEditingSubject({ ...editingSubject, code: e.target.value.toUpperCase() })}
                className="setup-input"
              />
              <span className="form-hint">
                Renaming this code automatically updates all class teacher assignments and allocations.
              </span>
            </div>

            <div className="setup-field" style={{ marginBottom: '1rem' }}>
              <label className="form-label">Display Name</label>
              <input
                type="text"
                required
                value={editingSubject.name}
                onChange={(e) => setEditingSubject({ ...editingSubject, name: e.target.value })}
                className="setup-input"
              />
            </div>

            <div className="setup-field" style={{ marginBottom: '1rem' }}>
              <label className="form-label">Curricular Category</label>
              <select
                value={editingSubject.category}
                onChange={(e) => setEditingSubject({ ...editingSubject, category: e.target.value as any })}
                className="setup-select"
              >
                <option value="CORE_ACADEMIC">Core Academic</option>
                <option value="ACTIVITY">Activity / Co-curricular</option>
                <option value="SPECIAL_CURRICULAR">Special Curricular</option>
              </select>
            </div>

            <div className="setup-field" style={{ marginBottom: '1.5rem' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={editingSubject.isLab}
                  onChange={(e) => setEditingSubject({ ...editingSubject, isLab: e.target.checked })}
                />
                <span style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)' }}>Requires Dedicated Lab Facility (Single Computer Lab Exclusive)</span>
              </label>
            </div>

            <div className="modal-actions-bar">
              <Button type="button" variant="ghost" size="md" onClick={() => setEditingSubject(null)}>
                Cancel
              </Button>
              <Button type="submit" variant="primary" size="md" leftIcon={<Check size={14} />}>
                Apply & Update
              </Button>
            </div>
          </form>
        </Modal>
      )}

      {/* MODAL 2: EDIT FACULTY MEMBER */}
      {editingProfessor && (
        <Modal
          isOpen={true}
          onClose={() => setEditingProfessor(null)}
          title={`Edit Faculty Member (${editingProfessor.originalCode})`}
          description="Update instructor identification, name, class teacher designation, and substitution availability."
          size="md"
        >
          <form onSubmit={handleSaveEditProfessor} className="setup-edit-form">
            {editProfessorError && (
              <div className="validation-alert-banner is-fail" style={{ marginBottom: '1rem' }}>
                <div className="validation-alert-banner__icon">
                  <AlertTriangle size={16} className="text-terracotta" />
                </div>
                <div className="validation-alert-banner__content">
                  <p>{editProfessorError}</p>
                </div>
              </div>
            )}

            <div className="setup-field" style={{ marginBottom: '1rem' }}>
              <label className="form-label">Teacher Code (e.g. T01)</label>
              <input
                type="text"
                required
                value={editingProfessor.code}
                onChange={(e) => setEditingProfessor({ ...editingProfessor, code: e.target.value.toUpperCase() })}
                className="setup-input"
              />
            </div>

            <div className="setup-field" style={{ marginBottom: '1rem' }}>
              <label className="form-label">Full Name</label>
              <input
                type="text"
                required
                value={editingProfessor.name}
                onChange={(e) => setEditingProfessor({ ...editingProfessor, name: e.target.value })}
                className="setup-input"
              />
            </div>

            <div className="setup-field" style={{ marginBottom: '1rem' }}>
              <label className="form-label">Designated Class Teacher Of</label>
              <select
                value={editingProfessor.classTeacherOf}
                onChange={(e) => setEditingProfessor({ ...editingProfessor, classTeacherOf: e.target.value })}
                className="setup-select"
              >
                <option value="">None (Standard Faculty)</option>
                {model.classes.map((cls) => (
                  <option key={cls} value={cls}>Class {cls}</option>
                ))}
              </select>
              <span className="form-hint">
                Class teachers are prioritized to instruct Period 1 in their assigned classroom division.
              </span>
            </div>

            <div className="setup-field" style={{ marginBottom: '1.5rem' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={editingProfessor.freeTeacher}
                  onChange={(e) => setEditingProfessor({ ...editingProfessor, freeTeacher: e.target.checked })}
                />
                <span style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)' }}>Free / Substitute Teacher (Floating staff without fixed schedule)</span>
              </label>
            </div>

            <div className="modal-actions-bar">
              <Button type="button" variant="ghost" size="md" onClick={() => setEditingProfessor(null)}>
                Cancel
              </Button>
              <Button type="submit" variant="primary" size="md" leftIcon={<Check size={14} />}>
                Apply & Update
              </Button>
            </div>
          </form>
        </Modal>
      )}

      {/* MODAL 3: EDIT WEEKLY ALLOCATION */}
      {editingAllocation && (
        <Modal
          isOpen={true}
          onClose={() => setEditingAllocation(null)}
          title={`Edit Allocation for Class ${editingAllocation.classId}`}
          description="Modify the subject, assigned instructor, or weekly lecture quota for this classroom division."
          size="md"
        >
          <form onSubmit={handleSaveEditAllocation} className="setup-edit-form">
            {editAllocationError && (
              <div className="validation-alert-banner is-fail" style={{ marginBottom: '1rem' }}>
                <div className="validation-alert-banner__icon">
                  <AlertTriangle size={16} className="text-terracotta" />
                </div>
                <div className="validation-alert-banner__content">
                  <p>{editAllocationError}</p>
                </div>
              </div>
            )}

            <div className="setup-field" style={{ marginBottom: '1rem' }}>
              <label className="form-label">Class Division</label>
              <input
                type="text"
                disabled
                value={`Class ${editingAllocation.classId}`}
                className="setup-input"
                style={{ opacity: 0.8, cursor: 'not-allowed' }}
              />
            </div>

            <div className="setup-field" style={{ marginBottom: '1rem' }}>
              <label className="form-label">Curricular Subject</label>
              <select
                required
                value={editingAllocation.subjectCode}
                onChange={(e) => setEditingAllocation({ ...editingAllocation, subjectCode: e.target.value })}
                className="setup-select"
              >
                {model.subjects.map((s) => (
                  <option key={s.code} value={s.code}>{s.code} — {s.name}</option>
                ))}
              </select>
            </div>

            <div className="setup-field" style={{ marginBottom: '1rem' }}>
              <label className="form-label">Assigned Faculty Member</label>
              <select
                required
                value={editingAllocation.teacherCode}
                onChange={(e) => setEditingAllocation({ ...editingAllocation, teacherCode: e.target.value })}
                className="setup-select"
              >
                {model.professors.map((p) => (
                  <option key={p.code} value={p.code}>{p.code} — {p.name}</option>
                ))}
              </select>
            </div>

            <div className="setup-field" style={{ marginBottom: '1.5rem' }}>
              <label className="form-label">Weekly Periods / Lectures</label>
              <input
                type="number"
                min={1}
                max={12}
                required
                value={editingAllocation.weekly}
                onChange={(e) => setEditingAllocation({ ...editingAllocation, weekly: parseInt(e.target.value, 10) || 1 })}
                className="setup-input"
              />
              <span className="form-hint">Number of periods this subject is taught each week (1–12).</span>
            </div>

            <div className="modal-actions-bar">
              <Button type="button" variant="ghost" size="md" onClick={() => setEditingAllocation(null)}>
                Cancel
              </Button>
              <Button type="submit" variant="primary" size="md" leftIcon={<Check size={14} />}>
                Apply & Update
              </Button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
};

export default SchoolSetupView;
