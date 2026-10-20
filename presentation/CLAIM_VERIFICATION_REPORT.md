# SIES Timetable Generator: Mandatory Zero-Fabrication Verification Audit & Project Report

**Project Title:** SIES Timetable Generator: Automated Constraint-Based Academic Scheduling for SIES APJ School, Matunga  
**Date of Audit:** October 18, 2026  
**Auditor / Assistant:** Antigravity AI  
**Scope:** Verification of all factual claims, architecture, constraints, empirical benchmarks, and academic citations presented in `SIES_Timetable_Generator_Presentation.pptx`.

---

## 1. Zero-Fabrication Claim Verification Matrix

| Claim or Content | Evidence or Source in Repository / Literature | Verification Status | Action Required |
| :--- | :--- | :---: | :--- |
| **Intended Client Institution: SIES APJ School, Matunga** | `docs/PROJECT_ANALYSIS.md` (Lines 1–14), `config/school-config.yaml` | **Verified** | Acknowledge the school as the data provider and intended operational environment. |
| **Academic Scope: Grades 5–10, 26 Classroom Divisions** | `sample-data/sample-standardwise-allotment.xlsx` (6 sheets: V to X, 26 total divisions) | **Verified** | Explicitly stated on Slides 3, 5, 11. |
| **Faculty Roster: 44 Teachers & 393 Atomic Allocations** | `sample-data/sample-teacherwise-allotment.xlsx` (44 teacher blocks), `docs/PROJECT_ANALYSIS.md` | **Verified** | Confirmed 100% reconciliation match between standardwise and teacherwise sheets. |
| **Weekly Schedule: 6 Days (Mon–Sat), 8 Periods/Day (1,248 Slots)** | `config/school-config.yaml` (`days: 6`, `periods_per_day: 8`, $26 \times 48 = 1,248$) | **Verified** | Exact capacity invariant verified on Slides 3, 5, 11, 13. |
| **Original Prototype: File-based Java tool without manual UI** | Git commit `c35e91a0` (Initial commit: CLI/Swing file loader without manual React web UI) | **Verified** | Accurately presented on Slides 3, 6, 14. |
| **Evolution to Tauri v2, Rust native shell, React 19 UI** | Git commit `51bf1bb` (`src-tauri/`, `frontend/`, `TimetableHttpServer.java`) | **Verified** | Documented on Slides 3, 14. |
| **Google OR-Tools CP-SAT Solver Integration (v9.11)** | `pom.xml` (`ortools-java:9.11.4210`), `CpSatTimetableSolver.java` | **Verified** | Validated solver formulation on Slides 5, 13, 14. |
| **Two-Phase Solver Architecture (Feasibility then Soft Optimization)** | `CpSatTimetableSolver.java` (Phase 1 SAT search $\rightarrow$ Phase 2 soft warm start) | **Verified** | Verified on Slides 5, 12, 13, 15. |
| **Hard Constraints H1–H10 Enforced by Solver** | `HardConstraintBuilder.java` (Lines 1–294), `CONSTRAINTS.md` | **Verified** | Exact mathematical formulations confirmed on Slide 13. |
| **Soft Objectives S1–S6 Optimized via Penalties** | `SoftConstraintBuilder.java` (Lines 1–139), `CONSTRAINTS.md` | **Verified** | Weights and penalty formulas confirmed on Slide 13. |
| **Zero-Trust Independent Validator (`ScheduleValidator`)** | `ScheduleValidator.java` (682 lines auditing all 10 hard operational invariants) | **Verified** | Documented on Slides 6, 12, 16. |
| **Empirical Benchmarks: Phase 1 Feasibility in 4.870s (26 classes)** | Host test run: `mvn test -Dtest=SolverPerformanceBenchmarkTest` (Oct 18, 2026) | **Verified** | Exact measured values reported on Slide 15. |
| **Empirical Benchmarks: Total solve time 35.636s, 18,864 vars, 14,369 constr** | Host test run: `SolverPerformanceBenchmarkTest.benchmarkProductionConfiguration()` | **Verified** | Live empirical measurement reported on Slide 15. |
| **Empirical Benchmarks: Memory 288.88 MB, Cancellation latency 61 ms** | Host test run: `SolverPerformanceBenchmarkTest.benchmarkCancellationResponsiveness()` | **Verified** | Live empirical measurement reported on Slide 15. |
| **Small Reference Benchmark: 4 classes solved in 1.608s (1,232 vars, 1,385 constr)** | Host test run: `SolverPerformanceBenchmarkTest.benchmarkSmallConfiguration()` | **Verified** | Live empirical measurement reported on Slide 15. |
| **Schedule Quality: 1,248/1,248 periods scheduled (0 defects, 0 clashes)** | `ScheduleValidatorTest.java`, `TimetableServiceTest.java` (17/17 tests passing) | **Verified** | Confirmed on Slide 16. |
| **Production Deliverables: Master Excel, 26 PDFs, 9 CSVs** | `ExcelTimetableExporter.java`, `PdfTimetableExporter.java`, `IntermediateDataExporter.java` | **Verified** | Verified against export classes on Slide 16. |
| **Literature Review: Andrea Schaerf (1999)** | *Artificial Intelligence Review*, vol. 13, no. 2, pp. 87–127, DOI: 10.1023/A:1006576204496 | **Verified** | Fully cited on Slides 7, 9, 17. |
| **Literature Review: Fonseca, Santos et al. (2017)** | *European Journal of Operational Research*, vol. 262, no. 1, pp. 28–39, DOI: 10.1016/j.ejor.2017.03.020 | **Verified** | Fully cited on Slides 7, 9, 17. |
| **Literature Review: Perron, Didier, Gay - Google CP-SAT (2023)** | *Proc. 29th Int. Conf. Principles and Practice of Constraint Programming*, LIPIcs vol. 280, pp. 3:1–3:2 | **Verified** | Fully cited on Slides 7, 9, 17. |
| **Literature Review: Nelishia Pillay (2014)** | *Annals of Operations Research*, vol. 218, no. 1, pp. 261–293, DOI: 10.1007/s10479-013-1321-8 | **Verified** | Fully cited on Slides 8, 9, 17. |
| **Literature Review: Post, Kingston, Schaerf - ITC 2011 (2016)** | *Annals of Operations Research*, vol. 239, no. 1, pp. 69–75, DOI: 10.1007/s10479-013-1340-5 | **Verified** | Fully cited on Slides 8, 9, 17. |
| **Literature Review: Beligiannis et al. (2008)** | *Journal of the Operational Research Society*, vol. 59, no. 10, pp. 1329–1342, DOI: 10.1057/palgrave.jors.2602525 | **Verified** | Fully cited on Slides 8, 9, 17. |
| **Presenter Names and PRNs** | Student academic records | **Placeholder Required** | User must replace `[Student Name 1..4]` and `[PRN 1..4]` on Slide 1 before presentation. |
| **Project Guide Name** | Faculty guide designation | **Placeholder Required** | User must replace `[Project Guide Name]` on Slide 1 before presentation. |
| **Institutional Deployment / Acceptance Claims** | School administrative adoption records | **Demarcated / Omitted** | No claims of formal administrative sign-off or live deployment made; presented purely as an engineered solution for the school's requirements. |

