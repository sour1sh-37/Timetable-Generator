# Timetable Generator — Constraint Specifications

This document defines the formal catalog of all scheduling constraints, categorized into **Hard Constraints (H1–H10)** and **Soft Constraints / Optimization Objectives (S1–S6)**. Both the Google OR-Tools CP-SAT solver and the independent schedule validator consume the exact specifications defined in `sample-data/sample-constraints.csv` and `config/school-config.yaml`.

---

## 1. Hard Constraints (H1 – H10)

Hard constraints are non-negotiable operational requirements. Any generated timetable violating a hard constraint is considered infeasible and rejected.

### H1: Class Teacher First Period Allocation
- **Source**: Institutional Scheduling Specification, Section 4
- **Authority Priority**: Priority 2 (Explicit Project Documentation)
- **Formal Definition**:
  Let $c \in \text{Classes}$ be a class, and let $T_c = \text{ClassTeacher}(c)$ be its designated class teacher from `sample-data/sample-classteacher-list.xlsx`.
  Let $\text{Alloc}(c, T_c)$ be the set of subject-teaching allocations of teacher $T_c$ in class $c$ from the reconciled allocation dataset.
  - **Case A ($\text{Alloc}(c, T_c) \neq \emptyset$)**:
    For any instructional day $d \in \{1 \dots 6\}$, whenever an allocated session of teacher $T_c$ is scheduled for class $c$, that session must be placed in **Period 1**:
    $$\forall d \in \{1 \dots 6\}, \forall a \in \text{Alloc}(c, T_c): \quad \sum_{p=2}^{8} x_{c, d, p, a} = 0$$
    Consequently, on every day that the class teacher teaches their class, they teach in Period 1. (For classes where $T_c$ has $\ge 6$ periods in class $c$, all 6 Period 1 slots are filled by $T_c$).
  - **Case B ($\text{Alloc}(c, T_c) = \emptyset$, e.g. class with unallocated class teacher)**:
    No artificial substitute assignments are invented. The zero-allocation condition is reported as an upstream data-quality and feasibility defect in `output/input_validation_report.csv` and `output/reconciliation_discrepancies.csv`. Period 1 for class $c$ is scheduled among its legitimately allocated subject teachers.

### H2: Maximum Two Continuous Lectures Per Teacher
- **Source**: Institutional Scheduling Specification, Section 5
- **Authority Priority**: Priority 2 (Explicit Project Documentation)
- **Formal Definition**:
  For every teacher $t$, day $d$, and starting period $p \in \{1, 2, 5, 6\}$ (within the pre-recess block $1 \dots 4$ and post-recess block $5 \dots 8$):
  $$\sum_{k=0}^{2} \text{is\_teaching}(t, d, p+k) \le 2$$
  where $\text{is\_teaching}(t, d, p) = \sum_{c \in \text{Classes}} \sum_{a \in \text{Assign}(c, t)} x_{c, d, p, a}$.
  *(Note: Recess between Period 4 and Period 5 provides a 20-minute physical break, resetting continuous lecture counting between morning and afternoon blocks).*

### H3: Single Assignment Per Class Slot (Dynamic Weekly Capacity Validation)
- **Source**: Fundamental Scheduling Axiom
- **Authority Priority**: Priority 1 / Priority 2
- **Formal Definition**:
  For every class $c$, day $d \in \{1 \dots 6\}$, and period $p \in \{1 \dots 8\}$:
  $$\sum_{a \in \text{Allocations}(c)} x_{c, d, p, a} = 1$$
  *Dynamic Validation Requirement*: Input validation dynamically verifies that for each class, the sum of required periods across all allocated subjects is exactly equal to the total weekly capacity ($6 \text{ days} \times 8 \text{ periods} = 48\text{ slots}$). Exactly one assignment per slot is thus valid and necessary to schedule all 48 required periods without idle gaps.

### H4: Single Class Per Teacher Slot (No Teacher Clash)
- **Source**: Fundamental Scheduling Axiom
- **Authority Priority**: Priority 1 / Priority 2
- **Formal Definition**:
  For every teacher $t$, day $d \in \{1 \dots 6\}$, and period $p \in \{1 \dots 8\}$:
  $$\sum_{c \in \text{Classes}} \sum_{a \in \text{Allocations}(c, t)} x_{c, d, p, a} \le 1$$
  A teacher can teach at most one class in any given time slot.

### H5: Computer Lab Exclusivity (No Lab Clash)
- **Source**: Institutional Scheduling Specification, Section 11
- **Authority Priority**: Priority 2 (Explicit Project Documentation)
- **Formal Definition**:
  For every day $d \in \{1 \dots 6\}$ and period $p \in \{1 \dots 8\}$:
  $$\sum_{c \in \text{Classes}} \sum_{a \in \text{LabAllocations}(c)} x_{c, d, p, a} \le 1$$
  Across all classes, at most one class can use the Computer Lab in any slot $(d, p)$.

### H6: Physical Education Prohibited in Period 1
- **Source**: Institutional Scheduling Specification, Section 12
- **Authority Priority**: Priority 2 (Explicit Project Documentation)
- **Formal Definition**:
  For every class $c$ and day $d \in \{1 \dots 6\}$:
  $$\sum_{a \in \text{PE\_Allocations}(c)} x_{c, d, p=1, a} = 0$$

