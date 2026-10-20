# SIES Timetable Generator

A high-performance, constraint-programming-based school timetable management and optimization platform developed specifically for **SIES APJ School, Matunga**. 

Designed for reliable local desktop deployment, the application couples an authoritative **Java 21 + Google OR-Tools CP-SAT** scheduling engine with a modern **React 19, TypeScript, and Tauri v2** Windows desktop shell featuring a full-spectrum custom theme system.

---

## 1. Project Overview

School timetabling is a high-dimensional combinatorial optimization problem. Coordinating dozens of class divisions across dozens of faculty members, specialized computer laboratories, differing weekly subject quotas, consecutive lecture ceilings, and day-specific bell schedules manually invariably results in teacher double-bookings, room collisions, uneven syllabus delivery, and teacher burnout.

**SIES Timetable Generator** solves this challenge by formulating institutional scheduling policies into an exact mathematical constraint satisfaction and optimization model.

- **Authoritative Mathematical Formulation**: Formulates physical invariants as strict CP-SAT hard constraints (H1–H10) and pedagogical preferences as weighted soft objectives (S1–S6).
- **Structured Administrative Workflow**: Reorganized into **5 primary destinations** designed around how school administrators actually work (Home, School Setup, Timetable Rules, Generate Timetable, Timetable Studio).
- **Custom Appearance System**: Full-spectrum 2D saturation/brightness colour picker, hue slider, and independent Light, Dark, and System display modes.
- **Interactive Schedule Studio**: Visual Day $\times$ Period weekly grids for every class and teacher, in-place manual slot swapping with live constraint verification, and full undo/redo history.
- **Publication-Ready Exporters**: One-click generation of Excel master workbooks (`.xlsx`), individual printable PDF schedules (`.pdf`), and detailed CSV audit reports.
- **100% Local & Self-Contained**: Runs entirely offline on the desktop with a private bundled JRE and native OR-Tools DLLs. Zero cloud or telemetry dependencies.

---

## 2. Application Navigation & Workspaces

The application is structured into **five primary destinations**:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        SIES Timetable Generator                        │
├────────────────────────────────────────────────────────────────────────┤
│ 1. Home               • 3 Core Guidance Questions & Task Shortcuts     │
│ 2. School Setup       • Classes, Subjects, Faculty, Allocations        │
│ 3. Timetable Rules    • Bell Schedule, Hard Invariants, Soft Weights   │
│ 4. Generate Timetable • 4-Step Guided Solver Workflow & Live Telemetry │
│ 5. Timetable Studio   • Inspection Matrix & Excel/PDF Roster Exporters │
└────────────────────────────────────────────────────────────────────────┘
```

### Destination 1: Home (Administrative Hub)
Addresses the three primary operational questions for school staff:
1. **Is the timetable ready to generate?** Visual readiness audit card (Passed / Blocked).
2. **What prevents generation?** Explicit blocker display showing exact deficits (0 blocking issues when ready).
3. **What should I do next?** Context-aware recommended action button ("Generate Timetable", "Open Setup", or "Inspect in Studio").
- **Task Shortcuts**: Direct cards to School Setup, Timetable Rules, Generate Timetable, and Timetable Studio.
- **Active Schedule Status**: Prominent banner when a schedule is ready, showing conflict-free status and instant Excel/PDF download buttons.
- **School Capacity Metrics**: Live counts of active classes, teaching faculty, curricular subjects, and weekly periods.

### Destination 2: School Setup
Focused purely on school curriculum and faculty data:
- **Classes & Divisions**: Standards 5 to 10 with division capacity counters and division add/edit/delete.
- **Curricular Subjects**: Core academic subjects, laboratory sciences, and co-curricular activities.
- **Faculty & Teachers**: Teacher profiles, class mentor designations, workload limits, and free-teacher tags.
- **Weekly Allocations**: Class-by-class allocation matrix with subject-teacher-period assignments and quick navigation to Timetable Rules.

### Destination 3: Timetable Rules & Optimization Constraints
Dedicated configuration interface for bell timings and mathematical constraints:
- **School Timings & Bell Schedule**: Active working days (5-day or 6-day week), daily period counts (e.g., 8 on weekdays, 4 on Saturday), morning start times, standard lecture durations (default 35 mins), and Period 1 assembly overrides.
- **Day-Specific Recess Configuration**: Independent recess placement per working day with dedicated Saturday half-day toggle and validation protection.
- **Hard Constraints (H1–H10)**:
  - **Mandatory Invariants (Locked)**: H3 (Class Slot Coverage), H4 (Teacher Single-Booking), and H9 (Curriculum Allotment Fulfillment) cannot be disabled, guaranteeing schedule integrity.
  - **Configurable Rules**: H1 (Teacher Daily Max), H2 (Consecutive Lectures), H5 (Computer Lab Exclusivity), H6 (Prohibited PE Period), H7 (Class Teacher Placement), H8 (Daily Activity Limit), and H10 (Saturday Schedule Integrity).
- **Soft Objectives (S1–S6)**: Checkbox toggles and priority weight sliders ($0 - 500$) with visual level badges (Off, Low, Med, High).
- **Timeline Preview**: Dynamic interactive preview visualizing daily period blocks and recess timings.

### Destination 4: Generate Timetable (Guided Workflow)
A structured 4-step wizard for timetable generation:
1. **Pre-flight Readiness Audit**: Live status checks across 4 foundational pillars (Classes, Faculty, Bell Schedule, Rules).
2. **Blocking Issues Banner**: Detects any feasibility deficit and provides direct deep-link buttons ("Fix in School Setup", "Fix in Timetable Rules") to jump straight to the source of the problem.
3. **CP-SAT Solver Execution**: Real-time progress bar ($0\% - 100\%$), phase description, elapsed time counter, and instant cancel button.
4. **Results & Publication**: Confirms zero hard constraint violations, displays soft objective scores, and provides direct links to open Timetable Studio or download Excel/PDF files.

### Destination 5: Timetable Studio
Interactive schedule inspection and publishing workspace:
- **Multi-View Modes**: Filter and view schedules by Class Division, Faculty Member, or School Master Grid.
- **Color-Coded Subject Badges**: Immediate visual distinction between Core Academics, Lab Practicals, and Co-curricular Activities.
- **Manual Slot Swapping**: Click to select and swap lecture slots with real-time constraint validation and conflict indicators.
- **Undo / Redo History**: Complete modification stack allowing administrators to revert or re-apply schedule adjustments.
- **Multi-Format Export Suite**: Instant downloads for Excel (`.xlsx`), PDF (`.pdf`), and CSV formats.

---

## 3. Custom Appearance & Theme System

The application features a built-in theme manager and full-spectrum color picker:

- **Full-Spectrum Color Picker**:
  - Interactive 2D Saturation / Value gradient canvas with smooth drag and touch support.
  - Rainbow Hue slider ($0^\circ - 360^\circ$).
  - Hex input field with live validation and bidirectional synchronization.
  - Institutional preset swatches: SIES Crimson (`#990000`), Royal Navy (`#1e3a8a`), Forest Emerald (`#047857`), Saffron Amber (`#d97706`), Imperial Purple (`#6b21a8`), and Deep Teal (`#0f766e`).
