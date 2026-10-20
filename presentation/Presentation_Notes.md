# SIES Academic Timetable Generator
## Comprehensive Speaker Notes, Technical Defense & Faculty Q&A Guide

**Project:** SIES (Dr. APJ Abdul Kalam Memorial) High School — Academic Timetable Generator  
**Author / Lead Developer:** Sourish Kumar Rai  
**Target Audience:** Academic Assessment Committee, Software Engineering Faculty, Project Review Panel  
**Deliverables Covered:** `SIES_Timetable_Generator_Presentation.pptx` (14 Widescreen Slides) & `SIES_Timetable_Generator_Presentation.pdf`  
**Presentation Timing:** Recommended ~15–20 minutes (~60–90 seconds per slide + 5–10 minutes technical defense Q&A)

---

## Presentation Run-of-Show & Time Allocation

| Slide | Section / Category | Slide Title | Recommended Time | Core Objective |
| :---: | :--- | :--- | :---: | :--- |
| **1** | Title Slide | SIES Academic Timetable Generator | 1:00 min | Set project scope, institutional context, and technical stack. |
| **2** | Problem Definition | The Academic Timetabling Challenge | 1:15 min | Articulate combinatorial complexity (NP-complete) and manual failure modes. |
| **3** | System Goals | Project Objectives & Design Mandates | 1:15 min | Define four pillars: Zero-defect H1–H10, soft optimization S1–S6, desktop app, studio. |
| **4** | Technical Foundations | Technology Stack & Architecture Justification | 1:30 min | Justify Java 21 LTS + OR-Tools CP-SAT vs Genetic Algorithms and Tauri v2 vs Electron. |
| **5** | System Architecture | Desktop Architecture & Process Lifecycle | 1:30 min | Detail client-daemon IPC, dynamic loopback port binding, and Win32 Job Object cleanup. |
| **6** | Execution Pipeline | End-to-End Timetable Generation Workflow | 1:30 min | Walk through the 6 continuous stages: Ingestion, Reconciliation, Axioms, Solver, Validator, Export. |
| **7** | Mathematical Modeling | Constraint Formulation with OR-Tools CP-SAT | 1:45 min | Explain Boolean decision variables $x(c,d,p,a)$, CDCL solver tree, and two-phase warm starting. |
| **8** | Constraint Catalog | Enforced Hard Operational Rules (H1–H10) | 1:30 min | Enumerate 10 non-negotiable operational rules guaranteeing clash-free feasibility. |
| **9** | Optimization Objectives| Pedagogical Quality Optimization (S1–S6) | 1:30 min | Detail weighted objective function minimizing student fatigue and teacher workload variance. |
| **10**| User Experience | Command Center & Interactive Timetable Studio | 1:15 min | Demonstrate Command Center launcher, School Setup configuration, and in-place slot swap studio. |
| **11**| Export Suite | Production Deliverables & Multi-Format Exporters | 1:15 min | Showcase Apache POI Excel workbook, 26 OpenPDF vector class PDFs, and 9 audit CSV reports. |
| **12**| Reliability & Verification| Independent Validation & Automated Testing | 1:30 min | Highlight zero-trust `ScheduleValidator.java` and 100% pass rate across 34 automated tests. |
| **13**| Deployment & Benchmarks | Desktop Packaging & Empirical Performance | 1:30 min | Present empirical benchmarks (34–48s solve, 58.5 MB NSIS setup, 48 MB private JRE via `jlink`). |
| **14**| Summary & Outlook | Limitations, Future Roadmap & Conclusion | 1:00 min | Summarize achievements, acknowledge desktop scope, present roadmap, and transition to Q&A. |

---

## Slide 1: Title Slide (Cover)

### Slide Header & Purpose
- **Category:** Title / Introduction
- **Title:** Academic Timetable Generator
- **Subtitle:** High-Performance Multi-Constraint School Scheduling & Desktop Management Platform
- **Visuals:** Embedded SIES Institutional Emblem, Gold/Terracotta Theme, Metadata Card
- **Primary Message:** Establish authority, academic rigor, and production-grade engineering for SIES High School.

### Spoken Presentation Script
> "Good morning, respected members of the evaluation committee and faculty panel. My name is Sourish Kumar Rai, and today I am proud to present my software engineering capstone project: the **SIES Academic Timetable Generator**.
>
> Secondary school scheduling is notoriously complex. In a large secondary school such as SIES High School, generating a conflict-free master timetable across 26 classes, 44 faculty members, and 1,248 weekly periods has historically taken administrative committees several weeks of manual spreadsheet juggling. Even then, hidden teacher clashes and bad pedagogical schedules slip through.
>
> To solve this, I engineered a high-performance, turnkey desktop platform. At its core is Google OR-Tools CP-SAT powered by Java 21, coupled with an independent zero-trust verification engine and wrapped in a modern, lightweight Tauri v2 Windows desktop application. Over the next fifteen minutes, I will walk you through the mathematical modeling, software architecture, verification methodology, and empirical performance of this production-ready system."

### Technical Deep-Dive
- **Repository Baseline:** Package root `com.sies.timetable`.
- **Core Technology Stack:**
  - Runtime: Java 21 LTS (Microsoft / Eclipse Temurin build with Virtual Threads and modern language ergonomics).
  - Optimization Engine: Google OR-Tools CP-SAT 9.11 native C++ engine accessed via JNI (`com.google.ortools:ortools-java:9.11.4210`).
  - Desktop Shell: Tauri v2 (`tauri` 2.x, Rust 1.77+) hosting Windows WebView2.
  - Frontend: React 19, TypeScript 5.6, Vite 6 bundler.
  - Publishing: Apache POI 5.2.5 (OOXML / XSSF) and OpenPDF 1.3.39 (vector layout).
- **Target Institution:** SIES (Dr. APJ Abdul Kalam Memorial) High School (Standards V through X, Divisions A through E).

### Anticipated Committee Q&A
- **Q1: Why did you choose to build a local desktop application instead of hosting a SaaS web portal?**
  - **Answer:** "School scheduling data contains sensitive institutional information including teacher workloads, leave patterns, and student room allocations. School administrators strongly prefer local data governance without recurring cloud hosting costs or mandatory internet connectivity. By combining a Rust-based desktop shell (Tauri) with an embedded Java daemon bound strictly to the local loopback interface (`127.0.0.1`), we achieve zero cloud leakage, instant local file-system integration, and full offline autonomy."
- **Q2: Who is the primary persona for this application?**
  - **Answer:** "The application is built for school principals, academic supervisors, and timetable committee heads who do not possess technical command-line skills. Every technical detail—process management, port discovery, and native constraint compilation—is abstracted away behind a clean GUI."

---

## Slide 2: Problem Statement & Academic Context

### Slide Header & Purpose
- **Category:** Problem Definition & Context
- **Title:** The Academic Timetabling Challenge
- **Subtitle:** Why Secondary School Scheduling is an NP-Hard Combinatorial Bottleneck
- **Visuals:** 3 structured cards: Combinatorial Scale, Manual Failure Modes, and The Solution Imperative.

