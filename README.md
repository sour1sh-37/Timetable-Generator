# SIES Timetable Generator

A high-performance, constraint-programming-based school timetable management and generation platform. Designed for local desktop deployment, it couples an authoritative **Java 21 + Google OR-Tools CP-SAT** scheduling engine with a modern **React 19, TypeScript, and Tauri v2** desktop application shell.

---

## 1. Project Overview

School timetabling is a high-dimensional combinatorial optimization problem. Coordinating dozens of class divisions across dozens of faculty members, specialized lab resources, differing weekly subject frequencies, consecutive period limits, and teacher preferences manually invariably results in scheduling clashes, teacher double-bookings, syllabus delivery imbalances, and faculty burnout.

**SIES Timetable Generator** solves this challenge by formulating institutional scheduling policies into an exact mathematical constraint satisfaction and optimization model. It:
- Ingests and reconciles class-wise and teacher-wise allocation spreadsheets.
- Formulates hard operational constraints (H1–H10) to guarantee conflict-free feasibility.
- Optimizes pedagogical soft constraints (S1–S6) to balance faculty workloads and distribute subjects evenly across instructional weekdays.
- Provides interactive visual grid views for all classes and teachers.
- Enables in-place manual slot swapping with live constraint validation and undo/redo history.
- Exports publication-ready Excel master workbooks, per-class printable PDFs, and comprehensive CSV reconciliation reports.
- Runs entirely locally on the desktop with zero cloud, internet, or telemetry dependencies.

---

## 2. Features

### Core Scheduling & Engine Features
- **Authoritative Google OR-Tools CP-SAT Solver**:
  - **Phase 1 (Hard Satisfaction)**: Rapidly finds a guaranteed feasible schedule satisfying all hard operational rules H1–H10.
  - **Phase 2 (Soft Optimization)**: Warm-starts from the Phase 1 solution to minimize penalty weights across pedagogical objectives S1–S6 within a user-configurable time limit.
- **Enforced Hard Constraints (H1–H10)**:
  - **H1**: Class teacher placed in Period 1 whenever they teach their assigned class.
  - **H2**: Maximum 2 consecutive lectures per teacher block (recess breaks count as resets).
  - **H3**: Exactly one teacher-subject assignment per class slot (complete 48-period grid coverage).
  - **H4**: Strict teacher single-booking (at most one class per teacher per time slot).
  - **H5**: Computer lab exclusivity (at most one class allocated to the lab per time slot).
  - **H6**: Physical Education (P.E.) strictly prohibited in Period 1.
  - **H7**: Physical Education prohibited in Period 5 (immediately following morning recess).
  - **H8**: Maximum 2 activity/co-curricular periods per class per day.
  - **H9**: Exact weekly allotment fulfillment across all classes, subjects, and teachers.
  - **H10**: P.E. and Computer partition around recess when scheduled on the same day.
- **Soft Pedagogical Objectives (S1–S6)**:
  - **S1**: Even distribution of multi-period subjects across distinct weekdays.
  - **S2**: Spacing 2-period subjects across non-consecutive days (minimum 2-day separation).
  - **S3**: Balanced spacing between repeated subject lectures.
  - **S4**: Minimization of daily teaching workload variance per faculty member.
  - **S5**: Minimization of unassigned idle gap windows in faculty daily schedules.
  - **S6**: Academic variety (avoiding sequences of more than 3 consecutive heavy academic lectures).

### User Interface & Experience
- **Command Center**:
  - Live system health and solver daemon readiness indicators.
  - Dataset status, loaded standard/division metrics, and input file validation.
  - One-click solver execution with real-time phase progress reporting.
  - Recent solve metrics: feasibility duration, optimization duration, and violation counters.
- **School Setup**:
  - Visual configuration of standards (5 to 10) and division counts (A through E).
  - Faculty directory with teaching allocations, subject specialties, and class-teacher designations.
  - Configurable bell schedule: period counts (default 8/day), period timings, and recess placement.
  - Model validation and reset controls.
- **Timetable Studio**:
  - Interactive Day $\times$ Period weekly schedule viewer for every class and teacher.
  - Color-coded subject badges, teacher codes, and distinct recess blocks.
  - Full Saturday timetable scheduling support.
  - **Manual Editing Mode**: Select and swap schedule slots with live instant re-validation against all H1–H10 hard constraints and S1–S6 soft objectives.
  - Complete **Undo / Redo** stack for manual schedule modifications.
