import sys
import os
import pptx
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE

# -----------------------------------------------------------------------------
# Color Palette (Matching SIES Institutional Identity & Academic Presentation)
# -----------------------------------------------------------------------------
COLOR_PRIMARY_NAVY = RGBColor(15, 23, 42)      # Deep Academic Navy (#0F172A)
COLOR_SECONDARY_BLUE = RGBColor(30, 58, 138)   # SIES Institutional Blue (#1E3A8A)
COLOR_ACCENT_CRIMSON = RGBColor(185, 28, 28)   # SIES Terracotta/Crimson (#B91C1C)
COLOR_TEXT_MAIN = RGBColor(30, 41, 59)         # Dark Slate (#1E293B)
COLOR_TEXT_MUTED = RGBColor(71, 85, 105)       # Slate Grey (#475569)
COLOR_CARD_BG = RGBColor(248, 250, 252)        # Light Slate Box (#F8FAFC)
COLOR_CARD_BORDER = RGBColor(203, 213, 225)    # Slate Border (#CBD5E1)
COLOR_CARD_HEADER = RGBColor(241, 245, 249)    # Card Header Tint (#F1F5F9)
COLOR_TABLE_HEADER_BG = RGBColor(15, 23, 42)   # Table Header Navy (#0F172A)
COLOR_TABLE_ROW_ALT = RGBColor(241, 245, 249)  # Table Row Alternating (#F1F5F9)
COLOR_WHITE = RGBColor(255, 255, 255)

FONT_SERIF = "Times New Roman"
FONT_SANS = "Segoe UI"

def set_slide_header(slide, title_text):
    """Updates the text in Group 2 -> TextBox 4 with high-contrast Navy font."""
    for shp in slide.shapes:
        if shp.name == "Group 2" and shp.shape_type == pptx.enum.shapes.MSO_SHAPE_TYPE.GROUP:
            for sub in shp.shapes:
                if sub.name == "TextBox 4" and sub.has_text_frame:
                    tf = sub.text_frame
                    tf.clear()
                    p = tf.paragraphs[0]
                    p.text = title_text
                    p.font.name = FONT_SERIF
                    p.font.size = Pt(46)
                    p.font.bold = True
                    p.font.color.rgb = COLOR_PRIMARY_NAVY
                    p.alignment = PP_ALIGN.LEFT
                    return

def delete_table(slide):
    """Deletes any table on the slide so content cards can be placed."""
    for shp in list(slide.shapes):
        if shp.has_table:
            sp = shp._element
            sp.getparent().remove(sp)

def add_card(slide, left, top, width, height, title, items, badge="", header_h=0.75):
    """Creates an elegant card container with title, optional badge, and bullet points."""
    # Outer card shape
    card = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, left, top, width, height)
    card.fill.solid()
    card.fill.fore_color.rgb = COLOR_CARD_BG
    card.line.color.rgb = COLOR_CARD_BORDER
    card.line.width = Pt(1.2)
    
    # Header background band inside card
    h_h = Inches(header_h)
    hb = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, left, top, width, h_h)
    hb.fill.solid()
    hb.fill.fore_color.rgb = COLOR_CARD_HEADER
    hb.line.color.rgb = COLOR_CARD_BORDER
    hb.line.width = Pt(1.0)
    
    # Left accent indicator bar
    bar = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, left, top, Inches(0.12), h_h)
    bar.fill.solid()
    bar.fill.fore_color.rgb = COLOR_ACCENT_CRIMSON
    bar.line.fill.background()
    
    # Card Title Textbox
    tb_title = slide.shapes.add_textbox(left + Inches(0.25), top + Inches(0.08), width - Inches(0.5), Inches(0.6))
    tf_title = tb_title.text_frame
    tf_title.word_wrap = True
    tf_title.margin_left = tf_title.margin_top = tf_title.margin_right = tf_title.margin_bottom = 0
    p_t = tf_title.paragraphs[0]
    p_t.text = title
    p_t.font.name = FONT_SANS
    p_t.font.size = Pt(18)
    p_t.font.bold = True
    p_t.font.color.rgb = COLOR_PRIMARY_NAVY
    
    if badge:
        r_b = p_t.add_run()
        r_b.text = f"  [{badge}]"
        r_b.font.name = FONT_SANS
        r_b.font.size = Pt(13)
        r_b.font.bold = True
        r_b.font.color.rgb = COLOR_ACCENT_CRIMSON
        
    # Card Body Textbox
    body_top = top + h_h + Inches(0.12)
    body_h = height - h_h - Inches(0.25)
    tb_body = slide.shapes.add_textbox(left + Inches(0.25), body_top, width - Inches(0.5), body_h)
    tf_body = tb_body.text_frame
    tf_body.word_wrap = True
    tf_body.margin_left = tf_body.margin_top = tf_body.margin_right = tf_body.margin_bottom = 0
    
    for idx, item in enumerate(items):
        p = tf_body.add_paragraph() if idx > 0 else tf_body.paragraphs[0]
        p.space_after = Pt(7)
        p.space_before = Pt(2)
        
        if "::" in item:
            parts = item.split("::", 1)
            lead = parts[0].strip()
            rest = parts[1].strip()
            
            r0 = p.add_run()
            r0.text = "• "
            r0.font.name = FONT_SANS
            r0.font.size = Pt(15)
            r0.font.bold = True
            r0.font.color.rgb = COLOR_SECONDARY_BLUE
            
            r1 = p.add_run()
            r1.text = lead + ": "
            r1.font.name = FONT_SANS
            r1.font.size = Pt(15)
            r1.font.bold = True
            r1.font.color.rgb = COLOR_PRIMARY_NAVY
            
            r2 = p.add_run()
            r2.text = rest
            r2.font.name = FONT_SANS
            r2.font.size = Pt(14.5)
            r2.font.color.rgb = COLOR_TEXT_MAIN
        else:
            r0 = p.add_run()
            r0.text = "• "
            r0.font.name = FONT_SANS
            r0.font.size = Pt(15)
            r0.font.bold = True
            r0.font.color.rgb = COLOR_SECONDARY_BLUE
            
            r1 = p.add_run()
            r1.text = item
            r1.font.name = FONT_SANS
            r1.font.size = Pt(14.5)
            r1.font.color.rgb = COLOR_TEXT_MAIN