### Spoken Presentation Script
> "To appreciate why this software was needed, we must look at the combinatorial scale of the School Timetabling Problem (STP). 
> 
> At SIES High School, we operate across 6 standards—Standards V through X. Standards V and VIII comprise 5 divisions each, while Standards VI, VII, IX, and X have 4 divisions, giving a total of 26 active class divisions. With a 6-day academic week running 8 periods per day, we must assign exactly 1,248 atomic period slots. Scheduling 44 specialized faculty members across 25 subjects into these 1,248 slots creates a theoretical search space exceeding $10^{300}$ combinations.
> 
> In computer science, the School Timetabling Problem is mathematically proven to be NP-complete. When academic committees attempt this manually using Excel, human error is inevitable: teachers get double-booked across different classrooms, faculty are subjected to four or five continuous teaching periods leading to fatigue, and shared physical spaces like the single Computer Lab suffer severe scheduling conflicts.
> 
> Our engineering imperative was clear: replace weeks of manual guesswork with an autonomous optimization system that guarantees mathematical feasibility in seconds, enforces pedagogical quality, and runs entirely offline on standard school desktop computers."

### Technical Deep-Dive
- **Theoretical Complexity:** The School Timetabling Problem (STP) reduces from the 3-Dimensional Matching (3DM) and Graph Coloring problems, both known NP-complete problems (Garey & Johnson, 1979).
- **SIES Dataset Dimensionality:**
  - Total Classes ($|C|$): 26 ($5\text{A}..5\text{E}, 6\text{A}..6\text{D}, 7\text{A}..7\text{D}, 8\text{A}..8\text{E}, 9\text{A}..9\text{D}, 10\text{A}..10\text{D}$).
  - Total Faculty ($|T|$): 44 unique teachers.
  - Total Subjects ($|S|$): 25 distinct curriculum subjects.
  - Total Days ($|D|$): 6 days (Monday through Saturday).
  - Total Periods per Day ($|P|$): 8 periods (Periods 1 to 4 morning, Recess, Periods 5 to 8 afternoon).
  - Weekly Slots per Class: $6 \times 8 = 48$ periods.
  - Total Weekly Schedule Slots: $26 \times 48 = 1,248$ slots.
- **Upstream Data Inconsistencies:** Spreadsheets created by individual departments often exhibit naming divergences (e.g., `'Mrs. Sharma'` vs `'S. Sharma'`), which previously broke manual scheduling and required automated extract-transform-load (ETL) normalization.

### Anticipated Committee Q&A
- **Q1: What specifically makes the school timetabling problem harder than a generic university timetabling problem?**
  - **Answer:** "University timetables typically permit student free periods and elective clashes because students follow individualized modular credit pathways. In contrast, secondary schools enforce a strict cohort-based model: every class section must be occupied and supervised for all 48 periods without idle student gaps, while faculty possess highly constrained subject-to-class mappings and class-teacher morning attendance responsibilities."
- **Q2: Why can't a simple greedy heuristic or rule-based script solve this?**
  - **Answer:** "Greedy algorithms make myopic local choices without backtracking or lookahead. When allocating the final 10% of slots, greedy heuristics invariably encounter irrecoverable deadlocks—such as a teacher having to teach two classes simultaneously—forcing full rollbacks. CP-SAT uses SAT-based clause learning to prune millions of dead-end branches simultaneously."

---

## Slide 3: Project Objectives & Scope

### Slide Header & Purpose
- **Category:** System Goals & Scope
- **Title:** Project Objectives & Design Mandates
- **Subtitle:** Engineering a Mathematically Sound, Turnkey Desktop Scheduling Solution
- **Visuals:** 4 distinct mandate quadrants: Zero-Defect Solvability, Pedagogical Optimization, Turnkey Desktop Packaging, and Interactive Studio.

### Spoken Presentation Script
> "To deliver a true engineering solution rather than a toy prototype, we established four non-negotiable architectural mandates:
>
> First, **Zero-Defect Hard Constraint Solvability**. The engine must enforce ten operational constraints—labeled H1 through H10—with absolute mathematical certainty. There can be zero teacher double-bookings, zero computer lab collisions, and 100% curriculum allotment fulfillment. Crucially, we enforce an independent post-solve audit that checks the timetable from scratch without relying on the solver's internal state.
>
> Second, **Multi-Objective Pedagogical Optimization**. Beyond mere feasibility, a good timetable must be pedagogically sound. We formulated six soft objectives—S1 through S6—to minimize student cognitive fatigue by spacing difficult subjects evenly across the week and eliminating erratic teacher daily workload swings.
>
> Third, **Turnkey Desktop Packaging**. School staff should never have to install Java Development Kits, Python environments, or type commands into a terminal. We mandated a self-contained Windows NSIS installer that packages a trimmed 48 MB private JRE and native binaries.
>
> Fourth, **Interactive Timetable Studio & In-Place Editing**. When unexpected events occur during the term, administrators must be able to visually swap periods with real-time constraint validation and complete Undo/Redo capabilities."

### Technical Deep-Dive
- **Mandate 1 Implementation:** `CpSatTimetableSolver.java` and `HardConstraintBuilder.java`. Feasibility is verified by `ScheduleValidator.java` checking all $|C| \times |D| \times |P|$ slots.
- **Mandate 2 Implementation:** `SoftConstraintBuilder.java`. Formulated as integer penalties weighted from 100 down to 20 points, driving Phase 2 optimization.
- **Mandate 3 Implementation:** Tauri v2 with NSIS installer bundling custom JRE generated via `jlink --add-modules java.base,java.desktop,jdk.httpserver,jdk.zipfs...`.
- **Mandate 4 Implementation:** React 19 UI in `TimetableStudio.tsx`, backed by REST endpoints `/api/edit/swap` and `/api/edit/validate` within `ManualTimetableModel.java`.

### Anticipated Committee Q&A
- **Q1: If manual edits are allowed, doesn't that risk breaking the zero-defect guarantee?**
  - **Answer:** "No, because the manual editing API in `ManualTimetableModel` intercepts every prospective swap or move request and passes it directly through `ScheduleValidator.validateAssignment()`. If the user attempts to move a teacher into an already occupied slot, the system rejects the operation with a 409 Conflict, displays an exact violation message, and preserves the valid schedule state."
- **Q2: Why separate hard feasibility from soft optimization into distinct mandates?**
  - **Answer:** "Hard constraints represent physical and legal impossibilities—a teacher cannot be in two rooms at once. Soft objectives represent institutional preferences. Decoupling them allows us to guarantee feasibility in under 4 seconds, and then dedicate the remaining compute budget to finding the most pedagogically balanced schedule."

---

## Slide 4: Technology Stack & Justification

### Slide Header & Purpose
- **Category:** Technical Foundations
- **Title:** Technology Stack & Architecture Justification
- **Subtitle:** Purpose-Built Multi-Tier Architecture Engineered for Performance and Reliability
- **Visuals:** 4 architectural tier cards: Core Solver Tier, API Daemon Tier, Desktop Shell Tier, Presentation & Export Tier.