- **Multi-Format Exporters**:
  - **Excel (`.xlsx`)**: Master timetable workbook via Apache POI featuring summary, all-class schedules, and all-teacher schedules with color-coded recess blocks.
  - **Printable PDFs (`.pdf`)**: Individual, beautifully styled per-class timetables generated via OpenPDF.
  - **CSV Suite (`.csv`)**: Normalized classes, teachers, student schedules, teacher schedules, reconciliation discrepancies, and audit quality reports.

### Legacy Desktop Compatibility
- The original standalone Java Swing GUI (`com.sies.timetable.Main` launched without arguments) remains fully functional alongside the modern desktop shell.

---

## 3. System Architecture

The application adopts a decoupled, local desktop architecture where the Java scheduling engine remains the authoritative source of truth:

```
┌────────────────────────────────────────────────────────┐
│               Windows Desktop Environment              │
│                                                        │
│  ┌─────────────────────────┐                           │
│  │  Tauri v2 Native Shell  │                           │
│  │   (Rust + WebView2)     │                           │
│  └───────────┬─────────────┘                           │
│              │ IPC / Local HTTP (127.0.0.1:8080)        │
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

- **Java 21 Engine**: Authoritative implementation of constraint programming, mathematical models, input ingestion, reconciliation, validation, and file exports.
- **Local HTTP API Daemon (`TimetableHttpServer`)**: Headless embedded HTTP server bound strictly to loopback (`127.0.0.1:8080` with dynamic ephemeral fallback).
- **React 19 + TypeScript Frontend**: Modern responsive web shell built with Vite and Tailwind/CSS tokens adhering to the official school identity palette.
- **Tauri v2 Desktop Shell**: Orchestrates the headless Java child process, manages dynamic loopback port discovery, monitors health check handshakes, and binds the process to a Windows Job Object (`JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE`) to prevent orphaned background processes.

---

## 4. Prerequisites

### For End Users
- **Operating System**: Windows 10 (64-bit) or Windows 11.
- **Dependencies**: None. The installer bundles a private, self-contained Java Runtime Environment (JRE) and all native OR-Tools dynamic link libraries (`.dll`).

### For Developers Building from Source
- **Java Development Kit (JDK)**: JDK 21 or higher (JDK 21 or JDK 25 recommended).
- **Apache Maven**: 3.9+ (or use the included `mvnw` wrapper).
- **Node.js**: v20+ with npm.
- **Rust Toolchain**: 1.80+ with `x86_64-pc-windows-msvc` target.
- **Python**: 3.10+ (used by the resource staging script `scripts/prepare_resources.py`).
- **Visual Studio C++ Build Tools**: Required for compiling native Tauri Rust crates.
- **NSIS (Nullsoft Scriptable Install System)**: Bundled automatically by the Tauri CLI.

---

## 5. Local Development

You can run the backend and frontend separately during active development:

### Terminal 1: Launch Java Backend Daemon
```powershell
# Build backend and start HTTP API on port 8080
mvn clean compile
mvn exec:java -Dexec.mainClass="com.sies.timetable.Main" -Dexec.args="--server 8080"
```
Verify the backend is running by opening: `http://127.0.0.1:8080/api/health`

### Terminal 2: Launch React Development Server
```powershell
# From project root
cd frontend
npm install
npm run dev
```
Open `http://localhost:5173` in your browser. The frontend will automatically detect and communicate with the backend daemon on port 8080.

### Standalone Swing GUI (Legacy Mode)
To launch the original Java Swing application directly:
```powershell
mvn exec:java -Dexec.mainClass="com.sies.timetable.Main"
```

---

## 6. Running Tests

The project includes unit tests, parser verification, constraint validator tests, solver benchmarks, and HTTP API integration tests:

```powershell
# Run the complete test suite
mvn test
```

### Verified Test Suite Breakdown (34 Tests, 0 Failures)
| Test Class | Purpose | Tests |
| :--- | :--- | :---: |
| `DynamicStructureSolverTest` | Tests dynamic division counts and standard configurations | 2 |
| `EndToEndSolverTest` | Solves full 26-class dataset and validates output constraints | 1 |
| `ExcelTimetableExporterTest` | Verifies Apache POI workbook structure and styling | 1 |
| `HttpServerIntegrationTest` | Tests all REST endpoints, CORS, preflight, and shutdown | 14 |
| `NormalizationTest` | Verifies teacher alias and code resolution dictionary | 2 |
| `ReconciliationTest` | Validates dual-source allotment demand vs supply matching | 5 |
| `ScheduleValidatorTest` | Audits H1–H10 hard rules and S1–S6 soft penalty calculations | 3 |
| `SolverPerformanceBenchmarkTest` | Profiles construction time, Phase 1/2 solve, and memory | 3 |
| `TimetableServiceTest` | Verifies end-to-end service orchestration and listeners | 3 |
| **Total** | | **34** |