- **Mathematical Color Engine**: Floating-point HSV $\leftrightarrow$ RGB $\leftrightarrow$ HEX mathematical conversion engine with luminance calculation and automatic derivative palette creation (`--color-brand-hover`, `--color-brand-active`, `--color-brand-subtle`, `--color-brand-border`, `--focus-ring`).
- **Independent Display Modes**: Supports `Light`, `Dark`, and `System` display modes.
- **Zero-Flash Startup**: Theme preferences are loaded and injected onto the root document element before DOM render, preventing any flash of unstyled content.
- **Persistent Storage**: Saved automatically in `localStorage` across application restarts.

---

## 4. Scheduling Constraints Model

### Hard Constraints (H1–H10)
Hard constraints must be satisfied for a mathematically valid schedule:

| ID | Constraint Name | Status | Description |
| :---: | :--- | :---: | :--- |
| **H1** | Daily Teacher Workload Limit | Configurable | Caps faculty teaching periods per day (default 7) to prevent burnout. |
| **H2** | Maximum Consecutive Lectures | Configurable | Limits continuous back-to-back lectures (default 2; heavy workloads allow 3). |
| **H3** | Class Slot Coverage & Integrity | **Mandatory (Locked)** | Guarantees exactly one instruction assignment per active class slot. |
| **H4** | Strict Teacher Single-Booking | **Mandatory (Locked)** | Prevents any faculty member or specialized room from being double-booked. |
| **H5** | Computer Lab Facility Exclusivity | Configurable | Restricts concurrent lab classes across the school to prevent room clashes. |
| **H6** | Prohibited PE Period | Configurable | Prohibits Physical Education during assembly / homeroom periods (default Period 1). |
| **H7** | Class Teacher Morning Placement | Configurable | Prefers assigning the designated class teacher during the morning block. |
| **H8** | Maximum Activity Periods Per Day | Configurable | Caps non-academic periods (Drawing, Craft, PE, Library) to at most 2 per day. |
| **H9** | Exact Allotment Fulfillment | **Mandatory (Locked)** | Guarantees every subject receives exactly its weekly lecture quota. |
| **H10** | Saturday Schedule Integrity | Configurable | Enforces Saturday half-day instructional caps and recess omission rules. |

### Soft Constraints (S1–S6)
Optimization objectives weighted during Phase 2 of the CP-SAT solver:

| ID | Objective Name | Default Weight | Optimization Goal |
| :---: | :--- | :---: | :--- |
| **S1** | Workload Uniformity Across Week | 100 | Balances faculty teaching periods evenly across working days. |
| **S2** | Minimize Faculty Idle Gaps | 80 | Minimizes unpaid idle waiting windows between morning and afternoon lectures. |
| **S3** | Teacher Period Preference Alignment | 50 | Encourages assigning faculty to their preferred instructional time blocks. |
| **S4** | Optimal Lab Session Placement | 40 | Avoids scheduling heavy practicals during the final period of the day. |
| **S5** | Balanced Distribution of Core Subjects | 30 | Spreads heavy subjects (Math, Science) throughout the week rather than clustered. |
| **S6** | Minimize Single-Period Work Days | 20 | Penalizes scheduling faculty to report to school for only one isolated period. |

---

## 5. System Architecture

The desktop application uses a decoupled, local desktop architecture where the Java backend serves as the authoritative source of truth:

```
┌────────────────────────────────────────────────────────┐
│               Windows Desktop Environment              │
│                                                        │
│  ┌─────────────────────────┐                           │
│  │  Tauri v2 Native Shell  │                           │
│  │   (Rust + WebView2)     │                           │
│  └───────────┬─────────────┘                           │
│              │ IPC / Local Loopback HTTP               │
│              ▼                                         │
│  ┌─────────────────────────┐    Win32 Job Object       │
│  │  Local Java HTTP Server │ ◄──────────────────────   │
│  │  (TimetableHttpServer)  │   (Guaranteed Termination │
│  └───────────┬─────────────┘    on App Close)          │
│              │                                         │
│              ▼                                         │
│  ┌─────────────────────────┐                           │
│  │  Authoritative Engine   │                           │
│  │  - OR-Tools CP-SAT      │                           │
│  │  - TimetableService     │                           │
│  │  - ScheduleValidator    │                           │
│  └───────────┬─────────────┘                           │
│              │                                         │
│              ▼                                         │
│  ┌─────────────────────────┐                           │
│  │  File Exporters         │                           │
│  │  (Excel, PDF, CSV)      │                           │
│  └─────────────────────────┘                           │
└────────────────────────────────────────────────────────┘
```

- **Java 21 Engine**: Authoritative implementation of constraint programming models, dual-source spreadsheet reconciliation, ScheduleValidator, and file exporters.
- **Embedded Loopback Daemon (`TimetableHttpServer`)**: Lightweight HTTP server bound strictly to loopback (`127.0.0.1:8080` with dynamic port fallback).
- **React 19 + TypeScript Frontend**: Modern responsive interface built with Vite and CSS custom properties adhering to school branding.
- **Tauri v2 Desktop Shell**: Manages the child Java backend process, monitors health handshakes, and binds the process to a Windows Job Object (`JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE`) to eliminate orphaned background processes.

---

## 6. Prerequisites

### For End Users
- **Operating System**: Windows 10 (64-bit) or Windows 11.
- **Dependencies**: None. The installer bundles a private, self-contained Java Runtime Environment (JRE) and all native OR-Tools dynamic link libraries (`.dll`).

### For Developers Building from Source
- **Java Development Kit (JDK)**: JDK 21 or higher (JDK 21 or JDK 25 recommended).
- **Apache Maven**: 3.9+ (or use the included `mvn` wrapper).
- **Node.js**: v20+ with npm.
- **Rust Toolchain**: 1.80+ with `x86_64-pc-windows-msvc` target.
- **Python**: 3.10+ (used by resource packaging scripts).
- **Visual Studio C++ Build Tools**: Required for compiling native Tauri Rust crates.

---

## 7. Local Development

You can run the backend and frontend separately during active development:

### Terminal 1: Launch Java Backend Daemon
```powershell
mvn clean compile
mvn exec:java -Dexec.mainClass="com.sies.timetable.Main" -Dexec.args="--server 8080"
```
Verify the backend is running by opening: `http://127.0.0.1:8080/api/health`

### Terminal 2: Launch React Development Server
```powershell
cd frontend
npm install
npm run dev
```
Open `http://localhost:5173` in your browser. The frontend will automatically detect and communicate with the backend daemon.

### Standalone Swing GUI (Legacy Mode)
To launch the original Java Swing application directly:
```powershell
mvn exec:java -Dexec.mainClass="com.sies.timetable.Main"
```

---

## 8. Automated Test Suite (62 Tests, 0 Failures)

The project includes an extensive suite of unit tests, reconciliation verifiers, validator tests, solver benchmarks, and HTTP API integration tests:

```powershell
# Run the complete test suite
mvn test
```

