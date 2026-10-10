import React from 'react';
import { LayoutDashboard, SlidersHorizontal, Calendar, ShieldCheck, Terminal, Layers } from 'lucide-react';
import { Badge } from '../common/Badge';
import './layout.css';

export type ActiveNavView = 'command-center' | 'school-setup' | 'timetable-studio';

export interface SidebarProps {
  activeView: ActiveNavView;
  onSelectView: (view: ActiveNavView) => void;
  solverAvailable: boolean;
}

export const Sidebar: React.FC<SidebarProps> = ({
  activeView,
  onSelectView,
  solverAvailable,
}) => {
  return (
    <aside className="app-sidebar" aria-label="Main Application Navigation">
      <div className="app-sidebar__section-title">Core Operations</div>
      <nav className="app-sidebar__nav">
        {/* 1. Command Center */}
        <button
          className={`app-sidebar__item ${activeView === 'command-center' ? 'is-active' : ''}`}
          onClick={() => onSelectView('command-center')}
          aria-current={activeView === 'command-center' ? 'page' : undefined}
        >
          <div className="app-sidebar__item-icon">
            <LayoutDashboard size={18} />
          </div>
          <div className="app-sidebar__item-content">
            <span className="app-sidebar__item-label">Command Center</span>
            <span className="app-sidebar__item-sub">Pre-flight & Workspace</span>
          </div>
          <Badge variant="jade" size="sm">Hub</Badge>
        </button>

        {/* 2. School Setup & Inputs Workspace */}
        <button
          className={`app-sidebar__item ${activeView === 'school-setup' ? 'is-active' : ''}`}
          onClick={() => onSelectView('school-setup')}
          aria-current={activeView === 'school-setup' ? 'page' : undefined}
        >
          <div className="app-sidebar__item-icon">
            <SlidersHorizontal size={18} />
          </div>
          <div className="app-sidebar__item-content">
            <span className="app-sidebar__item-label">School Setup</span>
            <span className="app-sidebar__item-sub">Inputs & Constraints</span>
          </div>
          <Badge variant="terracotta" size="sm">Config</Badge>
        </button>

        {/* 3. Timetable Studio */}
        <button
          className={`app-sidebar__item ${activeView === 'timetable-studio' ? 'is-active' : ''}`}
          onClick={() => onSelectView('timetable-studio')}
          aria-current={activeView === 'timetable-studio' ? 'page' : undefined}
        >
          <div className="app-sidebar__item-icon">
            <Calendar size={18} />
          </div>
          <div className="app-sidebar__item-content">
            <span className="app-sidebar__item-label">Timetable Studio</span>
            <span className="app-sidebar__item-sub">2D Grid & Export Studio</span>
          </div>
          <Badge variant="gold" size="sm">Studio</Badge>
        </button>
      </nav>

      {/* Engine Status Block */}
      <div className="app-sidebar__footer">
        <div className="app-sidebar__system-card">
          <div className="app-sidebar__system-card-header">
            <ShieldCheck size={14} className="text-jade" />
            <span>Constraints Axioms</span>
          </div>
          <p className="app-sidebar__system-card-text">
            H1–H10 Hard Satisfaction + S1–S6 Soft Objectives Enforced
          </p>
          <div className="app-sidebar__system-tags">
            <span className="app-sidebar__pill">
              <Terminal size={10} /> CP-SAT Solver
            </span>
            <span className="app-sidebar__pill">
              <Layers size={10} /> {solverAvailable ? 'Online' : 'Standby'}
            </span>
          </div>
        </div>
      </div>
    </aside>
  );
};
