export type BackendLifecycleState =
  | 'STARTING'
  | 'ONLINE'
  | 'SHUTTING_DOWN'
  | 'OFFLINE'
  | 'RECOVERING'
  | 'FAILED';

export interface BackendStatusDto {
  state: BackendLifecycleState;
  port: number;
  url: string;
  error?: string | null;
}

export interface JvmStats {
  version: string;
  vendor: string;
  pid: number;
  uptimeMs: number;
  availableProcessors: number;
  totalMemoryBytes: number;
  freeMemoryBytes: number;
  maxMemoryBytes: number;
}

export interface SolverInfo {
  engine: string;
  version: string;
  status: string;
  authoritative: boolean;
}

export interface HealthResponse {
  status: 'UP' | 'DOWN';
  service: string;
  apiVersion: string;
  port: number;
  timestamp: number;
  jvm: JvmStats;
  solver: SolverInfo;
}

export interface ConfigResponse {
  status: 'SUCCESS' | 'ERROR';
  configPath: string;
  loadedFromFile: boolean;
  schoolName: string;
  academicYear: string;
  days: string[];
  periodsPerDay: number;
  recessAfterPeriod: number;
  weeklyRequiredPeriodsPerClass: number;
  dailyPeriods: Record<string, number>;
  coreAcademicSubjects?: string[];
  activitySubjects?: string[];
  specialCurricularSubjects?: string[];
  labResources?: Record<string, string>;
  outputDir: string;
}

export interface WorkspaceDetection {
  isValid: boolean;
  folder: string | null;
  standardwiseFile: string | null;
  teacherwiseFile: string | null;
  classTeachersFile: string | null;
  constraintsFile: string | null;
  statusMessage: string;
  missingFiles: string[];
}

export interface WorkspaceDetectResponse {
  status: 'SUCCESS' | 'ERROR';
  detection: WorkspaceDetection;
}

export interface ModelPreset {
  loaded: boolean;
  name: string;
  startStandard: number;
  endStandard: number;
  standards: number[];
  divisions: Record<number, number>;
  totalClasses: number;
  totalSubjects: number;
  totalProfessors: number;
  weeklyRequiredPeriodsPerClass: number;
  recessAfterPeriod: number;
}

export interface ModelPresetResponse {
  status: 'SUCCESS' | 'ERROR';
  preset: ModelPreset;
}

export interface ModelValidateResponse {
  status: 'SUCCESS' | 'ERROR';
  isValid: boolean;
  statusMessage: string;
  missingFiles: string[];
}

// =========================================================================
// Phase 3 Solver & Studio Types
// =========================================================================

export type SolverState = 'IDLE' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface SolverJobStatus {
  status: SolverState;
  jobId: string | null;
  step: number;
  totalSteps: number;
  percent: number;
  message: string;
  elapsedSeconds: number;
  isSuccess: boolean;
  error: string | null;
}

export interface TeacherSummaryItem {
  code: string;
  name: string;
  totalWorkload: number;
  isFreeTeacher: boolean;
}

export interface OutputFilesPaths {
  excelPath: string | null;
  studentCsvPath: string | null;
  teacherCsvPath: string | null;
  pdfDir: string | null;
}

export interface ScheduleSummaryResponse {
  status: 'SUCCESS' | 'ERROR';
  available: boolean;
  isStale?: boolean;
  message?: string;
  solveTimeSeconds?: number;
  qualityIndex?: number;
  allHardConstraintsPassed?: boolean;
  totalHardViolations?: number;
  totalSoftPenalty?: number;
  totalClasses?: number;
  classes?: string[];
  totalTeachers?: number;
  teachers?: TeacherSummaryItem[];
  outputFiles?: OutputFilesPaths;
}

export type SubjectCategory = 'CORE_ACADEMIC' | 'ACTIVITY' | 'SPECIAL_CURRICULAR' | 'LAB' | 'OTHER';

export interface TimetableCell {
  mainText: string;
  subText: string;
  fullText: string;
  isRecess: boolean;
  isFree: boolean;
  category: SubjectCategory;
}

export interface TimetableGrid {
  title: string;
  subtitle: string;
  headers: string[];
  rowLabels: string[];
  cells: TimetableCell[][];
}

export interface ClassGridResponse {
  status: 'SUCCESS' | 'ERROR';
  classId: string;
  grid: TimetableGrid;
  error?: string;
}

export interface TeacherGridResponse {
  status: 'SUCCESS' | 'ERROR';
  teacherCode: string;
  grid: TimetableGrid;
  error?: string;
}

export interface ConstraintCheckItem {
  constraintId: string;
  name: string;
  type: 'HARD' | 'SOFT';
  status: 'PASS' | 'FAIL';
  violationCount: number;
  penaltyScore: number;
  details: string;
}