def create_styled_table(slide, left, top, width, height, col_widths, headers, data_rows, font_size=12.5):
    """Deletes existing table and builds a professionally formatted table with given rows/cols."""
    delete_table(slide)
    
    total_rows = len(data_rows) + 1
    total_cols = len(headers)
    tbl_shp = slide.shapes.add_table(total_rows, total_cols, left, top, width, height)
    table = tbl_shp.table
    
    for idx, w in enumerate(col_widths):
        table.columns[idx].width = w
        
    # Headers
    for c_idx, h_text in enumerate(headers):
        cell = table.cell(0, c_idx)
        cell.fill.solid()
        cell.fill.fore_color.rgb = COLOR_TABLE_HEADER_BG
        cell.vertical_anchor = MSO_ANCHOR.MIDDLE
        tf = cell.text_frame
        tf.word_wrap = True
        tf.clear()
        p = tf.paragraphs[0]
        p.text = h_text
        p.font.name = FONT_SANS
        p.font.size = Pt(14)
        p.font.bold = True
        p.font.color.rgb = COLOR_WHITE
        p.alignment = PP_ALIGN.CENTER
        
    # Data rows
    for r_idx, row_vals in enumerate(data_rows):
        bg_color = COLOR_TABLE_ROW_ALT if (r_idx % 2 == 1) else COLOR_WHITE
        for c_idx, val in enumerate(row_vals):
            cell = table.cell(r_idx + 1, c_idx)
            cell.fill.solid()
            cell.fill.fore_color.rgb = bg_color
            cell.vertical_anchor = MSO_ANCHOR.MIDDLE
            tf = cell.text_frame
            tf.word_wrap = True
            tf.clear()
            p = tf.paragraphs[0]
            p.text = str(val)
            p.font.name = FONT_SANS
            p.font.size = Pt(font_size)
            p.font.color.rgb = COLOR_TEXT_MAIN
            if c_idx == 0:
                p.alignment = PP_ALIGN.CENTER
                p.font.bold = True
            else:
                p.alignment = PP_ALIGN.LEFT