### Verified Test Suite Breakdown
| Test Class | Purpose | Tests | Status |
| :--- | :--- | :---: | :---: |
| `ConstraintConfigurationTest` | Verifies H1–H10 / S1–S6 enabling, locking, and parameter updates | 7 | Pass |
| `DynamicStructureSolverTest` | Tests dynamic division counts and standard configurations | 2 | Pass |
| `EndToEndSolverTest` | Solves full 26-class dataset and validates output constraints | 1 | Pass |
| `ExcelTimetableExporterTest` | Verifies Apache POI workbook structure and color styling | 1 | Pass |
| `HttpServerIntegrationTest` | Tests all REST endpoints, CORS, preflight, and shutdown | 15 | Pass |
| `NormalizationTest` | Verifies teacher alias and code resolution dictionary | 2 | Pass |
| `ReconciliationTest` | Validates dual-source allotment demand vs supply matching | 5 | Pass |
| `ScheduleValidatorTest` | Audits H1–H10 hard rules and S1–S6 soft penalty calculations | 3 | Pass |
| `SolverPerformanceBenchmarkTest` | Profiles construction time, Phase 1/2 solve, and memory footprint | 3 | Pass |
| `TeachingPeriodsFlowTest` | Validates teaching periods allocation, validation, and persistence | 20 | Pass |
| `TimetableServiceTest` | Verifies end-to-end service orchestration and listeners | 3 | Pass |
| **Total** | | **62** | **100% Pass** |

### Frontend Build & Lint Verification
```powershell
cd frontend
npm run build    # Production TypeScript & Vite bundling (0 errors)
```

---

## 9. Windows Desktop Packaging

To compile, bundle, and generate the standalone Windows NSIS installer executable:

```powershell
# Automated one-click packaging pipeline:
powershell -ExecutionPolicy Bypass -File scripts/build_desktop.ps1
```

### Packaging Steps Under the Hood
1. **Maven Package**: Builds `timetable-generator-1.0.0.jar` and copies runtime dependencies into `target/lib`.
2. **Resource Preparation (`scripts/prepare_resources.py`)**:
   - Invokes `jlink` to build a minimal 48 MB private JRE containing required modules (`java.base`, `java.desktop`, `java.logging`, `java.management`, `java.naming`, `java.xml`, `java.sql`, `jdk.unsupported`, `jdk.crypto.ec`, `jdk.httpserver`, and `jdk.zipfs`).
   - Copies the backend JAR and native OR-Tools Windows binaries (`ortools-win32-x86-64`) into `src-tauri/resources/backend`.
   - Stages default configurations and sample datasets into `src-tauri/resources/defaults`.
3. **Frontend Production Build**: Executes `npm run build` in `frontend/` to produce static assets in `frontend/dist`.
4. **Tauri NSIS Bundling**: Executes `npx @tauri-apps/cli build` to compile the Rust binary and generate the NSIS installer.

### Output Executable
The installer will be generated at:
```
src-tauri/target/release/bundle/nsis/SIES Timetable Generator_1.0.0_x64-setup.exe
```
- **Size**: ~58.5 MB
- **Installation Mode**: `currentUser` (installs to `%LOCALAPPDATA%\SIES Timetable Generator` without requiring UAC administrator privileges).

---

## 10. User Data & Storage Paths

When running as an installed desktop application:
- **Application Binaries & Bundled JRE**:
  `%LOCALAPPDATA%\SIES Timetable Generator\`
- **User Configuration, Active Data & Exports**:
  `%APPDATA%\com.sies.timetable.desktop\`
  - `config/school-config.yaml` — Active school settings, bell schedule, and weights.
  - `sample-data/` — Active allotment spreadsheets and constraint CSVs.
  - `output/` — Generated timetables (`timetable.xlsx`, `student_timetable.csv`, `teacher_timetable.csv`, and `pdf/`).

---

## 11. Third-Party Attribution & Licensing

This software relies on the following open-source libraries:

| Component | License | Purpose |
| :--- | :--- | :--- |
| [Google OR-Tools](https://developers.google.com/optimization) | Apache License 2.0 | CP-SAT constraint optimization engine |
| [Apache POI](https://poi.apache.org/) | Apache License 2.0 | Excel spreadsheet ingestion and generation |
| [OpenPDF](https://github.com/LibrePDF/OpenPDF) | LGPL / MPL | PDF timetable document generation |
| [Jackson Databind](https://github.com/FasterXML/jackson) | Apache License 2.0 | JSON serialization for HTTP API |
| [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml) | Apache License 2.0 | YAML configuration parsing |
| [FlatLaf](https://www.formdev.com/flatlaf/) | Apache License 2.0 | Java Swing look-and-feel styling |
| [Tauri](https://tauri.app/) | Apache License 2.0 / MIT | Desktop application framework |
| [React](https://react.dev/) | MIT License | Frontend user interface library |
| [Lucide Icons](https://lucide.dev/) | ISC License | User interface iconography |
