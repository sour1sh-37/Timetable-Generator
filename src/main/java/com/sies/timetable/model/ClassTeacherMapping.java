package com.sies.timetable.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Mapping between a class and its designated class teacher and substitute/free teachers.
 */
public class ClassTeacherMapping {
    private final SchoolClass schoolClass;
    private final Teacher classTeacher;
    private final List<Teacher> freeTeachers;

    public ClassTeacherMapping(SchoolClass schoolClass, Teacher classTeacher, List<Teacher> freeTeachers) {
        this.schoolClass = Objects.requireNonNull(schoolClass);
        this.classTeacher = classTeacher; // Can be null if missing in data (e.g. SEEMA L)
        this.freeTeachers = freeTeachers != null ? Collections.unmodifiableList(freeTeachers) : Collections.emptyList();
    }

    public SchoolClass getSchoolClass() {
        return schoolClass;
    }

    public Teacher getClassTeacher() {
        return classTeacher;
    }

    public List<Teacher> getFreeTeachers() {
        return freeTeachers;
    }

    public boolean hasClassTeacher() {
        return classTeacher != null;
    }

    @Override
    public String toString() {
        return schoolClass + " -> CT: " + (classTeacher != null ? classTeacher.getCode() : "NONE") + 
               ", Free: " + freeTeachers.stream().map(Teacher::getCode).toList();
    }
}
