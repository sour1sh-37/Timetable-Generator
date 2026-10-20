import React from 'react';
import {
  LayoutDashboard,
  Calendar,
  Layers,
  BookOpen,
  Users,
  Grid,
  Clock,
  Shield,
  Sliders,
  SlidersHorizontal,
  Sparkles,
  CheckCircle2,
} from 'lucide-react';
import './layout.css';

export type ActiveNavView = 'home' | 'school-setup' | 'timetable-rules' | 'generate' | 'timetable-studio' | 'command-center';
export type SetupTab = 'structure' | 'subjects' | 'faculty' | 'allocations' | 'bell' | 'constraints' | 'review';
export type RulesTab = 'bell' | 'hard' | 'soft';

export interface SidebarProps {
  activeView: ActiveNavView;
  activeSetupTab?: SetupTab;
  activeRulesTab?: RulesTab;
  onSelectView: (view: ActiveNavView, setupTab?: SetupTab, rulesTab?: RulesTab) => void;
  solverAvailable: boolean;
  isSolving?: boolean;
  hasTimetable?: boolean;
}

export const Sidebar: React.FC<SidebarProps> = ({
  activeView,
  activeSetupTab = 'structure',
  activeRulesTab = 'bell',
  onSelectView,
  solverAvailable,
  isSolving,
  hasTimetable,
}) => {
  const isHomeActive = activeView === 'home' || activeView === 'command-center';
  const isSetupActive = activeView === 'school-setup';
  const isRulesActive = activeView === 'timetable-rules';
  const isGenerateActive = activeView === 'generate';
  const isStudioActive = activeView === 'timetable-studio';

  return (
    <aside className="app-sidebar" aria-label="Main Application Navigation">
      {/* 1. Main Navigation Group */}
      <div className="app-sidebar__section-title">Navigation</div>
      <nav className="app-sidebar__nav">
        {/* Destination A: Home */}
        <button
          type="button"
          className={`app-sidebar__item ${isHomeActive ? 'is-active' : ''}`}
          onClick={() => onSelectView('home')}
          aria-current={isHomeActive ? 'page' : undefined}
        >
          <div className="app-sidebar__item-icon">
            <LayoutDashboard size={18} />
          </div>
          <div className="app-sidebar__item-content">
            <span className="app-sidebar__item-label">Home</span>
          </div>
        </button>

        {/* Destination B: School Setup */}
        <button
          type="button"
          className={`app-sidebar__item ${isSetupActive ? 'is-active' : ''}`}
          onClick={() => onSelectView('school-setup', 'structure')}
          aria-current={isSetupActive ? 'page' : undefined}
        >
          <div className="app-sidebar__item-icon">
            <Layers size={18} />
          </div>
          <div className="app-sidebar__item-content">
            <span className="app-sidebar__item-label">School Setup</span>
          </div>
        </button>

        {/* Sub-items for School Setup (visible when School Setup is active) */}
        {isSetupActive && (
          <div className="app-sidebar__sub-nav">
            <button
              type="button"
              className={`app-sidebar__sub-item ${activeSetupTab === 'structure' ? 'is-active' : ''}`}
              onClick={() => onSelectView('school-setup', 'structure')}
            >
              <Layers size={13} />
              <span>Classes & Divisions</span>
            </button>

            <button
              type="button"
              className={`app-sidebar__sub-item ${activeSetupTab === 'subjects' ? 'is-active' : ''}`}
              onClick={() => onSelectView('school-setup', 'subjects')}
            >
              <BookOpen size={13} />
              <span>Curricular Subjects</span>
            </button>

            <button
              type="button"
              className={`app-sidebar__sub-item ${activeSetupTab === 'faculty' ? 'is-active' : ''}`}
              onClick={() => onSelectView('school-setup', 'faculty')}
            >
              <Users size={13} />
              <span>Faculty & Teachers</span>
            </button>

            <button
              type="button"
              className={`app-sidebar__sub-item ${activeSetupTab === 'allocations' ? 'is-active' : ''}`}
              onClick={() => onSelectView('school-setup', 'allocations')}
            >
              <Grid size={13} />
              <span>Weekly Allocations</span>
            </button>
          </div>
        )}

        {/* Destination C: Timetable Rules */}
        <button
          type="button"
          className={`app-sidebar__item ${isRulesActive ? 'is-active' : ''}`}
          onClick={() => onSelectView('timetable-rules', undefined, 'bell')}
          aria-current={isRulesActive ? 'page' : undefined}
        >
          <div className="app-sidebar__item-icon">
            <Sliders size={18} />
          </div>
          <div className="app-sidebar__item-content">
            <span className="app-sidebar__item-label">Timetable Rules</span>
          </div>
        </button>

        {/* Sub-items for Timetable Rules */}
        {isRulesActive && (
          <div className="app-sidebar__sub-nav">
            <button
              type="button"
              className={`app-sidebar__sub-item ${activeRulesTab === 'bell' ? 'is-active' : ''}`}
              onClick={() => onSelectView('timetable-rules', undefined, 'bell')}
            >
              <Clock size={13} />
              <span>Timings & Bell Schedule</span>
            </button>

            <button
              type="button"
              className={`app-sidebar__sub-item ${activeRulesTab === 'hard' ? 'is-active' : ''}`}
              onClick={() => onSelectView('timetable-rules', undefined, 'hard')}
            >
              <Shield size={13} />
              <span>Hard Constraints (H1–H10)</span>
            </button>

            <button
              type="button"
              className={`app-sidebar__sub-item ${activeRulesTab === 'soft' ? 'is-active' : ''}`}
              onClick={() => onSelectView('timetable-rules', undefined, 'soft')}
            >
              <SlidersHorizontal size={13} />
              <span>Soft Objectives (S1–S6)</span>
            </button>
          </div>
        )}

        {/* Destination D: Generate Timetable */}
        <button
          type="button"
          className={`app-sidebar__item ${isGenerateActive ? 'is-active' : ''}`}
          onClick={() => onSelectView('generate')}
          aria-current={isGenerateActive ? 'page' : undefined}
        >
          <div className="app-sidebar__item-icon">
            <Sparkles size={18} />
          </div>
          <div className="app-sidebar__item-content">
            <span className="app-sidebar__item-label">Generate Timetable</span>
          </div>
          {isSolving && (
            <span className="app-sidebar__tag-pill is-busy" title="Solver in progress">
              Solving...
            </span>
          )}
        </button>

        {/* Destination E: Timetable Studio */}
        <button
          type="button"
          className={`app-sidebar__item ${isStudioActive ? 'is-active' : ''}`}
          onClick={() => onSelectView('timetable-studio')}
          aria-current={isStudioActive ? 'page' : undefined}
        >
          <div className="app-sidebar__item-icon">
            <Calendar size={18} />
          </div>
          <div className="app-sidebar__item-content">
            <span className="app-sidebar__item-label">Timetable Studio</span>
          </div>
          {hasTimetable && (
            <span className="app-sidebar__tag-pill is-success" title="Schedule ready">
              Ready
            </span>
          )}
        </button>
      </nav>

      {/* Sidebar Footer Info */}
      <div className="app-sidebar__footer">
        <div className="app-sidebar__info-box">
          <div className="app-sidebar__info-header">
            <CheckCircle2 size={13} className="text-jade" />
            <span>Academic Year 2026–27</span>
          </div>
          <p className="app-sidebar__info-text">
            SIES High School & Junior College
          </p>
          <div className="app-sidebar__engine-tag">
            <span>Optimizer Engine:</span>
            <strong>{solverAvailable ? 'Online' : 'Standby'}</strong>
          </div>
        </div>
      </div>
    </aside>
  );
};

export default Sidebar;
