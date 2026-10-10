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
} from 'lucide-react';
import { Button, Badge, LoadingSpinner } from '../components/common';
import type { TimetableApiClient } from '../api/client';
import type {
  SetupModelDto,
  SetupValidationDto,
  SubjectSetupItem,
  ProfessorSetupItem,
} from '../api/types';
import './setup.css';

interface SchoolSetupViewProps {
  client: TimetableApiClient;
  onNavigateToStudio: () => void;
}

type SetupTab = 'structure' | 'subjects' | 'faculty' | 'allocations' | 'bell' | 'review';

export const SchoolSetupView: React.FC<SchoolSetupViewProps> = ({
  client,
  onNavigateToStudio,
}) => {
  const [activeTab, setActiveTab] = useState<SetupTab>('structure');
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

  // Load Model from Server
  const loadSetupModel = useCallback(async () => {
    setIsLoading(true);
    setFeedbackMsg(null);
    try {
      const res = await client.getSetupModel();
      setModel(res.model);
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
    setIsSaving(true);
    setFeedbackMsg(null);
    try {
      const res = await client.updateSetupModel(model);
      setModel(res.model);
      setValidation(res.validation);
      setIsDirty(false);
      setFeedbackMsg({ text: 'School configuration successfully saved to active memory!', type: 'success' });
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
      setModel(res.model);
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

  // 5. Bell Schedule Handlers
  const handleToggleDay = (day: string) => {
    if (!model) return;
    const currentDays = model.bellSchedule.days;
    let nextDays: string[];
    if (currentDays.includes(day)) {
      if (currentDays.length <= 1) {
        alert('Timetable requires at least one working day.');
        return;
      }
      nextDays = currentDays.filter((d) => d !== day);
    } else {
      nextDays = [...currentDays, day];
    }
    setModel({
      ...model,
      bellSchedule: {
        ...model.bellSchedule,
        days: nextDays,
        saturdayEnabled: nextDays.includes('SATURDAY'),
      },
    });
    setIsDirty(true);
  };

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
            variant="primary"
            size="sm"
            onClick={onNavigateToStudio}
            style={{ backgroundColor: 'var(--color-jade)' }}
          >
            Studio <ArrowRight size={14} />
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
      <div className="setup-nav-tabs">
        <button
          className={`setup-nav-tab ${activeTab === 'structure' ? 'is-active' : ''}`}
          onClick={() => setActiveTab('structure')}
        >
          <span className="setup-nav-tab__num">1</span>
          <Layers size={14} />
          <span>Academic Structure</span>
        </button>

        <button
          className={`setup-nav-tab ${activeTab === 'subjects' ? 'is-active' : ''}`}
          onClick={() => setActiveTab('subjects')}
        >
          <span className="setup-nav-tab__num">2</span>
          <BookOpen size={14} />
          <span>Subjects & Categories ({model.subjects.length})</span>
        </button>

        <button
          className={`setup-nav-tab ${activeTab === 'faculty' ? 'is-active' : ''}`}
          onClick={() => setActiveTab('faculty')}
        >
          <span className="setup-nav-tab__num">3</span>
          <Users size={14} />
          <span>Faculty & Class Teachers ({model.professors.length})</span>
        </button>

        <button
          className={`setup-nav-tab ${activeTab === 'allocations' ? 'is-active' : ''}`}
          onClick={() => setActiveTab('allocations')}
        >
          <span className="setup-nav-tab__num">4</span>
          <Grid size={14} />
          <span>Weekly Allocations</span>
        </button>

        <button
          className={`setup-nav-tab ${activeTab === 'bell' ? 'is-active' : ''}`}
          onClick={() => setActiveTab('bell')}
        >
          <span className="setup-nav-tab__num">5</span>
          <Clock size={14} />
          <span>Bell Schedule & Constraints</span>
        </button>

        <button
          className={`setup-nav-tab ${activeTab === 'review' ? 'is-active' : ''}`}
          onClick={() => setActiveTab('review')}
        >
          <span className="setup-nav-tab__num">6</span>
          <CheckCircle2 size={14} />
          <span>Review & Validation</span>
          {validation && (
            <Badge variant={validation.isValid ? 'jade' : 'crimson'} size="sm">
              {validation.isValid ? 'Feasible' : `${validation.errors.length} Issues`}
            </Badge>
          )}
        </button>
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
                      <td style={{ textAlign: 'right' }}>
                        <button
                          onClick={() => handleDeleteSubject(subj.code)}
                          style={{ background: 'none', border: 'none', color: '#ff7777', cursor: 'pointer' }}
                          title="Delete Subject"
                        >
                          <Trash2 size={16} />
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
                      <td style={{ textAlign: 'right' }}>
                        <button
                          onClick={() => handleDeleteProfessor(prof.code)}
                          style={{ background: 'none', border: 'none', color: '#ff7777', cursor: 'pointer' }}
                          title="Delete Faculty"
                        >
                          <Trash2 size={16} />
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
                        <td style={{ textAlign: 'right' }}>
                          <button
                            onClick={() => handleRemoveAllocation(item.teacherCode, item.subjectCode)}
                            style={{ background: 'none', border: 'none', color: '#ff7777', cursor: 'pointer' }}
                            title="Remove Allocation"
                          >
                            <Trash2 size={16} />
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

      {/* Tab 5: Bell Schedule & Constraints */}
      {activeTab === 'bell' && (
        <div className="setup-step-panel">
          <div className="setup-card">
            <div className="setup-card__header">
              <div>
                <h2 className="setup-card__title">
                  <Clock size={18} className="text-terracotta" /> Bell Schedule, Working Week & Recess
                </h2>
                <p className="setup-card__subtitle">
                  Configure the working days, daily period count, recess placement, and hard constraint rules.
                </p>
              </div>
            </div>

            <h3 style={{ fontSize: 'var(--text-sm)', color: 'var(--color-ivory)', marginBottom: '0.75rem' }}>
              Working Days Selection
            </h3>
            <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginBottom: '1.5rem' }}>
              {['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'].map((day) => {
                const isSelected = model.bellSchedule.days.includes(day);
                return (
                  <button
                    key={day}
                    onClick={() => handleToggleDay(day)}
                    style={{
                      padding: '0.5rem 0.85rem',
                      borderRadius: 'var(--radius-md)',
                      border: isSelected ? '1px solid var(--color-terracotta)' : '1px solid var(--surface-border)',
                      backgroundColor: isSelected ? 'rgba(150, 63, 43, 0.2)' : 'var(--surface-ground)',
                      color: isSelected ? 'var(--color-ivory)' : 'var(--color-ivory-dim)',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.4rem',
                      fontSize: 'var(--text-xs)',
                      fontWeight: isSelected ? 600 : 400,
                    }}
                  >
                    {isSelected && <Check size={14} className="text-terracotta" />}
                    <span>{day}</span>
                  </button>
                );
              })}
            </div>

            <div className="setup-form-grid" style={{ marginBottom: '1.5rem' }}>
              <div className="setup-field">
                <label>Teaching Periods per Day</label>
                <input
                  type="number"
                  min={4}
                  max={10}
                  value={model.bellSchedule.periodsPerDay}
                  onChange={(e) => {
                    const ppd = parseInt(e.target.value, 10) || 8;
                    setModel({
                      ...model,
                      bellSchedule: {
                        ...model.bellSchedule,
                        periodsPerDay: ppd,
                      },
                    });
                    setIsDirty(true);
                  }}
                  className="setup-input"
                />
              </div>

              <div className="setup-field">
                <label>Recess Placement (After Period #)</label>
                <input
                  type="number"
                  min={1}
                  max={model.bellSchedule.periodsPerDay - 1}
                  value={model.bellSchedule.recessAfterPeriod}
                  onChange={(e) => {
                    const rec = parseInt(e.target.value, 10) || 4;
                    setModel({
                      ...model,
                      bellSchedule: {
                        ...model.bellSchedule,
                        recessAfterPeriod: rec,
                      },
                    });
                    setIsDirty(true);
                  }}
                  className="setup-input"
                />
              </div>

              <div className="setup-field">
                <label>Weekly Required Periods per Class</label>
                <input
                  type="number"
                  min={1}
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
                />
              </div>
            </div>

            <h3 style={{ fontSize: 'var(--text-sm)', color: 'var(--color-ivory)', marginBottom: '0.75rem' }}>
              Hard Constraint Axioms Enforced (H1–H10)
            </h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '0.75rem' }}>
              <div style={{ padding: '0.75rem', backgroundColor: 'var(--surface-ground)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--surface-border)' }}>
                <strong style={{ color: 'var(--color-gold)', display: 'block', fontSize: 'var(--text-xs)' }}>H1: Class Teacher First Period</strong>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-ivory-dim)' }}>Designated class teachers fill Period 1 slots.</span>
              </div>
              <div style={{ padding: '0.75rem', backgroundColor: 'var(--surface-ground)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--surface-border)' }}>
                <strong style={{ color: 'var(--color-gold)', display: 'block', fontSize: 'var(--text-xs)' }}>H2: Consecutive Lecture Limits</strong>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-ivory-dim)' }}>Max 2 for load ≤ 30; Max 3 for load &gt; 30.</span>
              </div>
              <div style={{ padding: '0.75rem', backgroundColor: 'var(--surface-ground)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--surface-border)' }}>
                <strong style={{ color: 'var(--color-gold)', display: 'block', fontSize: 'var(--text-xs)' }}>H5: Computer Lab Exclusivity</strong>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-ivory-dim)' }}>No simultaneous computer lab sessions school-wide.</span>
              </div>
              <div style={{ padding: '0.75rem', backgroundColor: 'var(--surface-ground)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--surface-border)' }}>
                <strong style={{ color: 'var(--color-gold)', display: 'block', fontSize: 'var(--text-xs)' }}>H6 & H7: Physical Education Rules</strong>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-ivory-dim)' }}>P.E. strictly prohibited in Period 1 and immediately post-recess.</span>
              </div>
              <div style={{ padding: '0.75rem', backgroundColor: 'var(--surface-ground)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--surface-border)' }}>
                <strong style={{ color: 'var(--color-gold)', display: 'block', fontSize: 'var(--text-xs)' }}>H8: Daily Activity Cap</strong>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-ivory-dim)' }}>At most 2 activity/sports periods per class per day.</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Tab 6: Review & Validation */}
      {activeTab === 'review' && (
        <div className="setup-step-panel">
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
                  <div className="setup-validation-box" style={{ backgroundColor: 'rgba(214, 180, 119, 0.1)', border: '1px solid rgba(214, 180, 119, 0.3)' }}>
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
    </div>
  );
};

export default SchoolSetupView;