def main():
    prs = Presentation("presentation_skeleton.pptx")
    print(f"Loaded presentation_skeleton.pptx with {len(prs.slides)} slides.")

    # -------------------------------------------------------------------------
    # SLIDE 1: COVER SLIDE
    # -------------------------------------------------------------------------
    slide1 = prs.slides[0]
    for shp in slide1.shapes:
        if shp.name == "Group 8":
            for sub in shp.shapes:
                if sub.name == "TextBox 10" and sub.has_text_frame:
                    tf = sub.text_frame
                    tf.clear()
                    p = tf.paragraphs[0]
                    p.text = "Department of Computer Engineering"
                    p.font.name = FONT_SERIF
                    p.font.size = Pt(40)
                    p.font.bold = True
                    p.font.color.rgb = COLOR_PRIMARY_NAVY
                    p.alignment = PP_ALIGN.CENTER

        elif shp.name == "Group 2":
            for sub in shp.shapes:
                if sub.name == "TextBox 4" and sub.has_text_frame:
                    tf = sub.text_frame
                    tf.clear()
                    
                    # Leading spacing paragraph so it appears cleanly below Department
                    p_space = tf.paragraphs[0]
                    p_space.text = ""
                    p_space.font.size = Pt(14)
                    
                    p0 = tf.add_paragraph()
                    p0.text = "Mini Project Presentation"
                    p0.font.name = FONT_SERIF
                    p0.font.size = Pt(36)
                    p0.font.bold = True
                    p0.font.color.rgb = COLOR_PRIMARY_NAVY
                    p0.alignment = PP_ALIGN.CENTER
                    
                    p1 = tf.add_paragraph()
                    p1.text = "On"
                    p1.font.name = FONT_SERIF
                    p1.font.size = Pt(24)
                    p1.font.bold = True
                    p1.font.color.rgb = COLOR_TEXT_MUTED
                    p1.alignment = PP_ALIGN.CENTER
                    
                    p2 = tf.add_paragraph()
                    p2.text = "“SIES Timetable Generator”"
                    p2.font.name = FONT_SERIF
                    p2.font.size = Pt(44)
                    p2.font.bold = True
                    p2.font.color.rgb = COLOR_ACCENT_CRIMSON
                    p2.alignment = PP_ALIGN.CENTER
                    
                    p3 = tf.add_paragraph()
                    p3.text = "Automated Constraint-Based Academic Scheduling for SIES APJ School, Matunga"
                    p3.font.name = FONT_SERIF
                    p3.font.size = Pt(20)
                    p3.font.italic = True
                    p3.font.color.rgb = COLOR_PRIMARY_NAVY
                    p3.alignment = PP_ALIGN.CENTER
                    
        elif shp.name == "Group 5":
            for sub in shp.shapes:
                if sub.name == "TextBox 7" and sub.has_text_frame:
                    tf = sub.text_frame
                    tf.clear()
                    p0 = tf.paragraphs[0]
                    p0.text = "Presented By:"
                    p0.font.name = FONT_SERIF
                    p0.font.size = Pt(24)
                    p0.font.bold = True
                    p0.font.color.rgb = COLOR_PRIMARY_NAVY
                    p0.space_after = Pt(6)
                    
                    presenters = [
                        ("[Student Name 1]", "[PRN 1]"),
                        ("[Student Name 2]", "[PRN 2]"),
                        ("[Student Name 3]", "[PRN 3]"),
                        ("[Student Name 4]", "[PRN 4]"),
                    ]
                    for name, prn in presenters:
                        p = tf.add_paragraph()
                        p.text = f"{name:<35} {prn}"
                        p.font.name = FONT_SERIF
                        p.font.size = Pt(20)
                        p.font.color.rgb = COLOR_TEXT_MAIN

        elif shp.name == "TextBox 12" and shp.has_text_frame:
            tf = shp.text_frame
            tf.clear()
            p = tf.paragraphs[0]
            p.text = "Guided By: [Project Guide Name]"
            p.font.name = FONT_SERIF
            p.font.size = Pt(22)
            p.font.bold = True
            p.font.color.rgb = COLOR_PRIMARY_NAVY
            p.alignment = PP_ALIGN.CENTER

    print("Slide 1 configured.")

    # -------------------------------------------------------------------------
    # SLIDE 2: CONTENTS
    # -------------------------------------------------------------------------
    slide2 = prs.slides[1]
    set_slide_header(slide2, "Contents")
    from pptx.oxml.xmlchemy import OxmlElement
    for shp in slide2.shapes:
        if shp.name == "TextBox 5" and shp.has_text_frame:
            tf = shp.text_frame
            tf.clear()
            contents = [
                "1. Introduction (Institutional Background & Scheduling Mission)",
                "2. Motivation (Operational Bottlenecks & Combinatorial Scale)",
                "3. Problem Statement (Formal Mathematical Optimization Model)",
                "4. Objectives (Implemented Deliverables & System Milestones)",
                "5. Literature Review (Systematic Survey of Timetabling Paradigms)",
                "6. Comparison of the Models (Comparative Evaluation of Methods)",
                "7. Research Gaps (Academic Limitations & Real-World Practical Challenges)",
                "8. Scope of the Project (Functional Boundaries & Operational Demarcations)",
                "9. Proposed Methodology (Pipeline, Constraint Catalog, & Architecture)",
                "10. Results & Verification (Empirical Benchmarks & Zero-Trust Audit)",
                "11. References (Complete Verified IEEE Format)",
            ]
            for idx, item in enumerate(contents):
                p = tf.add_paragraph() if idx > 0 else tf.paragraphs[0]
                pPr = p._p.get_or_add_pPr()
                for child in list(pPr):
                    if "bu" in child.tag:
                        pPr.remove(child)
                buNone = OxmlElement("a:buNone")
                pPr.append(buNone)
                p.text = item
                p.font.name = FONT_SERIF
                p.font.size = Pt(23)
                p.font.bold = True
                p.font.color.rgb = COLOR_PRIMARY_NAVY
                p.space_after = Pt(7)

    print("Slide 2 configured.")

    # -------------------------------------------------------------------------
    # SLIDE 3: INTRODUCTION
    # -------------------------------------------------------------------------
    slide3 = prs.slides[2]
    set_slide_header(slide3, "Introduction")
    delete_table(slide3)
    add_card(
        slide3, Inches(1.3), Inches(2.4), Inches(8.4), Inches(7.5),
        "Institutional Operational Context",
        [
            "Client Institution:: Specifically engineered for SIES APJ School, Matunga, addressing their real-world secondary school scheduling requirements.",
            "Academic Demographics:: Encompasses Grades 5 through 10 (Standards V to X) comprising exactly 26 classroom divisions (Standards V-A to V-E, VI-A to VI-D, VII-A to VII-D, VIII-A to VIII-E, IX-A to IX-D, X-A to X-D).",
            "Teaching Faculty Roster:: Staffed by 44 faculty members managing 393 distinct atomic subject-division allocations.",
            "Weekly Schedule Architecture:: 6 instructional days (Monday through Saturday), 8 periods per day, yielding a required total weekly capacity of exactly 1,248 class-periods (26 classes × 48 periods = 1,248 slots).",
            "Data Provenance:: All allocations, staff assignments, and scheduling rules are based directly on authoritative spreadsheets and documents supplied by SIES APJ School."
        ],
        badge="SIES APJ School, Matunga"
    )
    add_card(
        slide3, Inches(10.1), Inches(2.4), Inches(8.4), Inches(7.5),
        "System Purpose & Evolution",
        [
            "Core Engineering Mission:: Replace tedious, error-prone manual spreadsheet scheduling with a mathematically rigorous, automated constraint-based desktop application.",
            "Dual-Objective Solving:: Simultaneously guarantees 100% hard constraint feasibility while optimizing soft pedagogical preferences (teacher workload balance, lecture spacing, variety).",
            "Initial Prototype Reality:: Original version was developed rapidly as a file-driven Java tool; it loaded static input files and lacked interactive manual editing screens.",
            "Subsequent Evolution:: Transformed into a modern multi-tier application featuring an embedded Java daemon, React 19 UI shell, Tauri v2 native desktop wrapper, and interactive studio editing.",
            "Zero-Trust Reliability:: Integrates an external, post-solve ScheduleValidator that independently audits all 1,248 slots to ensure zero scheduling defects before publishing."
        ],
        badge="Engineering Scope"
    )
    print("Slide 3 configured.")

    # -------------------------------------------------------------------------
    # SLIDE 4: MOTIVATION
    # -------------------------------------------------------------------------
    slide4 = prs.slides[3]
    set_slide_header(slide4, "Motivation")
    delete_table(slide4)
    add_card(
        slide4, Inches(1.3), Inches(2.4), Inches(8.4), Inches(7.5),
        "Operational Complexities & Human Limits",
        [
            "Combinatorial Explosion:: Distributing 393 atomic allocations across 1,248 class-periods generates a search space exceeding 10^150 possible schedules, rendering manual optimization impossible.",
            "Exhausting Manual Drafting:: Academic coordinators manually spent 2 to 3 weeks each term attempting to align teacher allocations, class requirements, and room slots.",
            "Cascading Ripple Effects:: Altering a single teacher's lecture inevitably triggers dozens of conflicting overlaps across multiple classes, requiring full schedule restarts.",
            "Teacher Cognitive Fatigue:: Manual drafting frequently introduces hidden teacher overload, such as scheduling teachers for >2 continuous lectures or excessive daily variance.",
            "Human Vulnerability to Clashes:: Subtle overlaps—such as double-booking floating subject teachers or assigning class teachers outside Period 1—are easily overlooked by manual visual checks."
        ],
        badge="Manual Inefficiencies"
    )
    add_card(
        slide4, Inches(10.1), Inches(2.4), Inches(8.4), Inches(7.5),
        "Resource Bottlenecks & Automation Need",
        [
            "Shared Computer Lab Bottleneck:: The school has exactly one shared Computer Lab serving all 26 classes. Ensuring zero lab clashes across 48 weekly slots is a critical constraint.",
            "Stringent Physical Education Rules:: Institutional rules prohibit P.E. in Period 1 (morning arrival) and Period 5 (immediately after lunch/recess), strictly limiting viable outdoor slots.",
            "Same-Day Subject Balancing:: P.E. and Computer sessions on the same day must be partitioned around recess (one morning, one afternoon) to avoid student fatigue.",
            "Rapid Adaptation to Staffing Changes:: Late-stage faculty transfers or curriculum revisions require instant re-solving rather than weeks of manual recalculation.",
            "Empowerment Through Technology:: Providing school administrators with an automated, push-button desktop tool saves hundreds of administrative hours and guarantees operational feasibility."
        ],
        badge="Institutional Bottlenecks"
    )
    print("Slide 4 configured.")

    # -------------------------------------------------------------------------
    # SLIDE 5: PROBLEM STATEMENT
    # -------------------------------------------------------------------------
    slide5 = prs.slides[4]
    set_slide_header(slide5, "Problem Statement")
    delete_table(slide5)
    add_card(
        slide5, Inches(1.3), Inches(2.4), Inches(8.4), Inches(7.5),
        "Formal Mathematical Formulation",
        [
            "Input Domains:: 26 Classes C = {c1 … c26}, 44 Faculty Members T = {t1 … t44}, 6 Days D = {Mon … Sat}, 8 Daily Periods P = {1 … 8}.",
            "Atomic Allocation Tuples:: Reconciled requirement set A = {a1 … a393}, where each a = (c, t, s, Ka) specifies class c, teacher t, subject s, and weekly period requirement Ka.",
            "Boolean Decision Variables:: Binary variables x(c, d, p, a) in {0, 1}, where x = 1 indicates that allocation a is scheduled for class c on day d at period p.",
            "Complete Capacity Conservation:: Exactly 48 slots per class: sum over allocations a in Alloc(c) of x(c,d,p,a) = 1 for all c, d, p. Total scheduled slots = 1,248.",
            "Exact Requirement Fulfillment:: Every atomic subject requirement Ka must be fulfilled exactly: sum over days d and periods p of x(c,d,p,a) = Ka for all a in A."
        ],
        badge="Mathematical Model"
    )
    add_card(
        slide5, Inches(10.1), Inches(2.4), Inches(8.4), Inches(7.5),
        "Constraint Hierarchy & Optimization Objective",
        [
            "Non-Negotiable Hard Constraints (H1–H10):: Zero teacher clashes (at most 1 class per teacher per slot), zero lab collisions (at most 1 class in lab per slot), max 2 continuous lectures per teacher, class teacher Period 1 priority, and strict P.E. slot bans.",
            "Multi-Objective Soft Optimization (S1–S6):: Minimize total weighted penalty: Minimize ∑ [Weight_i × Penalty_i(X)].",
            "Pedagogical Spread (S1, Weight: 100):: Distribute core subjects evenly across the 6 instructional days to prevent student academic burnout.",
            "Workload Balancing (S4, Weight: 40):: Minimize daily teaching load variance for each faculty member around their weekly mean load.",
            "Idle Gap Minimization (S5, Weight: 30):: Minimize unpaid waiting gaps between a teacher's first and last daily lecture.",
            "Infeasibility Handling:: System detects and pinpoints over-constrained data upstream rather than silently generating invalid schedules."
        ],
        badge="Multi-Objective Goal"
    )
    print("Slide 5 configured.")

    # -------------------------------------------------------------------------
    # SLIDE 6: OBJECTIVES
    # -------------------------------------------------------------------------
    slide6 = prs.slides[5]
    set_slide_header(slide6, "Objectives")
    delete_table(slide6)
    add_card(
        slide6, Inches(1.3), Inches(2.4), Inches(8.4), Inches(7.5),
        "Completed Core Engineering Deliverables",
        [
            "Dual-Source Ingestion & Reconciliation Engine:: Ingest standard-wise and teacher-wise Excel files, resolve teacher name aliases, and emit intermediate audit CSVs.",
            "Two-Phase CP-SAT Solver Integration:: Formulate pure SAT-based hard constraints (Phase 1) and warm-start soft objective optimization (Phase 2) using Google OR-Tools in Java 21.",
            "Zero-Trust Independent ScheduleValidator:: Construct a decoupled validation engine that verifies 10 operational invariants post-solve with zero reliance on solver assumptions.",
            "Interactive Administrative Desktop Studio:: Build a native Windows desktop GUI using Tauri v2 and React 19, enabling drag-and-drop manual swaps and real-time conflict diagnostics.",
            "Automated Multi-Format Document Publisher:: Output production-ready Master Excel workbooks (Apache POI), 26 printable per-class PDFs (OpenPDF), and machine-readable CSVs.",
            "Verified Automated Regression Test Suite:: Implement 100% passing unit and integration tests (17 test cases) covering reconciliation, solving, capacity, and API handlers."
        ],
        badge="100% Completed"
    )
    add_card(
        slide6, Inches(10.1), Inches(2.4), Inches(8.4), Inches(7.5),
        "System Boundaries & Future Milestones",
        [
            "Turnkey Packaging Architecture:: Successfully package private JRE (jlink) and native backend into a single-file Windows NSIS installer (58.54 MB) requiring zero runtime installation.",
            "Accessible Dual-Theme Interface:: Deliver a polished academic user interface with fully repaired light and dark mode design tokens.",
            "Explicit Boundary - Primary Classes:: System strictly scopes Standards V to X; Standards I to IV remain outside current scheduling boundaries.",
            "Explicit Boundary - Campus Facility Routing:: Single building assumption; multi-building campus transit modeling is demarcated as future scope.",
            "Future Roadmap - Real-Time Substitution:: Planned dynamic module to re-solve morning schedules automatically upon unexpected faculty emergency leaves.",
            "Future Roadmap - Cloud Sync:: Planned synchronization with school ERP systems via secure REST endpoints."
        ],
        badge="Scope & Roadmap"
    )
    print("Slide 6 configured.")

    # -------------------------------------------------------------------------
    # SLIDE 7: LITERATURE REVIEW (PART 1)
    # -------------------------------------------------------------------------
    slide7 = prs.slides[6]
    set_slide_header(slide7, "Literature Review")
    col_widths_lit = [Inches(0.8), Inches(3.2), Inches(2.4), Inches(3.5), Inches(3.8), Inches(3.9)]
    headers_lit = ["S. No.", "Title and Year", "Author", "Objective", "Technology and Working", "Findings"]
    data_lit_1 = [
        [
            "1",
            "A Survey of Automated Timetabling\n(1999)\n[Artificial Intelligence Review]",
            "Andrea Schaerf",
            "Formulate a standardized taxonomy for academic timetabling, distinguish school vs university problems, and evaluate CSP and metaheuristic methods.",
            "Surveys graph coloring, integer linear programming (ILP), constraint logic programming, and local search heuristics (Tabu Search, Simulated Annealing).",
            "Proves school timetabling is NP-complete. Concludes that pure exact methods suffer from exponential scaling, while pure heuristics struggle to guarantee hard constraints without hybrid pruning."
        ],
        [
            "2",
            "Integer programming techniques for educational timetabling\n(2017)\n[European Journal of Operational Research]",
            "G. H. G. Fonseca, H. G. Santos, E. G. Carrano, T. J. R. Stidsen",
            "Formulate exact Mixed Integer Linear Programming (MILP) models and decomposition methods for high school timetabling under the international XHSTT benchmark.",
            "Formulates 0-1 integer linear programming with branch-and-cut, Gomory cuts, and fix-and-optimize neighborhood metaheuristics.",
            "MILP provides mathematical proof of optimality on smaller instances, but encounters memory explosion and prolonged runtimes when scaling to large, densely constrained school instances."
        ],
        [
            "3",
            "The CP-SAT-LP Solver\n(2023)\n[29th Int. Conf. Principles and Practice of Constraint Programming]",
            "Laurent Perron, Frédéric Didier, Steven Gay (Google)",
            "Present Google OR-Tools CP-SAT solver architecture combining Conflict-Driven Clause Learning (CDCL) with dynamic linear programming relaxations.",
            "Employs lazy clause generation over integer variables, dual simplex cutting planes, and parallel portfolio search workers sharing learned nogoods.",
            "Delivers orders-of-magnitude faster pruning on discrete scheduling problems than pure CP or pure MIP, dominating the international MiniZinc Challenges."
        ]
    ]
    create_styled_table(slide7, Inches(1.2), Inches(2.5), Inches(17.6), Inches(7.2), col_widths_lit, headers_lit, data_lit_1, font_size=12.5)
    print("Slide 7 configured.")

    # -------------------------------------------------------------------------
    # SLIDE 8: LITERATURE REVIEW (PART 2)
    # -------------------------------------------------------------------------
    slide8 = prs.slides[7]
    set_slide_header(slide8, "Literature Review (Contd.)")
    data_lit_2 = [
        [
            "4",
            "A survey of school timetabling research\n(2014)\n[Annals of Operations Research]",
            "Nelishia Pillay",
            "Comprehensive survey of methodologies specifically tailored to secondary school timetabling and practical institutional constraints.",
            "Classifies Genetic Algorithms, Simulated Annealing, Tabu Search, Ant Colony Optimization, and Hyper-heuristics across institutional case studies.",
            "Metaheuristics effectively optimize soft preferences across vast spaces, but require heavy penalty tuning and frequently fail to produce 100% clash-free schedules in tightly constrained schools."
        ],
        [
            "5",
            "The third international timetabling competition (ITC 2011)\n(2016)\n[Annals of Operations Research]",
            "G. Post, S. Ahmadi, S. Doulab, J. H. Kingston, A. Schaerf, B. McCollum",
            "Benchmark automated timetabling solvers on real-world secondary school instances from 10 countries using standardized XHSTT format.",
            "Evaluated 17 competing solvers combining integer programming, constraint programming, simulated annealing, and hyper-heuristics.",
            "Pure metaheuristics struggled with tight feasibility; two-phase hybrid methods separating hard constraint satisfaction from soft optimization consistently outperformed monolithic solvers."
        ],
        [
            "6",
            "A genetic algorithm approach to school timetabling\n(2008)\n[Journal of the Operational Research Society]",
            "G. N. Beligiannis, C. N. Moschopoulos, S. D. Likothanassis",
            "Solve school timetabling problems using adaptive genetic algorithms with domain-specific crossover and mutation operators.",
            "Implements 2D chromosome representation with penalty-based fitness functions targeting teacher collisions and curriculum gaps.",
            "Effective for small-to-medium school structures, but exhibits slow convergence, premature stagnation, and lack of mathematical guarantees for bottleneck resources."
        ]
    ]
    create_styled_table(slide8, Inches(1.2), Inches(2.5), Inches(17.6), Inches(7.2), col_widths_lit, headers_lit, data_lit_2, font_size=12.5)
    print("Slide 8 configured.")

    # -------------------------------------------------------------------------
    # SLIDE 9: COMPARISON OF THE MODELS
    # -------------------------------------------------------------------------
    slide9 = prs.slides[8]
    set_slide_header(slide9, "Comparison of the Models (from Literature Review)")
    
    for shp in list(slide9.shapes):
        if shp.name == "TextBox 11":
            sp = shp._element
            sp.getparent().remove(sp)
            
    col_widths_comp = [Inches(2.8), Inches(3.5), Inches(3.5), Inches(3.8), Inches(4.0)]
    headers_comp = [
        "Scheduling Paradigm",
        "Hard Constraint Guarantee",
        "Soft Objective Optimization",
        "Infeasibility Handling",
        "Scalability & Computational Speed"
    ]
    data_comp = [
        [
            "Genetic Algorithms (GA) / Metaheuristics\n(Beligiannis et al., 2008; Pillay, 2014)",
            "Stochastic (No Guarantee)\nViolations penalized in fitness function; often leaves subtle clashes in dense bottlenecks.",
            "High\nFlexible evaluation of complex, non-linear pedagogical preferences.",
            "Poor\nCannot mathematically prove infeasibility; runs iteratively until timeout.",
            "Moderate\nSlow convergence on tightly constrained schedules; prone to local optima."
        ],
        [
            "Mixed Integer Linear Programming (MILP)\n(Fonseca et al., 2017; Santos et al.)",
            "Exact (Guaranteed)\nStrictly enforced via linear equality and inequality bounds.",
            "Optimal\nFinds provably global optimum for linear objective penalty functions.",
            "Good\nDetects infeasibility via Farkas lemma / dual unbounded rays.",
            "Low to Moderate\nProne to exponential branch-and-cut tree explosion on large discrete school models."
        ],
        [
            "Classic Constraint Programming (CP)\n(Schaerf, 1999; Kingston, 2005)",
            "Exact (Guaranteed)\nSystematic domain reduction via arc consistency (AC3 / GAC).",
            "Moderate\nBranch-and-bound soft search is less efficient than LP simplex relaxation.",
            "Strong\nEarly failure detection during active forward checking and constraint propagation.",
            "Moderate\nStruggles when objective functions require global dual bounding."
        ],
        [
            "Hybrid CP-SAT (Google OR-Tools)\n(Perron, Didier, & Gay, 2023 - Implemented)",
            "Exact (Guaranteed)\nSAT Conflict-Driven Clause Learning (CDCL) engine with boolean nogood learning.",
            "Superior\nIntegrated dynamic simplex LP relaxations provide tight dual bounding.",
            "Superior\nExtracts minimal unsatisfiable cores (MUC) to identify conflicting constraints.",
            "High\nMulti-threaded parallel portfolio search workers continuously share learned nogoods."
        ]
    ]
    create_styled_table(slide9, Inches(1.2), Inches(2.35), Inches(17.6), Inches(6.8), col_widths_comp, headers_comp, data_comp, font_size=12.0)
    
    tb_note = slide9.shapes.add_textbox(Inches(1.2), Inches(9.35), Inches(17.6), Inches(0.6))
    tf_note = tb_note.text_frame
    tf_note.word_wrap = True
    p_n = tf_note.paragraphs[0]
    p_n.text = "Note: In accordance with template instructions, comparative metrics from the Literature Survey evaluate combinatorial constraint satisfaction and optimization capabilities, rather than deep learning classification metrics (precision/recall)."
    p_n.font.name = FONT_SANS
    p_n.font.size = Pt(13)
    p_n.font.italic = True
    p_n.font.color.rgb = COLOR_TEXT_MUTED
    print("Slide 9 configured.")

    # -------------------------------------------------------------------------
    # SLIDE 10: RESEARCH GAPS
    # -------------------------------------------------------------------------
    slide10 = prs.slides[9]
    set_slide_header(slide10, "Research Gaps")
    delete_table(slide10)
    add_card(
        slide10, Inches(1.3), Inches(2.4), Inches(8.4), Inches(7.5),
        "Academic & Theoretical Limitations",
        [
            "The Clean-Benchmark Fallacy:: Academic literature overwhelmingly evaluates solvers on sanitized synthetic benchmarks (e.g., XHSTT); it completely ignores the noisy, inconsistent spreadsheets actually used by secondary schools.",
            "The Heuristic Feasibility Compromise:: Many commercial tools employ Genetic Algorithms that optimize soft preferences but fail to guarantee 100% hard feasibility, forcing manual post-processing by staff.",
            "Exponential Scaling in Pure Integer Programming:: Pure MILP approaches struggle with branch-and-cut explosion when handling multi-class teacher continuous lecture constraints across large faculties.",
            "Neglect of Shared Singular Resources:: Standard formulations assume multiple identical laboratories; they degrade severely when scheduling a single shared facility (Computer Lab) across 26 classes.",
            "Infeasibility Explanation Void:: Solvers typically return a binary 'INFEASIBLE' status without pinpointing the exact conflicting constraint or over-allocated teacher."
        ],
        badge="Literature Gaps"
    )
    add_card(
        slide10, Inches(10.1), Inches(2.4), Inches(8.4), Inches(7.5),
        "How This Project Addresses The Gaps",
        [
            "Algorithmic Data Reconciliation:: Implemented dual-source ingestion (standard-wise vs teacher-wise) with automated alias normalization dictionary and explicit defect audit logging.",
            "Two-Phase Hybrid Solving Guarantee:: Combines pure SAT feasibility search (Phase 1, <5s) with warm-started soft objective optimization (Phase 2), ensuring zero hard violations.",
            "Dedicated Bottleneck Modeling:: Explicitly formulates single Computer Lab exclusivity and P.E. recess partition rules as linear CP constraints.",
            "Zero-Trust Independent Audit Engine:: Introduced ScheduleValidator, an external auditor that verifies 10 operational invariants post-solve independently of solver assumptions.",
            "Turnkey Administrative Usability:: Bridged the research-to-practice gap with a native desktop app, bundled private JRE, and interactive manual swap diagnostics."
        ],
        badge="Project Contributions"
    )
    print("Slide 10 configured.")

    # -------------------------------------------------------------------------
    # SLIDE 11: SCOPE OF THE PROJECT
    # -------------------------------------------------------------------------
    slide11 = prs.slides[10]
    set_slide_header(slide11, "Scope of the Project")
    delete_table(slide11)
    add_card(
        slide11, Inches(1.3), Inches(2.4), Inches(8.4), Inches(7.5),
        "In-Scope: Fully Implemented & Verified",
        [
            "Target Academic Boundary:: Secondary section of SIES APJ School, Matunga, covering Standards V through X (26 classroom divisions, 44 faculty members).",
            "Weekly Grid Structure:: 6 instructional days (Monday–Saturday), 8 periods per day, total weekly capacity of exactly 1,248 class-periods (26 classes × 48 slots = 1,248 total slots).",
            "Multi-File Ingestion Pipeline:: Ingestion and automated reconciliation of standard-wise allotments, teacher-wise allotments, and class teacher rosters.",
            "Constraint Solver Engine:: Two-phase Google OR-Tools CP-SAT solver enforcing all 10 Hard Constraints (H1–H10) and 6 Soft Pedagogical Objectives (S1–S6).",
            "Independent Audit Suite:: Zero-trust ScheduleValidator performing complete post-solve integrity checks across all 1,248 time slots.",
            "Interactive Desktop GUI:: Native Windows application (Tauri v2 + React 19) with manual in-place swap editing, visual conflict diagnostics, and dark mode.",
            "Production Deliverables:: Automated generation of Master Excel workbook, 26 per-class printable PDFs, and 9 audit CSVs."
        ],
        badge="Implemented Features"
    )
    add_card(
        slide11, Inches(10.1), Inches(2.4), Inches(8.4), Inches(7.5),
        "Out-of-Scope: Demarcated Future Enhancements",
        [
            "Primary & Pre-Primary Sections:: Standards I through IV and pre-primary sections operate under distinct pedagogical structures and are excluded from this project.",
            "Physical Room Navigation & Transit:: Assumes dedicated home classrooms for each division; multi-building physical campus walking transit times are not modeled.",
            "Student Elective Individual Routing:: SIES APJ School follows standard division-level cohort curricula; individual student elective choice pathing is not required or supported.",
            "Automated Emergency Substitute Generation:: Re-solving schedules dynamically in the morning upon unexpected faculty leave is demarcated as future scope.",
            "School ERP Direct API Sync:: Direct automated database synchronization with third-party school enterprise ERP portals remains an intended future enhancement."
        ],
        badge="Explicit Boundaries"
    )
    print("Slide 11 configured.")

    # -------------------------------------------------------------------------
    # SLIDE 12: PROPOSED METHODOLOGY: WORKFLOW PIPELINE
    # -------------------------------------------------------------------------
    slide12 = prs.slides[11]
    set_slide_header(slide12, "Proposed Methodology: End-to-End Workflow")
    delete_table(slide12)
    
    stages = [
        ("STAGE 1: Dual-Source Data Ingestion", [
            "Parses sample-standardwise-allotment.xlsx (class view, 26 divisions).",
            "Parses sample-teacherwise-allotment.xlsx (teacher view, 44 blocks).",
            "Parses sample-classteacher-list.xlsx (class teacher & free teacher roster)."
        ]),
        ("STAGE 2: Algorithmic Reconciliation", [
            "AllocationReconciler unifies inputs into 393 atomic (Class, Subject, Teacher, Periods) tuples.",
            "NormalizationDictionary resolves teacher aliases and naming discrepancies.",
            "Emits audit logs (reconciliation_discrepancies.csv, normalized_allocations.csv)."
        ]),
        ("STAGE 3: Pre-Solve Invariant Audit", [
            "InputValidator executes dynamic capacity verification (26 × 48 = 1,248 invariant).",
            "Audits teacher workload bounds and verifies Computer Lab weekly feasibility.",
            "Halts immediately with informative error if input configuration is mathematically infeasible."
        ]),
        ("STAGE 4: Two-Phase CP-SAT Optimization", [
            "Phase 1 (Satisfaction): Builds pure H1–H10 hard constraint model; finds feasible baseline in <5s.",
            "Phase 2 (Optimization): Warm-starts from Phase 1 solution; minimizes weighted soft penalties S1–S6.",
            "Parallel portfolio search workers share learned nogoods for rapid convergence."
        ]),
        ("STAGE 5: Zero-Trust ScheduleValidator", [
            "Independent verification engine audits schedule completely decoupled from solver.",
            "Verifies all 10 hard constraints across all 1,248 slots (100% compliance checked).",
            "Computes penalty scores and generates validation_report.csv."
        ]),
        ("STAGE 6: Multi-Format Document Publishing", [
            "Master Excel Workbook: Color-coded class and teacher tabs via Apache POI 5.2.5.",
            "Printable PDFs: 26 per-class formatted schedules via OpenPDF 1.3.39.",
            "Machine-Readable CSVs: 9 audit and data export files for administrative records."
        ])
    ]
    
    card_w = Inches(5.5)
    card_h = Inches(3.55)
    for idx, (title, items) in enumerate(stages):
        row = idx // 3
        col = idx % 3
        left = Inches(1.3) + col * Inches(5.95)
        top = Inches(2.4) + row * Inches(3.85)
        add_card(slide12, left, top, card_w, card_h, title, items, header_h=0.65)
        
    print("Slide 12 configured.")

    # -------------------------------------------------------------------------
    # SLIDE 13: PROPOSED METHODOLOGY: CONSTRAINT MODEL
    # -------------------------------------------------------------------------
    slide13 = prs.slides[12]
    set_slide_header(slide13, "Proposed Methodology: Constraint Formulation")
    delete_table(slide13)
    add_card(
        slide13, Inches(1.3), Inches(2.4), Inches(8.4), Inches(7.5),
        "Strict Hard Operational Constraints (H1–H10)",
        [
            "H1: Class Teacher Period 1 Priority:: Class teacher must be scheduled in Period 1 whenever teaching their assigned class.",
            "H2: Max 2 Continuous Lectures:: Faculty cannot teach >2 consecutive periods. Recess between P4 and P5 resets lecture fatigue.",
            "H3: Single Assignment per Class Slot:: Exactly 1 lecture per class per slot: sum of x(c,d,p,a) = 1. Verifies complete 48 periods/week.",
            "H4: Teacher Single-Booking:: A teacher can instruct at most 1 class in any time slot: sum of x(c,d,p,a) <= 1 (Zero clashes).",
            "H5: Computer Lab Exclusivity:: Across all 26 classes, at most 1 class can utilize the single Computer Lab in any slot.",
            "H6: P.E. Prohibited in Period 1:: Students must be in classroom for morning attendance; outdoor P.E. is forbidden in slot p = 1.",
            "H7: P.E. Prohibited Post-Recess:: Vigorous physical activity is strictly forbidden in Period 5 immediately following lunch/recess.",
            "H8: Max 2 Activity Periods / Day:: Classes are restricted to at most 2 co-curricular periods daily (P.E., Art, Craft, Music, Library).",
            "H9: Exact Granular Allotment Fulfillment:: Every atomic allocation count Ka is strictly satisfied. Total slots scheduled = 1,248.",
            "H10: P.E. & Computer Recess Partition:: If P.E. and Computer are scheduled on the same day, one must be pre-recess and one post-recess."
        ],
        badge="Zero Defects (H1–H10)"
    )
    add_card(
        slide13, Inches(10.1), Inches(2.4), Inches(8.4), Inches(7.5),
        "Soft Pedagogical Objectives (S1–S6)",
        [
            "Optimization Objective Function:: Minimize ∑ [Weight_i × Penalty_i(X)], balancing academic quality and teacher welfare.",
            "S1: Subject Weekly Spread (Weight: 100):: Evenly distributes core subject lectures throughout the week; penalizes multiple lectures of the same subject on one day.",
            "S2: Non-Consecutive 2-Period Days (Weight: 80):: For subjects requiring 2 periods/week, penalizes scheduling on adjacent days (target spacing >= 2 days).",
            "S3: Repeated Lecture Spacing (Weight: 50):: Enforces minimum time gaps between repeated subject lectures across consecutive school days.",
            "S4: Teacher Daily Workload Balance (Weight: 40):: Minimizes daily teaching variance for each faculty member around their weekly mean load.",
            "S5: Teacher Idle Gap Minimization (Weight: 30):: Minimizes unpaid waiting gaps between a teacher's first and last daily scheduled lecture.",
            "S6: Class Academic Variety (Weight: 20):: Penalizes heavy blocks of >3 continuous rigorous academic subjects without a linguistic or co-curricular break."
        ],
        badge="Soft Penalties (S1–S6)"
    )
    print("Slide 13 configured.")

    # -------------------------------------------------------------------------
    # SLIDE 14: PROPOSED METHODOLOGY: SOFTWARE ARCHITECTURE
    # -------------------------------------------------------------------------
    slide14 = prs.slides[13]
    set_slide_header(slide14, "Proposed Methodology: Software Architecture")
    delete_table(slide14)
    add_card(
        slide14, Inches(1.3), Inches(2.4), Inches(8.4), Inches(7.5),
        "Multi-Tier Application Architecture",
        [
            "Presentation Tier (React 19 + TypeScript + Vite):: Modern Single Page Application with CommandCenter (health dashboard), SchoolSetupView (bell schedule, capacity summary), and TimetableStudioView (grid view, teacher/room filters, in-place swap).",
            "Centralized Design Token Architecture:: Robust CSS custom properties supporting high-contrast, professional Light and Dark themes suitable for academic staff.",
            "Native Desktop Shell (Tauri v2 + Rust):: Lightweight Windows wrapper utilizing native WebView2; consumes only ~28 MB RAM compared to 300+ MB for Electron.",
            "Local Loopback API Daemon (Java HTTP Server):: Embedded lightweight TimetableHttpServer binding to local loopback (127.0.0.1) with dynamic port hunting, eliminating web framework overhead.",
            "Authoritative Computational Core (Java 21 LTS):: Implements Google OR-Tools 9.11 CP-SAT solver, Apache POI 5.2.5, OpenPDF 1.3.39, and SnakeYAML."
        ],
        badge="Multi-Tier Stack"
    )
    add_card(
        slide14, Inches(10.1), Inches(2.4), Inches(8.4), Inches(7.5),
        "Turnkey Windows Desktop Packaging",
        [
            "Private Modular JRE via jlink:: Packaging pipeline (build_desktop.ps1) extracts a stripped, minimal Java 25 runtime containing only required modules (java.base, java.desktop, jdk.httpserver).",
            "Zero External Runtime Dependencies:: End-user machines require neither pre-installed Java nor Node.js; the app runs completely self-contained.",
            "Single-File NSIS Installer:: Assembles backend JAR, bundled JRE, frontend assets, and native Rust binary into SIES Timetable Generator_1.0.0_x64-setup.exe (58.54 MB).",
            "Local Data Persistence Target:: Application state, YAML configurations, and generated schedules are persisted safely in the standard OS directory: %APPDATA%/com.sies.timetable/.",
            "Process Lifecycle Management:: Tauri Rust core orchestrates starting, health-checking, and cleanly terminating the Java backend daemon on application exit."
        ],
        badge="Turnkey Packaging"
    )
    print("Slide 14 configured.")

    # -------------------------------------------------------------------------
    # SLIDE 15: RESULTS: EMPIRICAL SOLVER BENCHMARKS
    # -------------------------------------------------------------------------
    slide15 = prs.slides[14]
    set_slide_header(slide15, "Results: Empirical Performance & Benchmarks")
    delete_table(slide15)
    add_card(
        slide15, Inches(1.3), Inches(2.4), Inches(8.4), Inches(7.5),
        "Production Dataset: SIES APJ School (26 Classes)",
        [
            "Production Problem Scale:: 26 classroom divisions (Standards V–X), 44 teachers, 393 atomic allocations, 1,248 total scheduled periods.",
            "Phase 1 Feasibility Solve Time:: 4.870 s — A 100% valid, clash-free schedule satisfying all H1–H10 hard constraints is proven in under 5 seconds!",
            "Phase 1 Model Build Time:: 0.267 s (Instant constraint generation).",
            "Phase 2 Optimization Solve Time:: 30.233 s — Warm-started from Phase 1 to minimize weighted soft penalties S1–S6.",
            "Total Generation Duration:: 35.636 s (Complete solve from raw files to fully optimized schedule).",
            "Total Decision Variables:: 18,864 boolean variables.",
            "Total Enforced Constraints:: 14,369 linear and boolean constraints.",
            "Peak Heap Memory Utilization:: 288.88 MB (Stable, predictable footprint).",
            "Instant Cancellation Latency:: 61 ms — User abort request stops active CP-SAT search within 61 ms (well below 1,000 ms threshold)."
        ],
        badge="Live Production Run"
    )
    add_card(
        slide15, Inches(10.1), Inches(2.4), Inches(8.4), Inches(7.5),
        "Reference Benchmark & Empirical Insights",
        [
            "Small Instance Benchmark (4 Classes, Standards 5–6, 160 Slots):: Phase 1: 0.194 s | Phase 2: 1.393 s | Total: 1.608 s | Variables: 1,232 | Constraints: 1,385 | Heap: 298.88 MB.",
            "The Power of Two-Phase Architecture:: Decoupling hard satisfaction from soft optimization prevents solver stalling. Phase 1 guarantees an immediate workable schedule, while Phase 2 iteratively enhances pedagogical quality.",
            "Linear Scalability in Practice:: Moving from 4 classes to 26 classes increases variables by 15.3x, while solve time scales gracefully from 1.6s to 35.6s.",
            "Zero False Feasibility:: CP-SAT mathematically proves satisfaction of all constraints; no soft trade-offs ever violate operational rules."
        ],
        badge="Empirical Analysis"
    )
    print("Slide 15 configured.")

    # -------------------------------------------------------------------------
    # SLIDE 16: RESULTS: SOLUTION QUALITY & VERIFICATION AUDIT
    # -------------------------------------------------------------------------
    slide16 = prs.slides[15]
    set_slide_header(slide16, "Results: Solution Quality & Verification Audit")
    delete_table(slide16)
    add_card(
        slide16, Inches(1.3), Inches(2.4), Inches(8.4), Inches(7.5),
        "Zero-Trust ScheduleValidator Audit Findings",
        [
            "Independent Audit Protocol:: ScheduleValidator independently inspects the final timetable matrix without relying on solver state or heuristics.",
            "Total Class-Periods Scheduled:: 1,248 / 1,248 periods (100.0% atomic allocation fulfillment, zero unscheduled periods).",
            "Hard Constraint Violations (H1–H10):: Exactly 0 defects (Strict 100% compliance across all 26 classes).",
            "Teacher Double-Booking Clashes:: 0 clashes across all 44 faculty members.",
            "Class Slot Double-Bookings / Gaps:: 0 clashes; exactly 48 valid assignments per class per week.",
            "Shared Computer Lab Exclusivity:: 0 lab clashes across all 48 weekly instructional periods.",
            "Class Teacher Period 1 Fulfillment:: 100% satisfied for all allocated class teachers.",
            "Physical Education & Activity Compliance:: 0 violations (No P.E. in Period 1 or Period 5; max 2 activity periods per class per day strictly honored)."
        ],
        badge="100% Validated Audit"
    )
    add_card(
        slide16, Inches(10.1), Inches(2.4), Inches(8.4), Inches(7.5),
        "Regression Test Suite & Export Verification",
        [
            "Automated Regression Test Suite:: 17 / 17 Maven Tests Passing (100% pass rate) in automated CI/CD pipeline.",
            "TeachingPeriodsFlowTest (14 Tests):: Fully validates dynamic daily periods, Saturday reduced schedule (4 periods), Smart Timing manager, and custom recess placement.",
            "TimetableServiceTest (3 Tests):: End-to-end integration tests verifying model generation, feasibility solving, and soft optimization.",
            "Master Excel Workbook (Apache POI):: timetable.xlsx featuring color-coded class schedules and teacher workload sheets.",
            "Printable Class PDFs (OpenPDF):: 26 clean vector PDF schedules (output/pdf/*.pdf) ready for notice boards.",
            "Audit CSV Datasets:: 9 normalized audit files including teacher workload distributions, class teacher rosters, and reconciliation discrepancy reports."
        ],
        badge="Production Deliverables"
    )
    print("Slide 16 configured.")

    # -------------------------------------------------------------------------
    # SLIDE 17: REFERENCES (IEEE FORMAT)
    # -------------------------------------------------------------------------
    slide17 = prs.slides[16]
    set_slide_header(slide17, "References (IEEE Format)")
    delete_table(slide17)
    
    references = [
        "[1] A. Schaerf, \"A survey of automated timetabling,\" Artificial Intelligence Review, vol. 13, no. 2, pp. 87–127, Apr. 1999, doi: 10.1023/A:1006576204496.",
        "[2] G. H. G. Fonseca, H. G. Santos, E. G. Carrano, and T. J. R. Stidsen, \"Integer programming techniques for educational timetabling,\" European Journal of Operational Research, vol. 262, no. 1, pp. 28–39, Oct. 2017, doi: 10.1016/j.ejor.2017.03.020.",
        "[3] L. Perron, F. Didier, and S. Gay, \"The CP-SAT-LP Solver,\" in Proc. 29th Int. Conf. Principles and Practice of Constraint Programming (CP 2023), vol. 280, pp. 3:1–3:2, 2023, doi: 10.4230/LIPIcs.CP.2023.3.",
        "[4] N. Pillay, \"A survey of school timetabling research,\" Annals of Operations Research, vol. 218, no. 1, pp. 261–293, Jul. 2014, doi: 10.1007/s10479-013-1321-8.",
        "[5] G. Post, S. Ahmadi, S. Doulab, J. H. Kingston, A. Schaerf, and B. McCollum, \"The third international timetabling competition (ITC 2011),\" Annals of Operations Research, vol. 239, no. 1, pp. 69–75, Apr. 2016, doi: 10.1007/s10479-013-1340-5.",
        "[6] G. N. Beligiannis, C. N. Moschopoulos, and S. D. Likothanassis, \"A genetic algorithm approach to school timetabling,\" Journal of the Operational Research Society, vol. 59, no. 10, pp. 1329–1342, Oct. 2008, doi: 10.1057/palgrave.jors.2602525.",
        "[7] Google Developers, OR-Tools CP-SAT Solver, v9.11, Mountain View, CA, USA, 2024. [Online]. Available: https://developers.google.com/optimization/cp/cp_solver"
    ]
    
    add_card(
        slide17, Inches(1.3), Inches(2.4), Inches(17.4), Inches(7.5),
        "Authoritative Literature & Official Technical References",
        references,
        badge="IEEE Format"
    )
    print("Slide 17 configured.")

    out_path = "SIES_Timetable_Generator_Presentation.pptx"
    prs.save(out_path)
    print(f"\n=======================================================")
    print(f"Presentation successfully built and saved to: {out_path}")
    print(f"Total slides: {len(prs.slides)}")
    print(f"=======================================================")

if __name__ == "__main__":
    main()
