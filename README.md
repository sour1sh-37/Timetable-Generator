# Timetable Generator

A constraint-programming-based school timetable generator and desktop application built in Java with Google OR-Tools.

---

## Overview

School timetabling is a notoriously complex combinatorial optimization problem. Scheduling dozens of class divisions across multiple teachers, subjects, specialized rooms, and operational constraints manually often results in scheduling conflicts, teacher double-bookings, curriculum guideline violations, and uneven teacher workloads.

**Timetable Generator** automates this process by formulating school scheduling policies into a mathematical constraint model. It reconciles dual-source allocation spreadsheets, enforces non-negotiable operational rules (such as zero teacher clashes, room capacity limits, consecutive lecture restrictions, and morning period class-teacher assignments), optimizes timetable quality objectives, validates the resulting schedule independently, and exports ready-to-use Excel, PDF, and CSV files alongside an interactive desktop interface.

This project was developed as an academic Software Engineering / GDG student showcase project to demonstrate practical applications of constraint satisfaction programming (CP-SAT) in education technology.

---

## Features

The following features are **currently implemented** in the project:

- **Dual-Source Ingestion & Reconciliation**:
  - Automatically parses standard-wise and teacher-wise allocation spreadsheets (`.xlsx`).
  - Reconciles class subject demands against teacher workload allocations at the individual period level.
  - Normalizes teacher initials and aliases using a canonical dictionary, logging upstream data anomalies prior to solving.
- **Constraint-Based CP-SAT Scheduling Engine**:
  - Two-phase solving architecture powered by **Google OR-Tools CP-SAT**:
    - **Phase 1**: Rapidly solves a pure hard constraint satisfaction baseline.
    - **Phase 2**: Warm-starts from the baseline and optimizes soft quality objectives under a configurable time limit.
- **Enforced Hard Constraints (H1–H10)**:
  - **H1**: Class teacher assigned to Period 1 whenever they teach their designated class.
  - **H2**: Maximum 2 continuous lectures per teacher block (reset by recess).
  - **H3**: Exactly one assignment per class time slot (weekly capacity matching).
  - **H4**: No teacher double-booking (at most one class per teacher per slot).
  - **H5**: Computer lab exclusivity (at most one class in the computer lab per slot).
  - **H6**: Physical Education (P.E.) prohibited in Period 1.
  - **H7**: P.E. combined-division synchronisation support where designated.
  - **H8**: Maximum 2 activity / co-curricular periods per class per day.
  - **H9**: Exact weekly allocation matching per subject-class pairing.
  - **H10**: Teacher weekly workload caps strictly respected.
- **Soft Optimization Objectives (S1–S6)**:
  - Daily core academic subject limits (avoiding subject fatigue).
  - Uniform spread of subject lectures across instructional weekdays.
  - Balanced daily teaching load distribution for faculty.
  - Minimization of idle gap periods in teacher schedules.
- **Independent Post-Solve Validator**:
  - Audits every single time slot from scratch independently of solver internals.
  - Generates location-specific diagnostics (exact Day, Period, Class, Teacher, Subject) with human-readable explanations.
- **Desktop Graphical User Interface (Java Swing)**:
  - Modern light theme styled with **FlatLaf**.
  - Folder selector with automatic input file detection.
  - Live progress log displaying parsing, reconciliation, solving, and export steps.
  - Interactive **Day $\times$ Period visual grid viewers** for all classes and teachers.
  - Clickable validation diagnostics to jump straight to affected class or teacher grids.
- **Multi-Format Export**:
  - **Excel Workbook (`.xlsx`)**: Master timetable workbook via Apache POI featuring summary, all-class schedules, and all-teacher schedules with color-coded recess breaks and borders.
  - **PDF Documents (`.pdf`)**: Printable per-class timetables generated via OpenPDF.
  - **CSV Audit Suite (`.csv`)**: 9 machine-readable reports covering master student/teacher matrices, quality scores, feasibility checks, reconciliation logs, and normalized entities.
