# Secondary School Timetable Generator — Technical Analysis & Data Specification

## 1. Executive Summary & Source-of-Truth Hierarchy

This project provides a robust, maintainable, and mathematically sound Java timetable generation application using constraint programming for secondary schools (Grades 5 to 10).

### Source-of-Truth Priority Hierarchy:
1. **Priority 1: Reconciled Allocation Data** (`sample-data/sample-standardwise-allotment.xlsx`, `sample-data/sample-teacherwise-allotment.xlsx`) — Highest authority for classes, subjects, teachers, and period counts.
2. **Priority 2: Institutional Scheduling Specifications** (`sample-data/sample-constraints.csv`, `config/school-config.yaml`) — Primary authority for institutional scheduling rules and hard/soft constraint specifications.
3. **Priority 3: Class & Free Teacher Rosters** (`sample-data/sample-classteacher-list.xlsx`) — Authority for class teacher assignments and substitution rosters.
4. **Priority 4: Historical Schedule Evidence** — Reference for period timings, bell schedules, and schedule rhythm.
5. **Priority 5: Configurable Operational Assumptions** — Used only where higher priorities are silent, made explicit and configurable.

---

## 2. File Inventory & Structural Analysis

| File Name | Format | Primary Role | Key Insights & Data Extracted |
|-----------|--------|--------------|-------------------------------|
| `config/school-config.yaml` | YAML | Rules / Configuration | Defines bell timings, days (Mon–Sat), periods per day (8), recess location (after period 4), subject taxonomy, and solver parameters. |
| `sample-data/sample-standardwise-allotment.xlsx` | XLSX (7 sheets) | Allocation (Class View) | 6 Standard sheets (`V`, `VI`, `VII`, `VIII`, `IX`, `X`) + 1 Master (`Sheet1`). Exactly 26 classes across standards V–X. Every division totals exactly 48 periods/week ($26 \times 48 = 1,248$ total class periods). 393 distinct atomic subject-division allocations. |
| `sample-data/sample-teacherwise-allotment.xlsx` | XLSX (2 sheets) | Allocation (Teacher View) | `TEACHERS` sheet: 44 individual teacher blocks with STD, DIV, SUBJECT, PERIODS, BUNDLES. Sum of total periods across all 44 blocks = **1,248 periods** (100.0% exact match with Standardwise). `TEACHERSTOT LECT AND BUNDLES`: summary matrix. |
| `sample-data/sample-classteacher-list.xlsx` | XLSX | Staff Roles | Maps 26 class teachers and 14 free teachers. Sheet 1 is authoritative for designated class teachers and substitution pools. |
| `sample-data/sample-constraints.csv` | CSV | Formal Catalog | Machine-readable definitions of Hard Constraints (H1–H10) and Soft Constraints (S1–S6) consumed by solver and validator. |

---

## 3. Granular Record-Level Data Reconciliation

Reconciliation operates at the **individual Class + Subject + Teacher + PeriodCount** level across all 393 atomic assignments.

### 3.1 Class & Period Statistics
- **Total Standards**: 6 (Standards V, VI, VII, VIII, IX, X)
- **Total Classes / Divisions**: **26 classes**
  - Std V: 5 divisions (V/A, V/B, V/C, V/D, V/E)
  - Std VI: 4 divisions (VI/A, VI/B, VI/C, VI/D)
  - Std VII: 4 divisions (VII/A, VII/B, VII/C, VII/D)
  - Std VIII: 5 divisions (VIII/A, VIII/B, VIII/C, VIII/D, VIII/E)
  - Std IX: 4 divisions (IX/A, IX/B, IX/C, IX/D)
  - Std X: 4 divisions (X/A, X/B, X/C, X/D)
