package com.sies.timetable.model;

import java.util.Objects;

/**
 * Domain model representing a subject in the school curriculum.
 */
public class Subject implements Comparable<Subject> {
    private final String name;
    private final String normalizedName;
    private final SubjectCategory category;
    private final boolean requiresLab;

    public Subject(String name, String normalizedName, SubjectCategory category, boolean requiresLab) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.category = category;
        this.requiresLab = requiresLab;
    }

    public Subject(String name, SubjectCategory category, boolean requiresLab) {
        this(name, name, category, requiresLab);
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public SubjectCategory getCategory() {
        return category;
    }

    public boolean isActivity() {
        return category == SubjectCategory.ACTIVITY;
    }

    public boolean isCoreAcademic() {
        return category == SubjectCategory.CORE_ACADEMIC;
    }

    public boolean isRequiresLab() {
        return requiresLab;
    }

    public boolean isPhysicalEducation() {
        String n = normalizedName.toUpperCase();
        return n.equals("P.E") || n.equals("P.E.") || n.equals("PE") || n.contains("PHYSICAL EDUCATION");
    }

    public boolean isComputer() {
        String n = normalizedName.toUpperCase();
        return n.equals("COMPUTER") || n.equals("COMP");
    }

    @Override
    public int compareTo(Subject o) {
        return this.normalizedName.compareTo(o.normalizedName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Subject subject = (Subject) o;
        return Objects.equals(normalizedName, subject.normalizedName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedName);
    }

    @Override
    public String toString() {
        return normalizedName;
    }
}