- **Headless / CLI Execution**: Run generation in batch mode via command-line flags (`--cli`) without launching the GUI.
- **Configurable Rules (YAML)**: External configuration for school details, bell timings, periods per day, recess placement, and solver parameters.

---

## Technology Stack

- **Language & Runtime**: Java 21+ (Tested on JDK 21 and JDK 25)
- **Build & Dependency Management**: Apache Maven (with included `mvnw` and `mvnw.cmd` wrapper)
- **Constraint Programming Solver**: [Google OR-Tools CP-SAT](https://developers.google.com/optimization) (`com.google.ortools:ortools-java:9.11.4210`)
- **Desktop GUI**: Java Swing with [FlatLaf](https://www.formdev.com/flatlaf/) (`com.formdev:flatlaf:3.4.1`)
- **Spreadsheet Processing**: [Apache POI](https://poi.apache.org/) (`org.apache.poi:poi-ooxml:5.2.5`)
- **PDF Generation**: [OpenPDF](https://github.com/LibrePDF/OpenPDF) (`com.github.librepdf:openpdf:1.3.39`)
- **Configuration Parser**: [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml) (`org.yaml:snakeyaml:2.2`)
- **Automated Testing**: JUnit 5 (`org.junit.jupiter:junit-jupiter:5.10.2`)

---

## How It Works

The timetable generation pipeline follows a 6-stage workflow:

```
[ Input Spreadsheets & YAML Config ]
                ↓
    [ 1. Parsing & Ingestion ]
                ↓
  [ 2. Data Reconciliation & Normalization ]
                ↓
   [ 3. CP-SAT Constraint Formulation ]
                ↓
  [ 4. Independent Schedule Validation ]
                ↓
   [ 5. File Export & Visual Rendering ]
```

1. **Input / Configuration Ingestion**: The system loads operational timings from `config/school-config.yaml` and scans the input directory for allotment spreadsheets.
2. **Reconciliation & Normalization**: The reconciler compares standard-wise demand against teacher-wise supply, resolves faculty aliases via `NormalizationDictionary`, and logs any upstream staffing discrepancies.
3. **Scheduling Model (CP-SAT)**: Integer decision variables $x_{c, d, p, a} \in \{0, 1\}$ are created for each class $c$, day $d$, period $p$, and atomic assignment $a$. Hard constraints (H1–H10) are applied to define feasible space, and soft objectives (S1–S6) are minimized.
4. **Independent Validation**: The generated schedule is evaluated by `ScheduleValidator` from scratch without referencing solver states to certify 0 hard violations and calculate a quality index.
5. **Timetable Output & Export**: Formatted Excel workbooks, PDFs, and 9 audit CSVs are exported to the `output/` directory.
6. **Viewing**: The interactive Swing GUI renders the schedule in Day $\times$ Period grids with recess breaks, allowing navigation across classes and teachers.

---

## Project Structure

```
timetable-generator/
├── config/
│   ├── school-config.yaml             # Active school, timing, and solver configuration
│   └── school-config.example.yaml     # Documented template for custom deployments
├── docs/
│   ├── CONSTRAINTS.md                 # Formal mathematical definitions of constraints
│   └── PROJECT_ANALYSIS.md            # In-depth technical data analysis & reconciliation rules
├── sample-data/
│   ├── sample-standardwise-allotment.xlsx  # Class-wise subject requirements
│   ├── sample-teacherwise-allotment.xlsx   # Teacher workload blocks
│   ├── sample-classteacher-list.xlsx       # Class teachers and substitution staff
│   └── sample-constraints.csv              # Machine-readable constraint catalog
├── src/
│   ├── main/java/com/sies/timetable/
│   │   ├── config/            # YAML configuration loader & SchoolConfig POJO
│   │   ├── exporter/          # Excel (POI), PDF (OpenPDF), and CSV exporters
│   │   ├── gui/               # Swing desktop UI (frames, viewers, diagnostic panels)
│   │   ├── model/             # Core domain models (ClassRoom, Teacher, TimeSlot, etc.)
│   │   ├── parser/            # Excel sheet parsers & staff alias dictionary
│   │   ├── reconciliation/    # Dual-source workload reconciler & discrepancy logger
│   │   ├── service/           # TimetableService orchestrating the complete pipeline
│   │   ├── solver/            # Google OR-Tools CP-SAT formulation & 2-phase solver
│   │   └── validator/         # Independent post-solve validator & diagnostics
│   └── test/java/com/sies/timetable/ # JUnit 5 test suite (17 automated tests)
├── .gitignore                         # Build, output, IDE, and sensitive file ignore rules
├── CONSTRAINTS.md                     # Constraint catalog at repository root
├── mvnw / mvnw.cmd                    # Maven wrapper scripts (Unix / Windows)
├── pom.xml                            # Project dependencies and build configuration
└── README.md                          # Repository documentation
```

---

## Getting Started

### Prerequisites

- **Java Development Kit (JDK 21 or higher)**
- **Git**

Verify your Java version:
```bash
java -version
```

### 1. Clone the Repository

```bash
git clone https://github.com/<your-username>/timetable-generator.git
cd timetable-generator
```

### 2. Build the Project with Maven

Use the included Maven wrapper to clean and build the project without requiring a separate Maven installation:

**Windows (PowerShell / Command Prompt):**
```powershell
.\mvnw.cmd clean compile
```

**Linux / macOS:**
```bash
./mvnw clean compile
```

### 3. Run Automated Tests

Execute the 17-test JUnit 5 suite (verifies normalization, reconciliation, CP-SAT dynamic solver, exporters, and validation):

**Windows:**
```powershell
.\mvnw.cmd test
```

**Linux / macOS:**
```bash
./mvnw test
```

### 4. Run the Application

#### Option A: Launch Desktop GUI (Recommended)

**Windows:**
```powershell
.\mvnw.cmd exec:java
```

**Linux / macOS:**
```bash
./mvnw exec:java
```

- When the application opens, verify that the input files from `sample-data/` are detected.
- Click **Generate Timetable** to run the complete reconciliation, solving, validation, and export pipeline.
- Browse the interactive Day $\times$ Period grids using the **View Class** and **View Teacher** tabs.

#### Option B: Run in Headless / CLI Mode

To run the solver in batch mode directly from the terminal without opening GUI windows:

**Windows:**
```powershell
.\mvnw.cmd exec:java "-Dexec.args=--cli"
```

**Linux / macOS:**
```bash
./mvnw exec:java -Dexec.args="--cli"
```

To run with a custom configuration file:
```bash
./mvnw exec:java -Dexec.args="config/school-config.yaml"
```

---

## Configuration

The application is configured through [`config/school-config.yaml`](config/school-config.yaml). A template is provided at [`config/school-config.example.yaml`](config/school-config.example.yaml).

### Key Settings

```yaml
school:
  name: "Sample High School"
  academic_year: "2026-27"

schedule:
  days:
    - MONDAY
    - TUESDAY
    - WEDNESDAY
    - THURSDAY
    - FRIDAY
    - SATURDAY
  periods_per_day: 8        # 8 periods/day = 48 weekly slots per class
  recess_after_period: 4    # 20-min recess between periods 4 and 5
  period_timings:
    1: "07:10 - 07:50"
    2: "07:50 - 08:25"
    # ...
    recess: "09:30 - 09:50"
    5: "09:50 - 10:25"
    # ...

solver:
  time_limit_seconds: 60    # Solver timeout limit
  num_search_workers: 8     # CP-SAT parallel worker threads
  random_seed: 42

paths:
  standardwise_file: "sample-data/sample-standardwise-allotment.xlsx"
  teacherwise_file: "sample-data/sample-teacherwise-allotment.xlsx"
  class_teachers_file: "sample-data/sample-classteacher-list.xlsx"
  constraints_file: "sample-data/sample-constraints.csv"
  output_dir: "output"
```

### Input Data Files

The engine reads four input files from `sample-data/`:
1. `sample-standardwise-allotment.xlsx`: Grade-wise division requirements across subjects and period counts.
2. `sample-teacherwise-allotment.xlsx`: Workload blocks grouped by teacher.
3. `sample-classteacher-list.xlsx`: Class teacher designations and substitute teacher pools.
4. `sample-constraints.csv`: Formal catalog of active constraints and their descriptions.

All sample files contain completely anonymized identifiers (`Teacher AAK`, `Teacher HD`, etc.) while retaining authentic secondary-school scheduling complexity (26 classes, 44 teachers, 393 atomic allocations, 1,248 total slots).

---

## Outputs

When the generation process completes, artifacts are saved to the `output/` directory:

| Output File | Format | Description |
| :--- | :---: | :--- |
| `output/timetable.xlsx` | Excel (`.xlsx`) | Formatted multi-sheet workbook with Summary, All Classes Timetable, and All Teachers Timetable with color-coded recess breaks and borders. |
| `output/*.pdf` | PDF | Formatted, printable timetables for each class division. |
| `output/student_timetable.csv` | CSV | Master class timetable matrix across all days and periods. |
| `output/teacher_timetable.csv` | CSV | Master faculty weekly schedule matrix. |
| `output/validation_report.csv` | CSV | Slot-level audit report verifying hard constraint satisfaction and diagnostics. |
| `output/quality_report.csv` | CSV | Overall timetable quality index and soft penalty breakdown. |
| `output/input_validation_report.csv` | CSV | Pre-solve feasibility checks (e.g. verifying exact 48 periods per division). |
| `output/reconciliation_discrepancies.csv` | CSV | Upstream spreadsheet anomaly log (unmatched teaching blocks, omitted formulas). |
| `output/normalized_allocations.csv` | CSV | 393 atomic (Class, Subject, Teacher, Periods) tuples fed into the solver. |
| `output/normalized_teachers.csv` | CSV | Normalized teacher roster with standardized codes and total workloads. |
| `output/normalized_classes.csv` | CSV | Canonical class directory with designated class teachers. |

---

## Project Status

> **This project is currently under development. The core timetable-generation functionality is implemented, while additional features, refinements, and improvements are still being worked on.**

The constraint solver, validation engine, spreadsheet parsers, and desktop grid viewers are fully operational and verified by automated tests.

---

## Future Improvements

The following improvements are planned or identified from current project development:

- **Interactive Manual Editor**: Interactive drag-and-drop / period swapping in the GUI with real-time constraint validation feedback (the underlying domain model and preview panels exist; interactive swap controls are being refined).
- **Web Interface / REST API**: A lightweight web interface or REST API wrapper to allow browser-based scheduling without desktop Java runtime dependencies.
- **Calendar Exports**: Support for exporting teacher and student schedules into iCal / Google Calendar format (`.ics`).
- **In-App Configuration Editor**: Visual dialogs within the GUI for modifying bell timings, subject taxonomy, and constraint weights without directly editing YAML files.
- **Teacher Leave & Unavailability Matrix**: Explicit GUI support for modeling teacher leave, half-day preferences, and part-time availability schedules.

---

## Screenshots / Demo

<!-- Placeholder: Add screenshots here after generating images -->
<!-- Example: ![Main Interface](docs/screenshots/main-interface.png) -->
*(Screenshots and UI walkthroughs will be added in an upcoming update.)*

---

## Contributing

This is a college / student showcase project. Suggestions, feedback, and discussions are welcome! Feel free to open an issue to suggest improvements or report bugs.

---

## License

This repository currently has no specified open-source license. All rights are reserved by the project author. If you wish to use, modify, or distribute this software, please contact the author.