- **Periods Per Class Per Week**: Exactly **48 periods** for every single division.
- **Total School Class-Periods**: $26 \times 48 = \mathbf{1,248\text{ periods}}$.
- **Total Assigned Teacher-Periods**: $\mathbf{1,248\text{ periods}}$ (100% assigned, 0 unassigned).
- **Atomic Assignments**: Exactly **393 atomic (Class, Subject, Teacher, PeriodCount)** tuples. 100.0% reconciled between Standardwise and Teacherwise blocks upon alias normalization.

### 3.2 Intermediate Audit Artifacts
To ensure full auditability independent of the solver, the reconciliation engine emits intermediate normalized CSV datasets:
1. `output/normalized_allocations.csv`: Full list of all 393 reconciled (Class, Subject, Teacher, Periods) requirements.
2. `output/normalized_teachers.csv`: Roster of all 44 teachers with canonical names, standardized initials, total workload, and bundle counts.
3. `output/normalized_classes.csv`: 26 classes with standard, division, designated class teacher, and substitution list.
4. `output/reconciliation_discrepancies.csv`: Record-level report of source aliases, summary sheet formula omissions, and upstream staffing defects.

### 3.3 Teacher Roster & Load Reconciliation Summary

| Initials | Canonical Staff Identifier | Stdwise Load | TEACHERS Block Load | Bundles | Key Class Assignments |
|----------|----------------------------|:------------:|:-------------------:|:-------:|-----------------------|
| `AAK` | Teacher AAK | 30 | 30 | 5 | Class Teacher VII C (Marathi 6), EVS-II 5D (5), History, Library |
| `ACK` | Teacher ACK | 29 | 29 | 5 | Class Teacher VII A (Sanskrit 3), Sanskrit across 8 classes, Music, Craft |
| `AD` | Teacher AD | 33 | 33 | 6 | Geometry, Sci-II, Science, EVS-I, Free Teacher VIII A |
| `AJ` | Teacher AJ | 30 | 30 | 5 | Class Teacher VII B (History 3, Library 1), Science, Computer |
| `AM` | Teacher AM | 26 | 26 | 0 | Physical Education & Self Defence (Free Teacher IX B) |
| `AY` | Teacher AY | 30 | 30 | 6 | Class Teacher V E (P.E. 2, Lib 1), History, Computer |
| `BSS` | Teacher BSS | 29 | 29 | 6 | Class Teacher VIII A (Hindi 3), Hindi across 6 classes, Value Ed, Craft |
| `CR` | Teacher CR | 28 | 28 | 3 | Sanskrit across 8 classes, Music, Craft, Free Teacher VIII C |
| `DS` | Teacher DS | 31 | 31 | 5 | Class Teacher V A (Marathi 6), Marathi, Hindi, Scout & Guide |
| `FK` | Teacher FK | 30 | 30 | 6 | English, EVS-II, Geography, Computer, Free Teacher X D |
| `GB` | Teacher GB | 13 | 13 | 3 | English 10A, Geography 9A/10C, Library 10C |
| `HD` | Teacher HD (`NEW`) | 31 | 31 | 4 | Maths 5B/6B/6C, EVS-I 5A, Music, Free Teacher (Sub) |
| `JN` | Teacher JN | 18 | 18 | 3 | English 10B, History 9D/10D, Scout & Guide 7A/8A |
| `JP` | Teacher JP | 30 | 30 | 6 | Class Teacher VIII C (Geography 3), English 8E, History, Comp, Craft |
| `MB` | Teacher MB | 30 | 30 | 5 | Class Teacher VIII D (Sanskrit 3), Sanskrit, Art, CCA, Value Ed |
| `MP` | Teacher MP | 30 | 30 | 6 | Class Teacher VI D (Maths 7), Maths, Geometry, Algebra, Sci-I |
| `MR` | Teacher MR | 30 | 30 | 6 | Class Teacher VIII E (Maths 7), Maths, Geometry, Algebra, Science |
| `NM` | Teacher NM | 26 | 26 | 2 | Computer Lab Teacher (across 11 classes), Maths 5A/6A, Free Tr IX C |
| `NN` | Teacher NN | 30 | 30 | 5 | Class Teacher VIII B (Science 7), Maths 5C, Sci-II 9C/10C/10D |
| `NU` | Teacher NU | 29 | 29 | 0 | Art Specialist (across 13 classes), Scout & Guide, Free Tr X D |
| `PI` | Teacher PI | 29 | 29 | 6 | Marathi, Hindi, Music, Library, Free Teacher IX D |
| `PMA` | Teacher PMA | 29 | 29 | 5 | Class Teacher IX B (English 6), English 7A/8A, Geog, Comp, CCA |
| `PPT` | Teacher PPT | 29 | 29 | 5 | English 6B/9C, Geog, Water Sec, Free Teacher X A |
| `PT` | Teacher PT | 30 | 30 | 5 | English 6D, Maths 7C, History 9C/10C, EVS-II 5E, Free Tr X B |
| `RI` | Teacher RI | 27 | 27 | 6 | English 5B, History, Geog, Art, Library, Free Teacher IX D |
| `SD` | Teacher SD | 31 | 31 | 4 | Class Teacher V B (EVS-I 7), Maths 7B, Science 7A/8D, PE 5E |
| `SG` | Teacher SG | 30 | 30 | 6 | Marathi 5D/8D/9D, Hindi 5B/6C, Craft 6B, Free Tr VIII B |
| `SI` | Teacher SI | 31 | 31 | 4 | Maths 5E/7A/8D, Science 7B, Music 5A, Library 7A |
| `SK` | Teacher SK | 30 | 30 | 4 | Maths 5D/8A, EVS-I 5E, Science 8E, Value Ed 8B |
| `SM` | Teacher SM | 30 | 30 | 6 | Class Teacher V C (EVS-II 5), English 6C/8C, History, Geog, Lib |
| `SN` | Teacher SN | 4 | 4 | 1 | Science II for X A (Part-time / specialized) |
| `SP` | Teacher SP | 30 | 30 | 5 | Class Teacher X A (Sci-I 4, Water Sec 2), Maths 8C, Science 8A |
| `SS` | Teacher SS | 29 | 29 | 5 | Class Teacher X D (English 6, Hindi 9C), English 5A, Scout & Guide |
| `SSP` | Teacher SSP | 30 | 30 | 6 | Class Teacher IX A (History 4), English 8B/8D, History 10A, Geog 6B |
| `SSS` | Teacher SSS | 30 | 30 | 5 | Class Teacher VII D (Marathi 6), Marathi 8C, Hindi 6D, Craft, Art |
| `SL` | Teacher SL | 29 | 29 | 6 | Class Teacher VI B (Marathi 6), Marathi 9C/10C/10D, Hindi 7D/8D |
| `TP` | Teacher TP | 29 | 29 | 6 | Class Teacher X C (English 6), English 5D/7C/10C, Geog 9C/10A |
| `UP` | Teacher UP | 30 | 30 | 6 | Marathi 5B/10B, Hindi 8E/10D, Art 5C, Free Teacher X C |
| `VM` | Teacher VM | 29 | 29 | 5 | Class Teacher X B (Hindi 4, Marathi 9A/10A), Marathi 8B, Value Ed |
| `VR` | Teacher VR | 26 | 26 | 0 | Physical Education & Self Defence (Free Teacher IX A) |
| `VS` | Teacher VS | 33 | 33 | 6 | Class Teacher VI C (Maths 7), EVS-I 5C, Maths 7D, Sci-II 9A/9B/10B |
| `VT` | Teacher VT | 30 | 30 | 6 | Class Teacher IX D (Sci-I 4, Lib 1), Geometry, Sci-I 10D, Computer |
| `VV` | Teacher VV | 30 | 30 | 6 | Class Teacher IX C (Algebra 3, Lib 1), Algebra 9D/10B, Maths 8B, Sci 7C |
| `YP` | Teacher YP | 30 | 30 | 5 | Class Teacher VI A (Marathi 6), Marathi 5E/7B, Hindi 5C, History 7C |