### Spoken Presentation Script
> "Let us examine the architectural justification behind our technology choices.
>
> For our **Core Solver Tier**, we selected Java 21 LTS with Google OR-Tools CP-SAT 9.11. Academic literature often discusses Genetic Algorithms or Simulated Annealing for timetabling. However, stochastic metaheuristics provide no mathematical proof of optimality, frequently get trapped in local minima, and scale poorly on tightly constrained models. CP-SAT applies Conflict-Driven Clause Learning (CDCL) and lazy clause generation to explore our 18,800+ decision variables in parallel across all CPU cores.
>
> For the **API Daemon Tier**, we deliberately avoided heavyweight web frameworks like Spring Boot or external Tomcat servers. Instead, we utilized Java's built-in `jdk.httpserver`. This provides an ultra-lightweight HTTP micro-daemon that initializes in under 150 milliseconds and binds strictly to `127.0.0.1`.
>
> For our **Desktop Shell Tier**, we chose Tauri v2 with Rust over Electron. Electron bundles an entire Chromium browser and Node.js runtime, easily inflating installer sizes to 150+ MB and consuming 200 MB of baseline RAM. Tauri uses the native Windows WebView2 already present in Windows 10 and 11, resulting in an 8.9 MB frontend binary, a 58.5 MB total installer, and under 28 MB of frontend RAM.
>
> Finally, our **Publishing Engine** combines React 19 and TypeScript for an interactive UI with Apache POI and OpenPDF for generating styled master Excel sheets, 26 vector PDFs, and 9 audit CSVs."

### Technical Deep-Dive
- **Constraint Programming vs Metaheuristics:**
  - Genetic Algorithms (GA) represent solutions as chromosomes and use crossover/mutation operators. In tightly constrained school timetabling, random mutations create infeasible individuals in >99% of cases, requiring expensive repair heuristics.
  - CP-SAT converts integer constraints into Boolean SAT clauses. When a conflict occurs, it learns the minimal conflicting clause (1-UIP cut) and backtracks directly to the decision level responsible, pruning vast subtrees.
- **Embedded Daemon (`TimetableHttpServer.java`):**
  - Uses `com.sun.net.httpserver.HttpServer`.
  - Configured with `Executors.newVirtualThreadPerTaskExecutor()` or a managed executor to process concurrent requests without thread starvation.
  - Zero third-party web framework dependencies; keeps the distribution JAR small (19.4 MB with bundled native OR-Tools DLLs).
- **Desktop Memory Footprint Comparison:**
  - Standard Electron App: ~150–220 MB RAM at idle.
  - Tauri v2 + WebView2: ~27.5 MB RAM at idle.

### Anticipated Committee Q&A
- **Q1: Why did you use JNI native bindings for OR-Tools rather than a pure Java solver like Choco or OptaPlanner/Timefold?**
  - **Answer:** "Google OR-Tools CP-SAT is implemented in optimized C++ and consistently ranks among the top performers in the international MiniZinc constraint programming competitions. Its multi-threaded SAT solver and Presolve simplifications outperform pure Java solvers by an order of magnitude on dense Boolean allocation grids."
- **Q2: Does Tauri introduce cross-platform compatibility issues?**
  - **Answer:** "Tauri is cross-platform across Windows, macOS, and Linux. For this project, our institutional target is Windows 10 and 11 desktop systems used by school administrators. Rust compiles natively to Win32, and WebView2 is standard on all modern Windows installations."

---

## Slide 5: System Architecture & Component Interaction

### Slide Header & Purpose
- **Category:** System Architecture
- **Title:** Desktop Architecture & Process Lifecycle
- **Subtitle:** Strictly Isolated Client-Daemon Communication with Guaranteed Process Teardown
- **Visuals:** 3-column process lifecycle diagram: Native Desktop Shell, Local Loopback API, Authoritative Core.

### Spoken Presentation Script
> "Slide 5 illustrates how these tiers interact during runtime and how we guarantee process lifecycle safety.
>
> When the user launches the application icon, the Tauri Rust executable starts immediately. Before showing the window, it runs our `initialize_user_data_dir()` routine to set up configuration and sample directories in `%APPDATA%`.
>
> Next, Tauri dynamically checks for port availability. It first checks `127.0.0.1:8080`. If another local process is using that port, it binds to port 0 to obtain an ephemeral loopback port. It then spawns the private Java executable headless using the Win32 `CREATE_NO_WINDOW` flag, ensuring no black command-prompt windows appear on screen.
>
> A major risk in dual-process desktop architectures is orphaned background processes—if the frontend crashes, the Java backend might stay running in the background consuming memory. To solve this at the kernel level, we register the Java process handle with a **Win32 Job Object** configured with `JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE`. The moment the Tauri window closes, is closed via Task Manager, or crashes, the Windows kernel instantly terminates the child Java process.
>
> Tauri polls the backend's `/api/health` endpoint until it receives an HTTP 200 OK handshake, passes the dynamic API URL to the React frontend, and displays the Command Center."

### Technical Deep-Dive
- **Win32 Job Object Implementation (`src-tauri/src/lib.rs`):**
  ```rust
  let job = CreateJobObjectW(std::ptr::null(), std::ptr::null());
  let mut info: ExtendedLimitInformation = std::mem::zeroed();
  info.basic_limit.limit_flags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
  SetInformationJobObject(job, JOB_OBJECT_EXTENDED_LIMIT_INFORMATION, ...);
  AssignProcessToJobObject(job, child_process_handle);
  ```
  This is a kernel-enforced guarantee: when the Job Object handle closes with the parent process, Windows kills all assigned child processes automatically.
- **Port Hunting & Healthcheck Handshake:**
  - `find_available_port()` binds `TcpListener::bind("127.0.0.1:0")` to find a free OS port if 8080 is occupied.
  - Spawns Java with argument `--port <port>`.
  - `wait_for_health()` sends raw `GET /api/health HTTP/1.1\r\nHost: 127.0.0.1:<port>\r\nConnection: close\r\n\r\n` with a 400ms timeout loop (max 15 seconds) until ready.
- **Loopback Security:**
  - Server explicitly binds to `new InetSocketAddress("127.0.0.1", port)`.
  - Any external connection attempt across LAN or WAN is dropped at the OS TCP socket stack.

### Anticipated Committee Q&A
- **Q1: What happens if the school administrator's PC has antivirus software that blocks port 8080?**
  - **Answer:** "Our dynamic port hunting logic detects if binding to 8080 fails and immediately requests an ephemeral port from the Windows socket manager (port 0). The assigned port is passed directly to the spawned Java backend, and Tauri injects this exact URL into the frontend state via `get_backend_url()`, allowing seamless operation even if port 8080 is blocked."
- **Q2: Why not communicate between Rust and Java via standard input/output (stdin/stdout) pipes instead of HTTP?**
  - **Answer:** "Standard input/output pipes are sequential and stream-oriented, making concurrent requests, progress streaming, binary file downloads (Excel/PDF), and cancellation signaling difficult to coordinate. A local HTTP REST server cleanly supports asynchronous progress listeners, structured JSON payloads, and standard HTTP error codes."

---

## Slide 6: End-to-End Timetable Generation Workflow

### Slide Header & Purpose
- **Category:** Execution Pipeline
- **Title:** End-to-End Timetable Generation Workflow
- **Subtitle:** A Continuous 6-Stage Pipeline Transforming Input Spreadsheets into Validated Schedules
- **Visuals:** 6 interconnected stage cards: Ingestion, Reconciliation, Pre-Solve Audit, CP-SAT Solve, Verification, Publishing.

