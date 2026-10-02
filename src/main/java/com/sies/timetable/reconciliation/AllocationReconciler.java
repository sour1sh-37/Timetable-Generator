package com.sies.timetable.reconciliation;

import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.model.*;
import com.sies.timetable.parser.*;

import java.util.*;

/**
 * Executes granular Class + Subject + Teacher + PeriodCount reconciliation
 * across Standardwise and Teacherwise inputs.
 */
public class AllocationReconciler {

    public record ReconciliationResult(
            List<SchoolClass> classes,
            Map<String, SchoolClass> classMap,
            List<Teacher> teachers,
            Map<String, Teacher> teacherMap,
            List<Subject> subjects,
            Map<String, Subject> subjectMap,
            List<TeachingAllocation> allocations,
            Map<SchoolClass, ClassTeacherMapping> classTeacherMappings,
            ReconciliationReport report
    ) {}

    public static ReconciliationResult reconcile(
            List<StandardwiseParser.StandardwiseRecord> stdRecords,
            List<TeacherwiseParser.ParsedTeacherBlock> teacherBlocks,
            List<ClassTeacherListParser.ParsedClassTeacherRow> ctRows,
            SchoolConfig config
    ) {
        ReconciliationReport report = new ReconciliationReport();

        // 1. Build canonical teachers from TEACHERS blocks
        Map<String, Teacher> teacherMap = new TreeMap<>();
        for (TeacherwiseParser.ParsedTeacherBlock block : teacherBlocks) {
            String code = block.normalizedCode();
            boolean isFree = (block.totalPeriods() == 0 || block.rawCode().equalsIgnoreCase("FREE"));
            Teacher teacher = new Teacher(
                    code,
                    block.fullName(),
                    block.totalPeriods(),
                    block.totalBundles(),
                    isFree
            );
            teacherMap.put(code, teacher);
        }

        // 2. Build subjects map
        Map<String, Subject> subjectMap = new TreeMap<>();
        for (StandardwiseParser.StandardwiseRecord sr : stdRecords) {
            String sNorm = sr.normalizedSubject();
            subjectMap.computeIfAbsent(sNorm, k -> new Subject(
                    sr.rawSubject(),
                    sNorm,
                    config.categorizeSubject(sNorm),
                    config.isLabRequired(sNorm)
            ));
        }

        // 3. Build canonical classes (26 divisions)
        Map<String, SchoolClass> classMap = new TreeMap<>();
        List<SchoolClass> classList = new ArrayList<>();
        int[] standards = {5, 6, 7, 8, 9, 10};
        Map<Integer, List<String>> divMap = Map.of(
                5, List.of("A", "B", "C", "D", "E"),
                6, List.of("A", "B", "C", "D"),
                7, List.of("A", "B", "C", "D"),
                8, List.of("A", "B", "C", "D", "E"),
                9, List.of("A", "B", "C", "D"),
                10, List.of("A", "B", "C", "D")
        );

        for (int std : standards) {
            for (String div : divMap.get(std)) {
                SchoolClass sc = new SchoolClass(std, div);
                classList.add(sc);
                classMap.put(sc.getCanonicalName(), sc);
            }
        }

        // 4. Build Atomic Allocations from Standardwise requirements
        List<TeachingAllocation> allocations = new ArrayList<>();
        int allocId = 0;
        int totalPeriodSum = 0;

        for (StandardwiseParser.StandardwiseRecord sr : stdRecords) {
            SchoolClass sc = classMap.get(sr.canonicalClass());
            if (sc == null) {
                report.addDiscrepancy("ERROR", "Class Resolution", sr.canonicalClass(), "Standardwise record references unknown class: " + sr.canonicalClass());
                continue;
            }

            Teacher teacher = teacherMap.get(sr.normalizedTeacherCode());
            if (teacher == null) {
                // Register missing teacher if not in teacher blocks
                report.addDiscrepancy("WARNING", "Teacher Registration", sr.normalizedTeacherCode(), "Teacher code from Standardwise not found in detailed teacher blocks. Registering dynamically.");
                teacher = new Teacher(sr.normalizedTeacherCode(), NormalizationDictionary.getCanonicalTeacherName(sr.normalizedTeacherCode()), 0, 0, false);
                teacherMap.put(sr.normalizedTeacherCode(), teacher);
            }

            Subject subject = subjectMap.get(sr.normalizedSubject());

            TeachingAllocation alloc = new TeachingAllocation(
                    allocId++,
                    sc,
                    teacher,
                    subject,
                    sr.periods(),
                    1 // Default 1 bundle per atomic subject-division assignment
            );
            allocations.add(alloc);
            totalPeriodSum += sr.periods();
        }

        // 5. Build Class Teacher & Free Teacher Mappings
        Map<SchoolClass, ClassTeacherMapping> ctMappings = new HashMap<>();
        for (ClassTeacherListParser.ParsedClassTeacherRow ctRow : ctRows) {
            String rawClass = ctRow.rawClass().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
            // Match to SchoolClass
            SchoolClass matchedClass = null;
            for (SchoolClass sc : classList) {
                if (sc.getCanonicalName().equalsIgnoreCase(rawClass) || 
                    (sc.getStandard() + sc.getDivision()).equalsIgnoreCase(rawClass)) {
                    matchedClass = sc;
                    break;
                }
            }

            if (matchedClass != null) {
                Teacher classTeacher = teacherMap.get(ctRow.normalizedTeacherCode());
                if (classTeacher == null && ctRow.rawClassTeacher() != null) {
                    report.addDiscrepancy("ERROR", "Class Teacher Staffing Defect", ctRow.rawClassTeacher(), 
                            "Designated Class Teacher for " + matchedClass + " (" + ctRow.rawClassTeacher() + ") has zero teaching allocations across all school data.");
                }

                List<Teacher> freeTeachers = new ArrayList<>();
                for (String freeCode : ctRow.normalizedFreeTeacherCodes()) {
                    Teacher ft = teacherMap.get(freeCode);
                    if (ft != null) {
                        freeTeachers.add(ft);
                    } else {
                        report.addDiscrepancy("WARNING", "Free Teacher Staffing Defect", freeCode, 
                                "Designated Free Teacher (" + freeCode + ") for " + matchedClass + " is not in the teaching roster.");
                    }
                }

                ctMappings.put(matchedClass, new ClassTeacherMapping(matchedClass, classTeacher, freeTeachers));
            }
        }

        // 6. Cross-check TEACHERWISE summary sheet formula discrepancies
        for (Teacher t : teacherMap.values()) {
            if ((t.getCode().equals("VR") || t.getCode().equals("AM")) && t.getTotalWorkload() > 0) {
                report.addDiscrepancy("INFO", "Formula Omission in Summary Sheet", t.getFullName() + " (" + t.getCode() + ")", 
                        "P.E. teacher has " + t.getTotalWorkload() + " periods in Standardwise and detailed TEACHERS block, but was omitted from the TEACHERSTOT summary sheet formula.");
            }
        }

        // Set report statistics
        report.setTotalStandardwiseRecords(stdRecords.size());
        report.setTotalTeacherwiseRecords(teacherBlocks.size());
        report.setTotalReconciledAllocations(allocations.size());
        report.setTotalPeriods(totalPeriodSum);
        report.setTotalClasses(classList.size());
        report.setTotalTeachers(teacherMap.size());

        return new ReconciliationResult(
                classList,
                classMap,
                new ArrayList<>(teacherMap.values()),
                teacherMap,
                new ArrayList<>(subjectMap.values()),
                subjectMap,
                allocations,
                ctMappings,
                report
        );
    }
}