---

## 4. Upstream Data Defects & Reconciliation Handling

1. **Teacher SL (Class Teacher for V-D)**:
   - Listed as Class Teacher for V-D in `sample-data/sample-classteacher-list.xlsx`.
   - Has **0 teaching assignments** in `Standardwise allotment` and does not exist in `TEACHERWISE ALLOTMENT`.
   - In V-D, all 48 periods are assigned to other staff: AAK (6), TP (6), SG (6), BSS (4), ACK (2), SK (8), AD (7), RI (1), CR (1), SSS (2), DS (2), MP (2), SD (1).
   - *Handling*: Reported as an upstream data defect in `output/input_validation_report.csv` and `output/reconciliation_discrepancies.csv`. No artificial teaching allocations are fabricated. V-D's schedule is generated strictly using its 48 allocated teaching periods.
2. **Teacher SY (Free Teacher for VIII-D)**:
   - Listed in the Free Teacher column for VIII-D, but does not appear in any teaching allotment.
   - *Handling*: Recorded as substitute-only staff in the data dictionary and reported in validation outputs.
3. **Summary Sheet Discrepancy for P.E. Teachers (`VR`, `AM`)**:
   - `VR` and `AM` have 26 periods in Standardwise and in the detailed `TEACHERS` block, but row totals in `TEACHERSTOT LECT AND BUNDLES` show 0 because P.E./Self Defence were omitted from the summary sheet formulas.
   - *Handling*: The detailed `TEACHERS` block and `Standardwise` sheets are authoritative.