### Spoken Presentation Script
> "Slide 6 illustrates our end-to-end data processing pipeline, divided into six sequential stages.
>
> In **Stage 1: Ingestion**, `StandardwiseParser` and `TeacherwiseParser` read the school's raw Excel spreadsheets using Apache POI, alongside teacher-class mappings and institutional constraints.
>
> In **Stage 2: Reconciliation & Normalization**, our `AllocationReconciler` performs atomic-level ETL validation. School spreadsheets frequently contain teacher naming aliases or small clerical discrepancies. The `NormalizationDictionary` resolves faculty aliases, compares standard-wise curriculum demand against teacher-wise supply, and writes any discrepancies to `reconciliation_discrepancies.csv`.
>
> In **Stage 3: Pre-Solve Axiomatic Audit**, `InputValidator` checks foundational scheduling invariants before touching the solver. For example, it confirms that every class division requires exactly 48 periods per week, giving a total demand of exactly 1,248 periods. Catching mismatches here prevents launching doomed solver runs.
>
> In **Stage 4: Two-Phase CP-SAT Solve**, `CpSatTimetableSolver` builds our decision model. Phase 1 solves hard constraint feasibility in under 4 seconds. Phase 2 warm-starts soft constraint optimization to minimize student fatigue and teacher workload variance.
>
> In **Stage 5: Independent Verification**, `ScheduleValidator` audits every slot from scratch with zero reliance on the solver's internal state, certifying zero hard violations.
>
> Finally, in **Stage 6: Publishing**, the engine generates the master multi-tab Excel workbook, 26 per-class vector PDFs, and 9 audit CSV reports into the user's output directory."

### Technical Deep-Dive
- **Stage 1 (Ingestion):**
  - `StandardwiseParser.java`: Extracts standard-wise period demands per subject for Standards V through X.
  - `TeacherwiseParser.java`: Extracts teacher-assigned workloads and subject allocations.
  - `ClassTeacherListParser.java`: Maps which teacher is designated as the primary Class Teacher for each division.
- **Stage 2 (Reconciliation):**
  - `NormalizationDictionary.java`: Case-insensitive and trimmed name normalization mapping variations (e.g. `'N. B. PATIL'` $\to$ `'PATIL N.B.'`).
  - `AllocationReconciler.java`: Calculates $\Delta = \text{Demand} - \text{Supply}$. If $\Delta \neq 0$, logs exact subject and class discrepancies.
- **Stage 3 (Pre-Solve Axioms):**
  - Total slots invariant: $|C| \times |D| \times |P| = 26 \times 6 \times 8 = 1,248$.
  - Period balance invariant: $\sum_{s} \text{AllottedPeriods}(c, s) = 48 \quad \forall c \in C$.
- **Stage 5 (Verification):**
  - `ScheduleValidator.validateAll(Schedule schedule)`: Iterates through all 1,248 `ScheduleAssignment` records to evaluate H1–H10 and S1–S6.

### Anticipated Committee Q&A
- **Q1: What happens if Stage 2 detects that a standard requests 5 periods of History, but the assigned teacher only has 4 periods allocated?**
  - **Answer:** "Stage 2 logs this discrepancy to `reconciliation_discrepancies.csv` and reports it in the pre-solve summary. If the total class periods do not sum to 48, Stage 3's pre-solve audit halts execution with an explanatory validation error in the Command Center, instructing the administrator to reconcile the missing period before running the solver. This prevents wasted solve cycles on mathematically impossible inputs."
- **Q2: Why is Apache POI used for Excel rather than CSV parsing?**
  - **Answer:** "School administration staff work natively with multi-sheet `.xlsx` workbooks containing formatted tables, merged headers, and stylized rosters. Supporting `.xlsx` directly via Apache POI avoids forcing non-technical users to manually convert files to CSV."

---

## Slide 7: Constraint Programming Formulation (OR-Tools CP-SAT)

### Slide Header & Purpose
- **Category:** Mathematical Modeling
- **Title:** Constraint Formulation with OR-Tools CP-SAT
- **Subtitle:** Mapping Institutional Policies to SAT Decision Variables and Linear Expressions
- **Visuals:** 2 deep-dive columns: Mathematical Decision Model (Variables, Equations, Scale) and Two-Phase Solving Strategy (Phase 1, Hints, Phase 2).

### Spoken Presentation Script
> "Slide 7 delves into the core mathematical modeling of our solver.
>
> We model the timetable using 4-dimensional Boolean decision variables:
> $$x(c, d, p, a) \in \{0, 1\}$$
> where $x$ equals 1 if and only if atomic allocation $a$—which encapsulates class $c$, subject $s$, and teacher $t$—is scheduled in class $c$ on day $d$ at period $p$.
>
> For our production dataset of 26 classes, the model instantiates exactly **18,864 Boolean decision variables** and **14,369 linear constraint equations**.
>
> To solve this efficiently, we implemented a **Two-Phase Solving Strategy**:
> In **Phase 1**, the solver optimizes purely for hard constraint feasibility:
> $$\text{Find } x \quad \text{subject to } \bigwedge_{k=1}^{10} H_k$$
> Because it ignores soft objective penalties during this initial pass, CP-SAT finds a complete, clash-free solution in just 3.9 to 4.7 seconds.
>
> Once Phase 1 succeeds, we extract its Boolean variable assignments and inject them as **warm-start search hints** into Phase 2 using `cpModel.addHint(var, val)`.
>
> In **Phase 2**, the solver minimizes the weighted soft penalty objective:
> $$\text{Minimize } \sum_{i=1}^{6} W_i \cdot \text{Penalty}_i$$
> Starting with a known feasible solution prevents the branch-and-bound search from getting stuck searching for initial feasibility, allowing it to dedicate its search tree exploration to optimizing timetable quality until time expires."

### Technical Deep-Dive
- **Decision Variable Indexing:**
  - Let $C$ be the set of 26 classes, $D = \{0..5\}$ be the 6 days, $P = \{0..7\}$ be the 8 periods.
  - Let $A_c$ be the set of atomic allocations required for class $c$, where each allocation $a = (c, s, t, k)$ specifies subject $s$, teacher $t$, and duration count $k$.
  - Variables are created via:
    ```java
    Literal var = cpModel.newBoolVar("x_c" + c + "_d" + d + "_p" + p + "_a" + aId);
    ```
- **Constraint Representation:**
  - Slot occupancy constraint: Exactly one allocation per slot:
    $$\sum_{a \in A_c} x(c, d, p, a) = 1 \quad \forall c \in C, d \in D, p \in P$$
  - In OR-Tools Java:
    ```java
    LinearExprBuilder slotExpr = LinearExpr.newBuilder();
    for (int aId : classAllocations) { slotExpr.add(x[c][d][p][aId]); }
    cpModel.addEquality(slotExpr, 1);
    ```
- **Warm-Start Injection Mechanism:**
  ```java
  for (Map.Entry<Literal, Integer> entry : phase1Solution.entrySet()) {
      IntVar var = (IntVar) entry.getKey();
      phase2Model.addHint(var, entry.getValue());
  }
  ```
- **Solver Configuration:**
  - `solver.getParameters().setNumWorkers(8)` (utilizes all available CPU cores).
  - `solver.getParameters().setMaxTimeInSeconds(timeLimitSeconds)` (default: 60s).

### Anticipated Committee Q&A
- **Q1: Why use warm-start hints instead of solving both hard and soft constraints together in a single phase?**
  - **Answer:** "A single-phase approach requires the solver to search for a feasible solution while simultaneously minimizing a complex penalty objective function. In dense combinatorial problems, this often delays finding the first feasible solution. By splitting the problem into two phases, Phase 1 guarantees a clash-free schedule in under 4 seconds. Phase 2 then treats that feasible schedule as a known baseline, ensuring the user always receives a valid timetable even if Phase 2 is stopped early."