### Frontend Linting & Build Verification
```powershell
cd frontend
npm run lint     # Oxlint verification (0 errors, 0 warnings)
npm run build    # Production TypeScript & Vite bundling
```

---

## 7. Windows Desktop Packaging

To compile, bundle, and generate the standalone Windows NSIS installer executable:

```powershell
# Automated one-click packaging pipeline:
powershell -ExecutionPolicy Bypass -File scripts/build_desktop.ps1
```

### Packaging Steps Under the Hood
1. **Maven Package**: Builds `timetable-generator-1.0.0.jar` and copies runtime dependencies into `target/lib`.
2. **Resource Preparation (`scripts/prepare_resources.py`)**:
   - Invokes `jlink` to build a minimal 48 MB private JRE containing the 11 required modules (`java.base`, `java.desktop`, `java.logging`, `java.management`, `java.naming`, `java.xml`, `java.sql`, `jdk.unsupported`, `jdk.crypto.ec`, `jdk.httpserver`, and `jdk.zipfs`).
   - Copies the backend JAR and Windows-specific native OR-Tools binaries (`ortools-win32-x86-64`) into `src-tauri/resources/backend`.
   - Stages default configuration and synthetic sample datasets into `src-tauri/resources/defaults`.
3. **Frontend Production Build**: Executes `npm run build` in `frontend/` to produce static assets in `frontend/dist`.
4. **Tauri NSIS Bundling**: Executes `npx @tauri-apps/cli build` to compile the Rust binary and generate the NSIS installer.

### Output Executable
The installer will be generated at:
```
src-tauri/target/release/bundle/nsis/SIES Timetable Generator_1.0.0_x64-setup.exe
```
- **Size**: ~58.5 MB
- **Installation Mode**: `currentUser` (installs to `%LOCALAPPDATA%\SIES Timetable Generator` without UAC administrator prompts).

---

## 8. User Data & Storage Paths

When running as an installed desktop application:
- **Application Binaries & Bundled JRE**:
  `%LOCALAPPDATA%\SIES Timetable Generator\`
- **User Configuration, Active Data & Exports**:
  `%APPDATA%\com.sies.timetable.desktop\`
  - `config/school-config.yaml` — Active school settings, bell schedule, and weights.
  - `sample-data/` — Active allotment spreadsheets and constraint CSVs.
  - `output/` — Generated timetables (`timetable.xlsx`, `student_timetable.csv`, `teacher_timetable.csv`, and `pdf/`).

---

## 9. Troubleshooting

### 1. "Failed to start Java backend" or Health Check Timeout
- **Check Java Installation**: Ensure JDK 21+ is installed and `JAVA_HOME` is set if running in development mode.
- **Port Conflict**: The application automatically checks port 8080 and falls back to an ephemeral loopback port. If a security tool blocks socket binding on `127.0.0.1`, ensure loopback connections are permitted.

### 2. "Provider 'jar' not found" during Solver Initialization
- Google OR-Tools extracts its native Windows C++ DLLs (`jniortools.dll`) dynamically from the jar file. This requires the `jdk.zipfs` module in the runtime JRE. The included `prepare_resources.py` script automatically includes this module.

### 3. Windows SmartScreen Warning on First Install
- Because the installer is built from source and not yet signed with a commercial EV Authenticode certificate, Windows Defender SmartScreen may display *"Windows protected your PC"*.
- **To proceed**: Click **More info** $\rightarrow$ **Run anyway**.

### 4. Input File Validation Errors
- Ensure all 3 required input Excel files are present:
  1. `sample-standardwise-allotment.xlsx`
  2. `sample-teacherwise-allotment.xlsx`
  3. `sample-classteacher-list.xlsx`
- Standard names must follow roman numeral conventions (`V`, `VI`, `VII`, `VIII`, `IX`, `X`) and divisions must be letters (`A` through `E`).

---

## 10. Third-Party Attribution & Licensing

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