4. **Header Naming / Alias Mappings**:
   - `HD` $\leftrightarrow$ `Teacher HD (NEW)` $\leftrightarrow$ `Teacher HD`
   - `AJ` $\leftrightarrow$ `TEACHER : Teacher AJ`
   - `SL` $\leftrightarrow$ `TEACHER : Teacher SL`
   - `BSS` $\leftrightarrow$ `Teacher BSS`
   - `SM` $\leftrightarrow$ `Teacher SM`
   - `NN` $\leftrightarrow$ `Teacher NN`
   - `SSP` $\leftrightarrow$ `Teacher SSP`
   - `SSS` $\leftrightarrow$ `Teacher SSS`

---

## 5. Subject Classification & Period Structure

### 5.1 Weekly Schedule Structure
- **Days**: 6 instructional days (Monday through Saturday).
- **Periods Per Day**: 8 standard instructional periods (07:10–12:10).
- **Recess**: 20 minutes between Period 4 and Period 5 (09:30–09:50).
- **Weekly Capacity**: 26 classes $\times$ 48 slots = **1,248 time slots**.

### 5.2 Configurable Subject Taxonomy
Configured in `config/school-config.yaml`:
- **CORE ACADEMIC**: `ENGLISH`, `MARATHI`, `HINDI`, `SANSKRIT`, `MATHS`, `ALGEBRA`, `GEOMETRY`, `SCIENCE`, `SCIENCE I`, `SCIENCE II`, `EVS I`, `EVS II`, `HISTORY`, `GEOGRAPHY`.
- **ACTIVITY / CO-CURRICULAR** (Subject to H8 max 2/day limit): `P.E`, `P.E.`, `COMPUTER`, `ART`, `CRAFT`, `MUSIC`, `LIBRARY`, `SCOUT & GUIDE`, `SELF DEFENCE`, `CCA`.
- **SPECIAL CURRICULAR**: `VALUE EDUCATION`, `WATER SECURITY` (Tracked explicitly; configurable in YAML whether grouped with co-curricular activities).
- **SHARED LAB RESOURCE**: `COMPUTER` (Constrained to 1 lab across the school).