- **Q2: Does adding search hints prevent the solver from finding a globally optimal solution?**
  - **Answer:** "No. In CP-SAT, `addHint()` provides a suggested initial variable assignment to guide the search heuristics, but it does not constrain the search space. The branch-and-bound engine remains free to explore other branches to find lower objective scores."

---

## Slide 8: Constraint Catalog: Hard Operational Rules (H1 to H10)

### Slide Header & Purpose
- **Category:** Constraint Catalog
- **Title:** Enforced Hard Operational Rules (H1 to H10)
- **Subtitle:** Ten Strict Mathematical Rules Guaranteeing Zero Clashes and Operational Feasibility
- **Visuals:** 10 numbered rule cards organized in two balanced columns with terracotta badges (H1 through H10).

### Spoken Presentation Script
> "Slide 8 details the ten hard operational rules enforced by our solver. Every valid timetable must satisfy all ten rules simultaneously:
>
> - **H1: Class Teacher Period 1**: Whenever a class teacher teaches their own division, they must be assigned to Period 1 to handle morning attendance and administrative duties.
> - **H2: Max 2 Continuous Lectures**: Faculty members cannot teach more than 2 consecutive periods without a rest break. The morning recess automatically resets this counter.
> - **H3: Single Assignment per Slot**: Every class period must have exactly one subject and teacher assigned—no unassigned gaps and no collisions.
> - **H4: Teacher Single-Booking**: A teacher can instruct at most one class division at any given time.
> - **H5: Computer Lab Exclusivity**: Across all 26 classes, at most one class division may be scheduled in the Computer Lab in any single period.
> - **H6 & H7: P.E. Placement Restrictions**: Physical Education is strictly prohibited in morning Period 1, and prohibited in Period 5 immediately following lunch recess.
> - **H8: Max 2 Activity Periods per Day**: A class division cannot have more than two non-academic or activity periods on the same day.
> - **H9: Exact Allocation Fulfillment**: Every curriculum subject allotment count $K$ must be fulfilled exactly.
> - **H10: P.E. and Computer Recess Partitioning**: If a class has both Physical Education and Computer Lab on the same day, one must be before recess and the other after recess to prevent fatigue."

### Technical Deep-Dive
- **Constraint Mathematical Formulations:**
  - **H2 (Consecutive Periods):** For any teacher $t$, day $d$, and three consecutive periods $p, p+1, p+2$ within the same session (before or after recess):
    $$\sum_{c} x(c, d, p, t) + \sum_{c} x(c, d, p+1, t) + \sum_{c} x(c, d, p+2, t) \le 2$$
  - **H4 (Teacher Conflict):** For any teacher $t$, day $d$, period $p$:
    $$\sum_{c \in C} \sum_{a \in A_{c,t}} x(c, d, p, a) \le 1$$
  - **H5 (Shared Resource - Computer Lab):** Let $A_{\text{comp}}$ be all allocations where subject $s = \text{'Computer'}$:
    $$\sum_{c \in C} \sum_{a \in A_{c, \text{comp}}} x(c, d, p, a) \le 1 \quad \forall d \in D, p \in P$$
  - **H6 (P.E. Period 1):** For $p = 0$, $x(c, d, 0, a) = 0 \quad \forall a \in A_{\text{PE}}$.
  - **H7 (P.E. After Recess):** For $p = 4$ (Period 5), $x(c, d, 4, a) = 0 \quad \forall a \in A_{\text{PE}}$.
  - **H9 (Total Subject Allotment):** For each allocation $a = (c, s, t)$ with required periods $K$:
    $$\sum_{d \in D} \sum_{p \in P} x(c, d, p, a) = K$$

### Anticipated Committee Q&A
- **Q1: How does the model handle the recess boundary in constraint H2 (maximum 2 continuous lectures)?**
  - **Answer:** "Periods 1 to 4 occur before morning recess, and Periods 5 to 8 occur after recess. The recess break is 20 minutes long, providing adequate rest. Therefore, the constraint $\sum_{i=0}^2 \text{assignment}(p+i) \le 2$ is only enforced across consecutive triplets within the morning block $\{1,2,3\}$ and $\{2,3,4\}$, and within the afternoon block $\{5,6,7\}$ and $\{6,7,8\}$. Periods 3, 4, and 5 do not form an invalid triplet because the recess falls between periods 4 and 5."
- **Q2: Why is Computer Lab modeled as a hard constraint H5 rather than an open room assignment?**
  - **Answer:** "SIES High School operates a single dedicated Computer Laboratory with 40 workstations. Since all 26 class divisions must share this single physical facility, scheduling two classes in the lab simultaneously is physically impossible, requiring an absolute hard upper bound of 1."

---

## Slide 9: Pedagogical Quality Optimization (Soft Objectives S1 to S6)

### Slide Header & Purpose
- **Category:** Optimization Objectives
- **Title:** Pedagogical Quality Optimization (S1 to S6)
- **Subtitle:** Balancing Faculty Workloads and Enhancing Student Learning Experience
- **Visuals:** Top mathematical objective equation banner, followed by 6 objective cards with assigned penalty weights.

### Spoken Presentation Script
> "While Slide 8 showed how we ensure legal schedules, Slide 9 explains how we make timetables pedagogically effective.
>
> We formulate an objective function that minimizes the weighted sum of penalties across six soft criteria:
> $$\text{Minimize } \sum_{i=1}^{6} W_i \cdot \text{Penalty}_i$$
>
> - **S1: Subject Spread Across Week (Weight: 100)**: For major subjects taught 5 or 6 times a week, we penalize scheduling more than one lecture on the same day. Spreading subjects evenly prevents student cognitive fatigue.
> - **S2: Non-Consecutive 2-Period Days (Weight: 80)**: For subjects with exactly 2 lectures a week, such as Drawing or Value Education, we penalize scheduling them on adjacent days. Spacing them by at least 2 days improves learning retention.
> - **S3: Repeated Lecture Spacing (Weight: 50)**: If a subject must be taught twice in a single day, we penalize scheduling them back-to-back unless explicitly configured as a practical double-period.
> - **S4: Teacher Daily Workload Balance (Weight: 40)**: We penalize variance in a teacher's daily workload around their weekly average. This avoids erratic schedules where a teacher has 6 periods on Monday but only 1 period on Tuesday.
> - **S5: Teacher Idle Gap Minimization (Weight: 30)**: We penalize unproductive idle periods between a teacher's first and last period of the day.
> - **S6: Class Academic Variety (Weight: 20)**: We penalize scheduling more than three consecutive heavy academic subjects without a linguistic or co-curricular break."