export interface ViolationDetailItem {
  constraintId: string;
  constraintName: string;
  severity: 'HARD' | 'SOFT';
  day: string;
  period: string;
  className: string;
  teacher: string;
  subject: string;
  resource: string;
  conflictingAssignments: string;
  reason: string;
  penaltyScore: number;
}

export interface ScheduleValidationResult {
  allHardConstraintsPassed: boolean;
  totalHardViolations: number;
  totalSoftPenalty: number;
  overallQualityIndex: number;
  checks: ConstraintCheckItem[];
  violations: ViolationDetailItem[];
}

export interface ScheduleValidationResponse {
  status: 'SUCCESS' | 'ERROR';
  validation: ScheduleValidationResult;
  error?: string;
}

// =========================================================================
// School Setup & Inputs Workspace Types (Batch 1)
// =========================================================================

export interface SubjectSetupItem {
  code: string;
  name: string;
  category: 'CORE_ACADEMIC' | 'ACTIVITY' | 'SPECIAL_CURRICULAR';
  isLab: boolean;
  weeklyPeriods?: number;
}

export interface AllocationSetupItem {
  subjectCode: string;
  subjectName?: string;
  weeklyLectures: number;
  classes: string[];
}

export interface ProfessorSetupItem {
  code: string;
  name: string;
  freeTeacher: boolean;
  classTeacherOf: string | null;
  totalWorkload: number;
  allocations: AllocationSetupItem[];
}

export interface BellScheduleSetup {
  days: string[];
  periodsPerDay: number;
  weeklyRequiredPeriodsPerClass: number;
  recessAfterPeriod: number;
  dailyPeriods: Record<string, number>;
  recessOverrides: Record<string, number>;
  dailyRecessEnabled?: Record<string, boolean>;
  dailyRecessAfterPeriod?: Record<string, number>;
  dailyRecessDurations?: Record<string, number>;
  saturdayEnabled: boolean;
  schoolStartTime?: string;
  saturdayStartTime?: string;
  defaultPeriodDuration?: number;
  passingTime?: number;
  recessDuration?: number;
  shortBreakEnabled?: boolean;
  shortBreakAfterPeriod?: number;
  shortBreakDuration?: number;
  periodDurationOverrides?: Record<string, number>;
  periodTimeOverrides?: Record<string, string>;
  computedTimings?: {
    weekday?: Record<string, string>;
    saturday?: Record<string, string>;
    recessWeekday?: string;
    recessSaturday?: string;
  };
}

export interface HardConstraintConfig {
  id: string;
  name: string;
  description: string;
  mandatory: boolean;
  enabled: boolean;
  maxContinuousLectures?: number;
  maxConcurrentLabClasses?: number;
  prohibitedPeriod?: number;
  maxActivityPeriodsPerDay?: number;
}

export interface SoftConstraintConfig {
  id: string;
  name: string;
  description: string;
  enabled: boolean;
  weight: number;
  defaultWeight: number;
}

export interface ConstraintsSetupDto {
  hardConstraints: Record<string, HardConstraintConfig>;
  softConstraints: Record<string, SoftConstraintConfig>;
}

export interface ConstraintsResponse {
  status: 'SUCCESS' | 'ERROR';
  message?: string;
  error?: string;
  constraints: ConstraintsSetupDto;
}

export interface SetupModelDto {
  startStandard: number;
  endStandard: number;
  standardDivisions: Record<string, number>;
  classes: string[];
  subjects: SubjectSetupItem[];
  professors: ProfessorSetupItem[];
  bellSchedule: BellScheduleSetup;
  constraints?: ConstraintsSetupDto;
}

export interface SetupValidationDto {
  isValid: boolean;
  errors: string[];
  warnings: string[];
  notices: string[];
  totalAvailablePeriods: number;
  weeklyRequiredPeriods: number;
  totalClasses: number;
  totalProfessors: number;
  totalSubjects: number;
}

export interface SetupModelResponse {
  status: 'SUCCESS' | 'ERROR';
  message?: string;
  model: SetupModelDto;
  validation: SetupValidationDto;
}

export interface SetupValidateResponse {
  status: 'SUCCESS' | 'ERROR';
  validation: SetupValidationDto;
}

// =========================================================================
// Schedule Manual Editing Types (Batch 2)
// =========================================================================

export interface ScheduleEditSlot {
  day: string;
  period: number; // 1-indexed period number
}

export interface ScheduleEditRequest {
  classId: string;
  source: ScheduleEditSlot;
  target: ScheduleEditSlot;
  operation?: 'SWAP' | 'MOVE';
}

export interface ScheduleEditValidateResponse {
  status: 'SUCCESS' | 'ERROR';
  isValid: boolean;
  hardViolations: number;
  errors: string[];
  warnings: string[];
  qualityScore: number;
}

export interface ScheduleEditApplyResponse {
  status: 'SUCCESS' | 'REJECTED' | 'ERROR';
  message?: string;
  error?: string;
  violations?: string[];
  qualityScore?: number;
}

