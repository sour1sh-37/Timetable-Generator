package com.sies.timetable.model;

import java.util.Objects;

/**
 * Composite key representing a specific (Day, Period) in the weekly schedule.
 */
public record TimeSlot(Day day, Period period) implements Comparable<TimeSlot> {
    public TimeSlot {
        Objects.requireNonNull(day, "Day cannot be null");
        Objects.requireNonNull(period, "Period cannot be null");
    }

    public int getDayIndex() {
        return day.getIndex();
    }

    public int getPeriodIndex() {
        return period.getIndex();
    }

    public boolean isPreRecess() {
        return period.isPreRecess();
    }

    public boolean isPostRecess() {
        return period.isPostRecess();
    }

    public boolean isPeriod1() {
        return period.isFirstPeriod();
    }

    public boolean isPeriod5() {
        return period.isImmediatelyAfterRecess();
    }

    @Override
    public int compareTo(TimeSlot other) {
        int dayCompare = Integer.compare(this.day.getIndex(), other.day.getIndex());
        if (dayCompare != 0) {
            return dayCompare;
        }
        return this.period.compareTo(other.period);
    }

    @Override
    public String toString() {
        return day.getShortName() + "-P" + period.getIndex();
    }
}