### Technical Deep-Dive
- **Penalty Linearization in CP-SAT:**
  - **S1 (Subject Day Clustering):** For subject $s$ in class $c$ on day $d$, define count $y(c, s, d) = \sum_{p} x(c, d, p, s)$. If target maximum is 1:
    $$\text{excess}(c, s, d) \ge y(c, s, d) - 1, \quad \text{excess} \ge 0$$
    The term $W_{\text{S1}} \times \text{excess}(c, s, d)$ is added to the minimization objective.
  - **S4 (Workload Deviation):** Let $T_{\text{avg}}(t) = \lfloor \text{WeeklyHours}(t) / 6 \rfloor$. For each day $d$:
    $$\text{dev}^{+}(t, d) \ge \text{dailyHours}(t, d) - (T_{\text{avg}}(t) + 1)$$
    $$\text{dev}^{-}(t, d) \ge (T_{\text{avg}}(t) - 1) - \text{dailyHours}(t, d)$$
    Penalizes days with loads significantly above or below the teacher's average.
  - **S5 (Teacher Idle Gaps):**
    $$\text{Span}(t, d) = \text{LastPeriod}(t, d) - \text{FirstPeriod}(t, d) + 1$$
    $$\text{IdleGaps}(t, d) = \text{Span}(t, d) - \text{TotalLectures}(t, d)$$
    Penalizing $\text{IdleGaps}$ compresses the teacher's teaching hours into a tighter block.

### Anticipated Committee Q&A
- **Q1: How were the relative weights (100, 80, 50, 40, 30, 20) determined?**
  - **Answer:** "The weights reflect academic priorities established with the school administration. Subject spread (S1 at weight 100) and multi-day spacing (S2 at weight 80) directly impact student learning and syllabus pacing, making them top priorities. Faculty comfort criteria like workload balancing (S4 at 40) and idle gap compression (S5 at 30) are important second-tier objectives, while academic variety (S6 at 20) provides fine-tuning."
- **Q2: Does the solver always find a solution with zero soft penalty?**
  - **Answer:** "In a dense timetable with 26 classes and 44 teachers, a zero-penalty solution is mathematically impossible because several objectives naturally compete. For example, compressing teacher idle gaps (S5) can push subjects onto days that slightly conflict with subject spread (S1). CP-SAT finds Pareto-optimal trade-offs that minimize the aggregate weighted penalty."

---

## Slide 10: Modern User Interface & Studio Features

### Slide Header & Purpose
- **Category:** User Experience
- **Title:** Command Center & Interactive Timetable Studio
- **Subtitle:** Modern Desktop User Interface Built with React 19, TypeScript, and SIES Visual Identity
- **Visuals:** 3 feature columns: Command Center, School Setup View, and Timetable Studio & Editing; bottom workflow banner.

### Spoken Presentation Script
> "Slide 10 highlights the user experience of the desktop application.
>
> In the **Command Center**, school administrators see a clean status dashboard. It verifies the health of the local backend, automatically detects whether input spreadsheets are ready in `%APPDATA%`, and provides a one-click generation trigger. During solving, it shows live multi-stage progress so the user always knows what the system is doing.
>
> The **School Setup View** allows administrators to configure their academic structure. They can modify standards and division counts, adjust the bell schedule—including period timings and recess placement—and manage the faculty directory and subject categories.
>
> Finally, the **Timetable Studio** provides an interactive weekly grid. Users can view schedules across all 26 classes or inspect individual teacher timetables.
>
> If an administrator needs to make an adjustment during the term, they can swap any two slots directly in the grid. Our backend validates prospective swaps in real time against constraints H1 through H10. If the move is valid, it applies cleanly with complete Undo and Redo support. If it violates a constraint—like double-booking a teacher—the UI explains the conflict and preserves the valid schedule."

### Technical Deep-Dive
- **Frontend Architecture:**
  - Written in React 19 with strict TypeScript typing (`types.ts`).
  - Compiled using Vite 6 in just 515 milliseconds.
  - Cleaned with Oxlint across all 21 UI source files (0 errors, 0 warnings).
  - Designed around SIES brand identity design tokens: Deep Slate (`#0F172A`), Card Slate (`#1E293B`), SIES Terracotta (`#E0564C`), and Gold (`#E5B869`).
- **Manual Editing Engine (`ManualTimetableModel.java`):**
  - Thread-safe state managed using `ReentrantReadWriteLock`.
  - Maintains `historyStack` and `redoStack` containing immutable `Schedule` snapshot copies.
  - Implements atomic `swapSlots(int classId, Day day1, int p1, Day day2, int p2)` and `moveSlot(...)`.
  - Calls `ScheduleValidator.validateAssignment()` before modifying state. If a hard violation occurs, it aborts the operation and returns HTTP 409 with the violation details.

### Anticipated Committee Q&A
- **Q1: How does the frontend communicate with the backend during manual edits?**
  - **Answer:** "When two cells are selected in `TimetableStudio.tsx`, the frontend sends a `POST /api/edit/swap` payload containing class ID, day, and period coordinates. The Java backend checks the swap against `ScheduleValidator`. If valid, it returns the updated schedule and a HTTP 200 status code. The React state then updates cleanly. If invalid, it returns HTTP 409 with human-readable error messages."
- **Q2: Does undo/redo persist across application restarts?**
  - **Answer:** "Undo/Redo history is maintained in memory for the active editing session. When the administrator clicks 'Save Schedule', the current verified timetable is serialized to disk in `%APPDATA%` and exported to the output files, ensuring persistence across restarts."

---

## Slide 11: Production Deliverables & Multi-Format Exporters

### Slide Header & Purpose
- **Category:** Export Suite
- **Title:** Production Deliverables & Multi-Format Exporters
- **Subtitle:** Automated High-Fidelity Publishing of Excel Workbooks, Class PDFs, and Audit Reports
- **Visuals:** 3 exporter columns: Master Excel Workbook, Printable Class PDFs, Machine-Readable CSVs; bottom storage path banner.

### Spoken Presentation Script
> "Slide 11 shows the final deliverables generated by the system. A timetable is only as useful as its delivery format to teachers and students. We built an automated multi-format publishing engine:
>
> First, a **Master Excel Workbook (`timetable.xlsx`)** generated via Apache POI. It features a multi-sheet layout including an executive summary, all 26 class timetables, and master teacher matrices. It includes custom cell styling, clear period boundaries, and colored recess markers.
>
> Second, **Printable Vector PDFs** generated via OpenPDF. The system produces 26 individual, landscape-oriented PDF documents—one for each division from 5-A to 10-D. Each PDF includes the SIES school header, class teacher name, bell timings, and clear subject-teacher tables ready for classroom display.
>
> Third, a **CSV Audit Suite** generating 9 machine-readable reports. These include student and teacher timetable matrices, reconciliation discrepancy reports, and validation logs that can be audited by academic committees or imported into other school databases.
>
> All deliverables are generated completely offline into the user's local application directory with zero cloud telemetry."

### Technical Deep-Dive
- **Excel Generation (`ExcelTimetableExporter.java`):**
  - Uses `org.apache.poi.xssf.usermodel.XSSFWorkbook`.
  - Builds indexed cell styles: `headerStyle`, `recessStyle` (distinct fill pattern), `boldBorderedStyle`.
  - Aggregates teacher workload matrices showing period distribution by day.
- **Vector PDF Generation (`PdfTimetableExporter.java`):**
  - Uses `com.lowagie.text` (OpenPDF 1.3.39).
  - Landscape A4 geometry (`PageSize.A4.rotate()`).
  - Strict 9-column layout: Day, Periods 1 to 4, Recess column, Periods 5 to 8.
  - Embeds metadata: Academic Year, Standard & Division, Class Teacher name, and Bell Timings.
- **CSV Exporters (`StudentTimetableCsvExporter.java`, `TeacherTimetableCsvExporter.java`):**
  - Streams UTF-8 rows: `Class,Day,Period,StartTime,EndTime,Subject,Teacher`.
  - `reconciliation_discrepancies.csv`: Documents upstream standard-versus-teacher allocation differences.
  - `validation_report.csv`: Complete audit record documenting constraint verification results.

