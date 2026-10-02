package com.sies.timetable.model;

/**
 * Machine-readable specification entry for a constraint from constraints.csv.
 */
public record ConstraintSpec(
        String constraintId,
        String name,
        String type, // HARD or SOFT
        int defaultWeight,
        String sourceDocument,
        String sourceSection,
        String description,
        String mathematicalFormulation
) {
    public boolean isHard() {
        return "HARD".equalsIgnoreCase(type);
    }

    public boolean isSoft() {
        return "SOFT".equalsIgnoreCase(type);
    }
}
