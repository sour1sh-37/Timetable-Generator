package com.sies.timetable.reconciliation;

import com.sies.timetable.model.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Emits intermediate normalized audit datasets so that input reconciliation can be audited
 * independently of the solver.
 */
public class IntermediateDataExporter {

    public static void exportAll(AllocationReconciler.ReconciliationResult result, String outputDir) throws Exception {
        Files.createDirectories(Paths.get(outputDir));

        exportAllocations(result.allocations(), outputDir + "/normalized_allocations.csv");
        exportTeachers(result.teachers(), outputDir + "/normalized_teachers.csv");
        exportClasses(result.classes(), result.classTeacherMappings(), outputDir + "/normalized_classes.csv");
        exportDiscrepancies(result.report().getDiscrepancies(), outputDir + "/reconciliation_discrepancies.csv");
    }

    private static void exportAllocations(List<TeachingAllocation> allocations, String filePath) throws Exception {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {
            bw.write("AllocationID,Standard,Division,ClassCanonical,Subject,Category,TeacherCode,TeacherName,PeriodCount,Bundles\n");
            for (TeachingAllocation a : allocations) {
                bw.write(String.format("%d,%d,%s,%s,\"%s\",%s,%s,\"%s\",%d,%d\n",
                        a.getId(),
                        a.getSchoolClass().getStandard(),
                        a.getSchoolClass().getDivision(),
                        a.getSchoolClass().getCanonicalName(),
                        a.getSubject().getNormalizedName(),
                        a.getSubject().getCategory().name(),
                        a.getTeacher().getCode(),
                        a.getTeacher().getFullName(),
                        a.getPeriodCount(),
                        a.getBundleCount()
                ));
            }
        }
    }

    private static void exportTeachers(List<Teacher> teachers, String filePath) throws Exception {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {
            bw.write("TeacherCode,FullName,TotalWorkload,TotalBundles,IsFreeTeacher,MaxContinuousLimit\n");
            for (Teacher t : teachers) {
                bw.write(String.format("%s,\"%s\",%d,%d,%b,%d\n",
                        t.getCode(),
                        t.getFullName(),
                        t.getTotalWorkload(),
                        t.getTotalBundles(),
                        t.isFreeTeacher(),
                        t.getMaxContinuousLectures()
                ));
            }
        }
    }

    private static void exportClasses(List<SchoolClass> classes, Map<SchoolClass, ClassTeacherMapping> ctMap, String filePath) throws Exception {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {
            bw.write("Standard,Division,ClassCanonical,DisplayName,ClassTeacherCode,ClassTeacherName,FreeTeacherCodes\n");
            for (SchoolClass sc : classes) {
                ClassTeacherMapping ctm = ctMap.get(sc);
                String ctCode = (ctm != null && ctm.getClassTeacher() != null) ? ctm.getClassTeacher().getCode() : "NONE";
                String ctName = (ctm != null && ctm.getClassTeacher() != null) ? ctm.getClassTeacher().getFullName() : "NONE";
                String freeCodes = (ctm != null && !ctm.getFreeTeachers().isEmpty()) ?
                        String.join(";", ctm.getFreeTeachers().stream().map(Teacher::getCode).toList()) : "NONE";

                bw.write(String.format("%d,%s,%s,%s,%s,\"%s\",\"%s\"\n",
                        sc.getStandard(),
                        sc.getDivision(),
                        sc.getCanonicalName(),
                        sc.getDisplayName(),
                        ctCode,
                        ctName,
                        freeCodes
                ));
            }
        }
    }

    private static void exportDiscrepancies(List<ReconciliationReport.DiscrepancyItem> items, String filePath) throws Exception {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {
            bw.write("Severity,Category,Entity,Description\n");
            for (ReconciliationReport.DiscrepancyItem item : items) {
                bw.write(String.format("%s,\"%s\",\"%s\",\"%s\"\n",
                        item.severity(),
                        item.category(),
                        item.entity().replace("\"", "\"\""),
                        item.description().replace("\"", "\"\"")
                ));
            }
        }
    }
}