### Anticipated Committee Q&A
- **Q1: Why did you choose OpenPDF over alternatives like iText or Apache PDFBox?**
  - **Answer:** "OpenPDF is a clean, LGPL-licensed fork of iText 2.1.7 with no commercial licensing restrictions. Unlike PDFBox, which requires low-level manual positioning of text coordinates, OpenPDF provides high-level table abstractions (`PdfPTable`, `PdfPCell`) that make generating clean, bordered timetable grids straightforward."
- **Q2: Where are output files saved on the user's machine?**
  - **Answer:** "Output files are written to `%APPDATA%/com.sies.timetable.desktop/output/`. The UI provides an 'Open Output Folder' button that opens Windows File Explorer directly to this directory, making it easy for non-technical users to access their files."

---

## Slide 12: Independent Validation & Engineering Verification

### Slide Header & Purpose
- **Category:** Reliability & Verification
- **Title:** Independent Validation & Automated Testing
- **Subtitle:** Zero-Trust Architecture and 100% Automated Test Suite Passing across All Components
- **Visuals:** 2 deep-dive columns: Zero-Trust ScheduleValidator and Verified Automated Test Suite; bottom matrix showing all 34 passing tests across 9 test suites.

### Spoken Presentation Script
> "Slide 12 covers our quality assurance and validation architecture. In software engineering, you never simply trust a solver's internal claims of feasibility.
>
> We implemented a **Zero-Trust ScheduleValidator**. After CP-SAT finishes, `ScheduleValidator.java` inspects the candidate `Schedule` object from scratch. It knows nothing about the solver's internal variables or decision trees. It inspects every assignment coordinate against all 10 hard constraints and 6 soft objectives independently. If an invalid assignment somehow slipped through, the validator catches it, provides the exact day, period, and teacher coordinates, and prevents flawed files from being published.
>
> Furthermore, we built a comprehensive automated test suite. Across **9 test suites**, we have **34 out of 34 Java tests passing with 0 errors and 0 failures**. This includes:
> - 14 REST API contract tests verifying all endpoints and error codes in `HttpServerIntegrationTest`.
> - End-to-end integration tests that run the entire pipeline from raw Excel files to final schedules.
> - Empirical benchmark tests that measure solve times and cancellation responsiveness.
> - Dynamic structure tests verifying arbitrary grade and division layouts.
>
> In addition, our frontend code passes Oxlint with zero errors and zero warnings across 21 TypeScript source files."

### Technical Deep-Dive
- **Zero-Trust Architecture (`ScheduleValidator.java`):**
  - Audits invariant: $\text{Slots}(c) = 48 \quad \forall c \in C$.
  - Teacher clash checker:
    ```java
    Map<String, Set<Teacher>> teacherMap = new HashMap<>();
    // Builds composite key: day + "_" + period
    // Asserts set.add(teacher) returns true; flags duplicates immediately.
    ```
  - Lab exclusivity checker: Asserts that at most one class is assigned to subject `'Computer'` for any `day + "_" + period` key.
- **Automated Test Suite Breakdown (Maven Surefire: 34 tests, 0 failures):**
  1. `DynamicStructureSolverTest` (2 tests): Verifies configurable standard and division counts.
  2. `EndToEndSolverTest` (1 test): Complete ingestion $\to$ solve $\to$ validation $\to$ export pipeline.
  3. `ExcelTimetableExporterTest` (1 test): Verifies POI workbook sheet structure, headers, and formulas.
  4. `HttpServerIntegrationTest` (14 tests): Verifies `/api/status`, `/api/solve/start`, `/api/solve/progress`, `/api/schedule/class`, `/api/edit/swap`, `/api/edit/undo`, `/api/shutdown`, CORS headers, and 400/404/409 error handling.
  5. `NormalizationTest` (2 tests): Alias mapping and whitespace/punctuation resilience.
  6. `ReconciliationTest` (5 tests): Discrepancy detection between teacher workloads and standard allocations.
  7. `ScheduleValidatorTest` (3 tests): Verifies that artificially injected clashes in H1–H10 are caught correctly.
  8. `SolverPerformanceBenchmarkTest` (3 tests): Benchmarks Phase 1 and Phase 2 solve times, memory usage, and cancellation speed.
  9. `TimetableServiceTest` (3 tests): Service-layer orchestration and lifecycle management.

### Anticipated Committee Q&A
- **Q1: How do you verify that your tests aren't simply passing on trivial mock data?**
  - **Answer:** "`EndToEndSolverTest` and `SolverPerformanceBenchmarkTest` run directly against the full, production-scale SIES High School dataset with 26 classes, 44 faculty members, and 1,248 periods. They compile the full 18,864 variables and verify the resulting schedule from scratch using `ScheduleValidator`."
- **Q2: How responsive is solver cancellation if a user aborts an in-progress solve?**
  - **Answer:** "Our `SolverPerformanceBenchmarkTest` specifically tests cancellation responsiveness. We register an atomic cancellation listener via `cpSolver.stopSearch()`. When an abort signal is sent, the solver stops searching and returns cleanly in under 32 milliseconds."

---

## Slide 13: Desktop Packaging & Empirical Performance

### Slide Header & Purpose
- **Category:** Deployment & Benchmarks
- **Title:** Desktop Packaging & Empirical Performance
- **Subtitle:** Self-Contained Windows Distribution Coupled with Rigorous Benchmark Profiling
- **Visuals:** Top 4 KPI metric cards (58.5 MB installer, 34–48s solve, 34/34 tests passing, 27.5 MB frontend RAM); bottom split panels on Packaging and Empirical Benchmarks.

### Spoken Presentation Script
> "Slide 13 presents our packaging strategy and benchmark results.
>
> On the deployment side:
> - Our total **NSIS installer is just 58.52 MB**.
> - It bundles a **private 48 MB Java Runtime Environment** created using `jlink`, containing only the 11 modules the application requires—such as `java.base`, `java.desktop`, `jdk.httpserver`, and `jdk.zipfs`, which OR-Tools needs to extract its native Windows DLLs.
> - The installer is configured with `currentUser` scope, meaning it installs cleanly into `%LOCALAPPDATA%` without requiring Windows administrator UAC elevation.
> - The user needs zero prerequisites: no existing Java, no Python, and no Node.js.
>
> On the performance side:
> - For a **Small Dataset** of 4 classes, the model compiles 1,232 variables and reaches full feasibility in **0.15 seconds**, completing optimization in 1.79 seconds.
> - For the full **Production Dataset** of 26 classes, the model compiles 18,864 variables and reaches Phase 1 hard-constraint feasibility in **3.92 seconds**. Complete soft-objective optimization finishes in **34 to 48 seconds**.
> - Most importantly, across all benchmark runs, we achieved **exactly 0 hard constraint violations**."

### Technical Deep-Dive
- **`jlink` Modular Stripping:**
  ```bash
  jlink --module-path $JAVA_HOME/jmods \
        --add-modules java.base,java.desktop,java.logging,java.net.http,java.prefs,java.scripting,java.sql,jdk.httpserver,jdk.unsupported,jdk.zipfs \
        --strip-debug --no-man-pages --no-header-files --compress 2 \
        --output dist-jre
  ```
  Reduces standard ~250 MB JDK down to a clean 48.2 MB private JRE.
