package com.sies.timetable.model;

import java.util.Objects;

/**
 * Represents a single scheduled assignment in the timetable grid:
 * (SchoolClass, TimeSlot, Teacher, Subject, TeachingAllocation).
 */
public record ScheduleAssignment(
        SchoolClass schoolClass,
        TimeSlot timeSlot,
        Teacher teacher,
        Subject subject,
        TeachingAllocation allocation
) implements Comparable<ScheduleAssignment> {
    public ScheduleAssignment {
        Objects.requireNonNull(schoolClass, "SchoolClass cannot be null");
        Objects.requireNonNull(timeSlot, "TimeSlot cannot be null");
        Objects.requireNonNull(teacher, "Teacher cannot be null");
        Objects.requireNonNull(subject, "Subject cannot be null");
    }

    public Day getDay() {
        return timeSlot.day();
    }

    public Period getPeriod() {
        return timeSlot.period();
    }

    public int getPeriodIndex() {
        return timeSlot.getPeriodIndex();
    }

    public boolean isPeriod1() {
        return timeSlot.isPeriod1();
    }

    public boolean isPeriod5() {
        return timeSlot.isPeriod5();
    }

    public boolean isPreRecess() {
        return timeSlot.isPreRecess();
    }

    public boolean isPostRecess() {
        return timeSlot.isPostRecess();
    }

    public boolean isActivity() {
        return subject.isActivity();
    }

    public boolean isComputer() {
        return subject.isComputer();
    }

    public boolean isPhysicalEducation() {
        return subject.isPhysicalEducation();
    }

    @Override
    public int compareTo(ScheduleAssignment o) {
        int classComp = this.schoolClass.compareTo(o.schoolClass);
        if (classComp != 0) return classComp;
        return this.timeSlot.compareTo(o.timeSlot);
    }

    @Override
    public String toString() {
        return schoolClass + " @ " + timeSlot + " -> " + subject + " (" + teacher.getCode() + ")";
    }
}