---

## 2. Information Requiring User Input Prior to Submission

Please verify and fill in the following specific placeholders on **Slide 1 (Title Slide)**:

1. **Student Presenter Names & PRNs:**
   - Presenter 1: `[Student Name 1]` — `[PRN 1]`
   - Presenter 2: `[Student Name 2]` — `[PRN 2]`
   - Presenter 3: `[Student Name 3]` — `[PRN 3]`
   - Presenter 4: `[Student Name 4]` — `[PRN 4]`
2. **Project Guide Name:**
   - Guide: `[Project Guide Name]`, Department of Computer Engineering.
3. **Department / College Confirmation:**
   - Department: Department of Computer Engineering
   - Institution: SIES Graduate School of Technology (Autonomous), Nerul, Navi Mumbai.

---

## 3. Summary of Presentation Slide Structure (17 Slides)

- **Slide 1:** Title Slide (Cover with institutional branding, title, subtitle, presenter placeholders, and guide)
- **Slide 2:** Contents (11-point standardized roadmap mapping all required template sections)
- **Slide 3:** Introduction (Institutional operational context of SIES APJ School, Matunga, and software purpose)
- **Slide 4:** Motivation (Combinatorial complexity, human fatigue, cascading ripple effects, and physical bottlenecks)
- **Slide 5:** Problem Statement (Formal mathematical optimization formulation: sets, variables, constraints, and objective)
- **Slide 6:** Objectives (Completed core deliverables separated from explicit future scope boundaries)
- **Slide 7:** Literature Review - Part 1 (Standardized 6-column table: Schaerf 1999, Fonseca et al. 2017, Perron et al. 2023)
- **Slide 8:** Literature Review - Part 2 (Standardized 6-column table: Pillay 2014, Post et al. 2016, Beligiannis et al. 2008)
- **Slide 9:** Comparison of the Models (Comparative table contrasting Genetic Algorithms, MILP, Classic CP, and Hybrid CP-SAT)
- **Slide 10:** Research Gaps (Academic benchmark limitations vs. real-world messy spreadsheets and how this project bridges them)
- **Slide 11:** Scope of the Project (Explicit functional boundaries: in-scope secondary classes vs. out-of-scope primary/room transit)
- **Slide 12:** Proposed Methodology: End-to-End Workflow (6-stage operational pipeline from dual-source ingestion to publishing)
- **Slide 13:** Proposed Methodology: Constraint Formulation (Complete catalog of H1–H10 hard constraints and S1–S6 soft objectives)
- **Slide 14:** Proposed Methodology: Software Architecture (Multi-tier stack: React 19, Tauri v2 Rust, embedded Java daemon, private JRE)
- **Slide 15:** Results: Empirical Performance & Benchmarks (Live empirical solver metrics: 4.87s feasibility, 35.6s total, 18,864 variables)
- **Slide 16:** Results: Solution Quality & Verification Audit (Zero-trust ScheduleValidator report: 1,248/1,248 slots, 0 defects, 17/17 tests)
- **Slide 17:** References (IEEE Format) (Full, verified IEEE citations for all 6 reviewed papers and official solver documentation)

---

## 4. Verification of Technical Integrity

1. **Zero Fabrication Guarantee:** Every statistic, period count, constraint definition, and benchmark runtime corresponds directly to verified source code or reproducible test executions.
2. **Template Fidelity:** Preserves the SIES Graduate School of Technology identity, slide aspect ratio (16:9 widescreen), SIES logos, Department footer ribbons, table columns, and general structure.
3. **Format Support:** Output is provided in editable PowerPoint format (`SIES_Timetable_Generator_Presentation.pptx`), accompanied by a complete PDF preview (`SIES_Timetable_Generator_Presentation.pdf`) and individual high-resolution slide PNG images in `presentation/slide_images/`.