- **Empirical Benchmark Profiling (Intel Core i7 / 16 GB RAM / Windows 11):**
  - **Model Construction Time:** 412 ms (instantiating 18,864 variables and 14,369 constraints).
  - **Phase 1 Feasibility Duration:** 3.92s (first clash-free integer solution found).
  - **Phase 2 Soft Optimization Duration:** 29.90s.
  - **Total Generation Duration:** 34.09s (well within the 60s timeout).
  - **Memory Footprint:** Peak Java heap usage during solve is ~184 MB; settles to ~62 MB post-GC. Tauri frontend uses 27.5 MB RAM.
  - **Cancellation Latency:** 31.4 ms.

### Anticipated Committee Q&A
- **Q1: Why was `jdk.zipfs` explicitly required in the custom JRE?**
  - **Answer:** "Google OR-Tools packages its native C++ runtime (`jniortools.dll`) inside the JAR file. At runtime, OR-Tools uses Java's NIO ZipFileSystem provider to extract the native library into a temporary directory before loading it via `System.load()`. Without `jdk.zipfs`, the runtime throws a `ProviderNotFoundException` on startup. We identified and verified this dependency during our packaging audit."
- **Q2: Why install to `%LOCALAPPDATA%` instead of `Program Files`?**
  - **Answer:** "Installing to `Program Files` requires administrative privileges (`adminMode`). In school environments, staff computers often have restricted user accounts without administrative rights. By setting `installMode: currentUser` in NSIS, the software installs cleanly into `%LOCALAPPDATA%/Programs/SIES Timetable Generator` without UAC prompts, while user data and exports are kept isolated in `%APPDATA%`."

---

## Slide 14: System Limitations, Future Roadmap & Conclusion

### Slide Header & Purpose
- **Category:** Summary & Future Outlook
- **Title:** System Limitations, Future Roadmap & Conclusion
- **Subtitle:** Reflecting on Operational Capabilities and Academic Engineering Impact
- **Visuals:** 3 structured outcome columns: Current Limitations, Future Roadmap, and Project Conclusion.

### Spoken Presentation Script
> "To conclude our presentation, Slide 14 reviews the current system boundaries, future roadmap, and overall outcomes.
>
> In the spirit of engineering honesty, we acknowledge four **Current Limitations**:
> 1. It is designed as a standalone desktop application on local loopback; it is not currently a multi-tenant cloud SaaS platform.
> 2. The local daemon processes one active solve job at a time to keep all CPU cores focused on the solver.
> 3. As an academic project, the executable is not signed with an expensive commercial EV Authenticode certificate, which produces the standard Windows SmartScreen prompt on first run.
> 4. Physical room exclusivity is currently modeled for the Computer Lab; science laboratories are modeled as standard classrooms.
>
> Our **Future Roadmap** includes:
> - Extending resource modeling to Chemistry, Physics, and Biology laboratories.
> - Building a Dynamic Substitution Engine to re-optimize schedules when a teacher reports sick morning-of.
> - Providing an optional cloud sync module for multi-campus school systems.
>
> In **Conclusion**:
> We have engineered a mathematically proven, production-grade scheduling platform that replaces four weeks of arduous manual committee scheduling with a 40-second autonomous solve. It guarantees 100% adherence to all ten hard operational rules, verifies every assignment via an independent zero-trust audit, and delivers a turnkey desktop experience for school administrators.
>
> Thank you for your time. I am now open to your questions."

### Technical Deep-Dive
- **Code Signing Economics:** A commercial Extended Validation (EV) code-signing certificate requires hardware token storage and costs \$300–\$500 annually, which is impractical for an academic capstone. For production distribution, signing with Microsoft Authenticode eliminates SmartScreen prompts.
- **Substitution Engine Mathematical Concept:**
  - When teacher $t_{\text{absent}}$ is absent on day $d$, fix all other teacher assignments as immutable hard constraints:
    $$x(c, d', p, a) = \text{CurrentSchedule}(c, d', p, a) \quad \forall d' \neq d$$
  - Solve a localized mini-CP-SAT model for day $d$ that reassigns free faculty to cover absent slots with minimal disruption.

### Anticipated Committee Q&A
- **Q1: If you were to adapt this application for a university with 500 faculty members and 5,000 students, what architectural changes would be needed?**
  - **Answer:** "A university with 5,000 students uses modular elective credit enrollments rather than fixed cohort divisions. Mathematically, this shifts the model from class-based timetabling to Course Timetabling (ITC-2007 formulation), requiring student-course enrollment matrices. Architecturally, we would migrate the solver daemon to a distributed cloud worker queue (such as Celery or RabbitMQ with Redis) while decomposing the problem standard-by-standard using Benders decomposition or column generation."
- **Q2: What was the single most difficult engineering challenge in this project?**
  - **Answer:** "Balancing the tension between mathematical solver performance and desktop usability. Specifically, ensuring that OR-Tools native C++ binaries loaded reliably across different Windows machines without external prerequisites, while keeping the child process safely managed under the Windows kernel so no zombie processes are left behind on crash. Solving this required deep integration across Java, Rust, Tauri, and native Win32 APIs."

---

## Master Defense Q&A Reference Bank

### 1. Optimization & Mathematical Formulation
* **Why CP-SAT over Mixed-Integer Linear Programming (MILP)?**  
  MILP solvers (like CBC, GLPK, or Gurobi) excel at continuous linear problems. However, secondary school timetabling is purely discrete and combinatorial, characterized by dense disjunctive constraints (e.g., "teacher A cannot teach if period is P" or "at most 2 consecutive lectures"). CP-SAT's SAT solver native clause learning prunes discrete permutations orders of magnitude faster than MILP branch-and-bound with linear relaxations.
* **How is symmetry breaking handled in the solver?**  
  When multiple identical elective or activity subjects exist within a standard (e.g., two periods of Drawing), the solver could waste time exploring permutations that yield identical schedules. We break symmetry by imposing an ascending period order constraint on identical unindexed allocations within the same division: $p(\text{slot}_1) < p(\text{slot}_2)$.

### 2. Software Architecture & Concurrency
* **How does `TimetableHttpServer` handle concurrent requests?**  
  The HTTP server uses Java's built-in executor service. For long-running operations like `/api/solve/start`, the solver runs asynchronously on a dedicated worker thread while the endpoint immediately returns HTTP 202 Accepted. The frontend then polls `/api/solve/progress` to display real-time progress without blocking the network connection.
* **Can a user trigger two simultaneous solves and crash the system?**  
  No. `TimetableService.java` enforces an `AtomicBoolean isSolving` guard. If a solve request arrives while another is in progress, the service immediately returns HTTP 409 Conflict with the message `"A timetable generation job is already active."`

### 3. Engineering Quality & Reproducibility
* **How do you ensure timetable generation is reproducible?**  
  The CP-SAT solver parameters include a deterministic random seed (`setRandomSeed(42)`). Given the same input spreadsheets and constraint configuration, the solver explores the search tree in the exact same sequence, producing bit-for-bit identical schedules.
* **How is data protected against corruption if the app is closed mid-export?**  
  Exporters generate documents into a temporary staging file (`.tmp`) first. Only after the document streams finish writing and pass checksum verification is the file atomically renamed to `timetable.xlsx` or the respective PDF path, preventing corrupted, partial files.
