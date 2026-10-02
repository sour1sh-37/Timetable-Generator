package com.sies.timetable.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Complete timetable representation containing all scheduled assignments across classes and teachers.
 */
public class Schedule {
    private final List<ScheduleAssignment> assignments;
    private final Map<SchoolClass, Map<TimeSlot, ScheduleAssignment>> classTimeSlotMap;
    private final Map<Teacher, Map<TimeSlot, ScheduleAssignment>> teacherTimeSlotMap;
    private final Map<TimeSlot, List<ScheduleAssignment>> slotAssignmentsMap;

    public Schedule(List<ScheduleAssignment> assignments) {
        this.assignments = Collections.unmodifiableList(new ArrayList<>(assignments));
        this.classTimeSlotMap = new HashMap<>();
        this.teacherTimeSlotMap = new HashMap<>();
        this.slotAssignmentsMap = new HashMap<>();

        for (ScheduleAssignment sa : this.assignments) {
            classTimeSlotMap
                    .computeIfAbsent(sa.schoolClass(), k -> new HashMap<>())
                    .put(sa.timeSlot(), sa);

            teacherTimeSlotMap
                    .computeIfAbsent(sa.teacher(), k -> new HashMap<>())
                    .put(sa.timeSlot(), sa);

            slotAssignmentsMap
                    .computeIfAbsent(sa.timeSlot(), k -> new ArrayList<>())
                    .add(sa);
        }
    }

    public List<ScheduleAssignment> getAssignments() {
        return assignments;
    }

    public int getTotalAssignmentsCount() {
        return assignments.size();
    }

    public ScheduleAssignment getAssignment(SchoolClass schoolClass, TimeSlot timeSlot) {
        Map<TimeSlot, ScheduleAssignment> map = classTimeSlotMap.get(schoolClass);
        return map != null ? map.get(timeSlot) : null;
    }

    public ScheduleAssignment getAssignment(SchoolClass schoolClass, Day day, Period period) {
        return getAssignment(schoolClass, new TimeSlot(day, period));
    }

    public ScheduleAssignment getTeacherAssignment(Teacher teacher, TimeSlot timeSlot) {
        Map<TimeSlot, ScheduleAssignment> map = teacherTimeSlotMap.get(teacher);
        return map != null ? map.get(timeSlot) : null;
    }

    public List<ScheduleAssignment> getAssignmentsForClass(SchoolClass schoolClass) {
        Map<TimeSlot, ScheduleAssignment> map = classTimeSlotMap.get(schoolClass);
        return map != null ? new ArrayList<>(map.values()) : Collections.emptyList();
    }

    public List<ScheduleAssignment> getAssignmentsForClassAndDay(SchoolClass schoolClass, Day day) {
        return getAssignmentsForClass(schoolClass).stream()
                .filter(a -> a.getDay() == day)
                .sorted(Comparator.comparing(ScheduleAssignment::getPeriod))
                .collect(Collectors.toList());
    }

    public List<ScheduleAssignment> getAssignmentsForTeacher(Teacher teacher) {
        Map<TimeSlot, ScheduleAssignment> map = teacherTimeSlotMap.get(teacher);
        return map != null ? new ArrayList<>(map.values()) : Collections.emptyList();
    }

    public List<ScheduleAssignment> getAssignmentsForTeacherAndDay(Teacher teacher, Day day) {
        return getAssignmentsForTeacher(teacher).stream()
                .filter(a -> a.getDay() == day)
                .sorted(Comparator.comparing(ScheduleAssignment::getPeriod))
                .collect(Collectors.toList());
    }

    public List<ScheduleAssignment> getAssignmentsAtSlot(TimeSlot timeSlot) {
        return slotAssignmentsMap.getOrDefault(timeSlot, Collections.emptyList());
    }
}
