package com.sies.timetable.model;

import java.util.Objects;

/**
 * Domain model representing a teacher in the school.
 */
public class Teacher implements Comparable<Teacher> {
    private final String code; // Standardized initials (e.g. "MP", "HD", "SP")
    private final String fullName; // Canonical full name (e.g. "MARUTI PATIL")
    private final int totalWorkload; // Reconciled weekly lecture count
    private final int totalBundles; // Bundle count
    private final boolean isFreeTeacher; // Flag indicating substitute / free teacher status
    private final int maxContinuousLectures; // Default 2

    public Teacher(String code, String fullName, int totalWorkload, int totalBundles, boolean isFreeTeacher) {
        this(code, fullName, totalWorkload, totalBundles, isFreeTeacher, 2);
    }

    public Teacher(String code, String fullName, int totalWorkload, int totalBundles, boolean isFreeTeacher, int maxContinuousLectures) {
        this.code = code.trim().toUpperCase();
        this.fullName = fullName != null ? fullName.trim() : this.code;
        this.totalWorkload = totalWorkload;
        this.totalBundles = totalBundles;
        this.isFreeTeacher = isFreeTeacher;
        this.maxContinuousLectures = maxContinuousLectures;
    }

    public String getCode() {
        return code;
    }

    public String getFullName() {
        return fullName;
    }

    public int getTotalWorkload() {
        return totalWorkload;
    }

    public int getTotalBundles() {
        return totalBundles;
    }

    public boolean isFreeTeacher() {
        return isFreeTeacher;
    }

    public int getMaxContinuousLectures() {
        return maxContinuousLectures;
    }

    @Override
    public int compareTo(Teacher o) {
        return this.code.compareTo(o.code);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Teacher teacher = (Teacher) o;
        return Objects.equals(code, teacher.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }

    @Override
    public String toString() {
        return fullName + " (" + code + ")";
    }
}