### H7: Physical Education Prohibited Immediately After Recess (Period 5)
- **Source**: Institutional Scheduling Specification, Section 12
- **Authority Priority**: Priority 2 (Explicit Project Documentation)
- **Formal Definition**:
  For every class $c$ and day $d \in \{1 \dots 6\}$:
  $$\sum_{a \in \text{PE\_Allocations}(c)} x_{c, d, p=5, a} = 0$$
  (where Period 5 is the slot immediately following the morning recess).

### H8: Maximum Two Activity Periods Per Class Per Day
- **Source**: Institutional Scheduling Specification, Section 9
- **Authority Priority**: Priority 2 (Explicit Project Documentation)
- **Formal Definition**:
  For every class $c$ and day $d \in \{1 \dots 6\}$:
  $$\sum_{p=1}^{8} \sum_{a \in \text{ActivityAllocations}(c)} x_{c, d, p, a} \le 2$$
  where $\text{ActivityAllocations}(c)$ is the set of assignments with subjects tagged as `ACTIVITY` in `config/school-config.yaml` (`P.E`, `COMPUTER`, `ART`, `CRAFT`, `MUSIC`, `LIBRARY`, `SCOUT & GUIDE`, `SELF DEFENCE`, `CCA`).

### H9: Exact Granular Allotment Fulfillment & Total Capacity Validation
- **Source**: Standardwise & Teacherwise Allotment Datasets
- **Authority Priority**: Priority 1 (Current Allocation Data)
- **Formal Definition**:
  Both of the following conditions must be strictly satisfied:
  1. For every atomic requirement $a = (c, t, s, K)$ in the reconciled dataset:
     $$\sum_{d=1}^{6} \sum_{p=1}^{8} x_{c, d, p, a} = K$$
  2. The sum of all scheduled slots across all classes must exactly equal the total reconciled requirement sum:
     $$\sum_{c \in \text{Classes}} \sum_{d=1}^{6} \sum_{p=1}^{8} \sum_{a \in \text{Allocations}(c)} x_{c, d, p, a} = \sum_{a \in \text{AllAllocations}} K_a = 1,248$$

### H10: P.E. and Computer Same-Day Partition Around Recess
- **Source**: Institutional Scheduling Specification, Section 10
- **Authority Priority**: Priority 2 (Explicit Project Documentation)
- **Formal Definition**:
  For any class $c$ and day $d$:
  If both P.E. and Computer are scheduled on day $d$, exactly one must be in the pre-recess window (Periods 1–4) and the other must be in the post-recess window (Periods 5–8):
  $$\left( \sum_{p=1}^{4} x_{c, d, p, \text{PE}} = 1 \land \sum_{p=5}^{8} x_{c, d, p, \text{Comp}} = 1 \right) \lor \left( \sum_{p=1}^{4} x_{c, d, p, \text{Comp}} = 1 \land \sum_{p=5}^{8} x_{c, d, p, \text{PE}} = 1 \right)$$
  **Linear Formulation**:
  $$\sum_{p=1}^{4} x_{c, d, p, \text{PE}} + \sum_{p=1}^{4} x_{c, d, p, \text{Comp}} \le 1$$
  $$\sum_{p=5}^{8} x_{c, d, p, \text{PE}} + \sum_{p=5}^{8} x_{c, d, p, \text{Comp}} \le 1$$

---

## 2. Soft Constraints & Optimization Objectives (S1 – S6)

Soft constraints are expressed as penalty terms in the CP-SAT objective function:
$$\min \sum_{i=1}^{6} W_i \cdot \text{ViolationScore}(S_i)$$

### S1: Subject Lectures Evenly Distributed Throughout the Week
- **Weight**: 100 (Default)
- **Formulation**: For subjects with weekly periods $K \le 6$, penalize scheduling $>1$ lecture on the same day. For subjects with $K > 6$, penalize scheduling $>2$ lectures on the same day.

### S2: Non-Consecutive Days for 2-Period Subjects
- **Weight**: 80 (Default)
- **Formulation**: For subjects requiring exactly 2 periods per week in class $c$, add a penalty if scheduled on adjacent days $(d, d+1)$ (target spacing $\ge 2$ days).

### S3: Spacing / Gap Between Repeated Subject Lectures
- **Weight**: 50 (Default)
- **Formulation**: Penalize tight multi-day clustering of repeated subject sessions across the week.

### S4: Teacher Daily Workload Balance
- **Weight**: 40 (Default)
- **Formulation**: Minimize daily teaching load variance for each teacher around their weekly mean $L_t / 6$.

### S5: Teacher Idle Gap Minimization (Compact Schedules)
- **Weight**: 30 (Default)
- **Formulation**: Minimize unassigned gap periods occurring between a teacher's first teaching period and last teaching period on each day.

### S6: Class Academic Variety & Sequencing
- **Weight**: 20 (Default)
- **Formulation**: Penalize heavy academic clustering of $>3$ consecutive rigorous academic periods without a co-curricular or linguistic break.
