package com.sies.timetable.reconciliation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Report containing data reconciliation statistics, record-level comparisons, and data defect logs.
 */
public class ReconciliationReport {

    public record DiscrepancyItem(
            String severity, // ERROR, WARNING, INFO
            String category,
            String entity,
            String description
    ) {}

    private int totalStandardwiseRecords;
    private int totalTeacherwiseRecords;
    private int totalReconciledAllocations;
    private int totalPeriods;
    private int totalClasses;
    private int totalTeachers;
    private final List<DiscrepancyItem> discrepancies = new ArrayList<>();

    public void addDiscrepancy(String severity, String category, String entity, String description) {
        discrepancies.add(new DiscrepancyItem(severity, category, entity, description));
    }

    public List<DiscrepancyItem> getDiscrepancies() {
        return Collections.unmodifiableList(discrepancies);
    }

    public int getTotalStandardwiseRecords() { return totalStandardwiseRecords; }
    public void setTotalStandardwiseRecords(int totalStandardwiseRecords) { this.totalStandardwiseRecords = totalStandardwiseRecords; }

    public int getTotalTeacherwiseRecords() { return totalTeacherwiseRecords; }
    public void setTotalTeacherwiseRecords(int totalTeacherwiseRecords) { this.totalTeacherwiseRecords = totalTeacherwiseRecords; }

    public int getTotalReconciledAllocations() { return totalReconciledAllocations; }
    public void setTotalReconciledAllocations(int totalReconciledAllocations) { this.totalReconciledAllocations = totalReconciledAllocations; }

    public int getTotalPeriods() { return totalPeriods; }
    public void setTotalPeriods(int totalPeriods) { this.totalPeriods = totalPeriods; }

    public int getTotalClasses() { return totalClasses; }
    public void setTotalClasses(int totalClasses) { this.totalClasses = totalClasses; }

    public int getTotalTeachers() { return totalTeachers; }
    public void setTotalTeachers(int totalTeachers) { this.totalTeachers = totalTeachers; }
}
