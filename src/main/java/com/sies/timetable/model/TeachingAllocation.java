package com.sies.timetable.model;

import java.util.Objects;

/**
 * Atomic teaching requirement: (Class, Teacher, Subject, PeriodCount, Bundles).
 */
public class TeachingAllocation implements Comparable<TeachingAllocation> {
    private final int id; // Unique integer index (0 .. 392)
    private final SchoolClass schoolClass;
    private final Teacher teacher;
    private final Subject subject;
    private final int periodCount; // Weekly periods required
    private final int bundleCount; // Bundles assigned

    public TeachingAllocation(int id, SchoolClass schoolClass, Teacher teacher, Subject subject, int periodCount, int bundleCount) {
        this.id = id;
        this.schoolClass = Objects.requireNonNull(schoolClass);
        this.teacher = Objects.requireNonNull(teacher);
        this.subject = Objects.requireNonNull(subject);
        this.periodCount = periodCount;
        this.bundleCount = bundleCount;
    }

    public int getId() {
        return id;
    }

    public SchoolClass getSchoolClass() {
        return schoolClass;
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public Subject getSubject() {
        return subject;
    }

    public int getPeriodCount() {
        return periodCount;
    }

    public int getBundleCount() {
        return bundleCount;
    }

    public boolean isPhysicalEducation() {
        return subject.isPhysicalEducation();
    }

    public boolean isComputer() {
        return subject.isComputer();
    }

    public boolean isActivity() {
        return subject.isActivity();
    }

    @Override
    public int compareTo(TeachingAllocation o) {
        int classComp = this.schoolClass.compareTo(o.schoolClass);
        if (classComp != 0) return classComp;
        int subjComp = this.subject.compareTo(o.subject);
        if (subjComp != 0) return subjComp;
        return this.teacher.compareTo(o.teacher);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TeachingAllocation that = (TeachingAllocation) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "[" + id + "] " + schoolClass + " - " + subject + " (" + teacher.getCode() + ") x " + periodCount;
    }
}
