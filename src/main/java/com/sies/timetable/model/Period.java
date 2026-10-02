package com.sies.timetable.model;

import java.util.Objects;

/**
 * Represents an instructional period in the school day.
 */
public class Period implements Comparable<Period> {
    private final int index;
    private final String timing;
    private final int recessAfterPeriod;
    private final boolean preRecess;
    private final boolean postRecess;

    public Period(int index, String timing, int recessAfterPeriod) {
        this.index = index;
        this.timing = timing;
        this.recessAfterPeriod = recessAfterPeriod;
        this.preRecess = (index <= recessAfterPeriod);
        this.postRecess = (index > recessAfterPeriod);
    }

    public int getIndex() {
        return index;
    }

    public String getTiming() {
        return timing;
    }

    public String getStartTime() {
        if (timing != null && timing.contains("-")) {
            return timing.split("-")[0].trim();
        }
        return "";
    }

    public String getEndTime() {
        if (timing != null && timing.contains("-")) {
            return timing.split("-")[1].trim();
        }
        return "";
    }

    public int getRecessAfterPeriod() {
        return recessAfterPeriod;
    }

    public boolean isPreRecess() {
        return preRecess;
    }

    public boolean isPostRecess() {
        return postRecess;
    }

    public boolean isFirstPeriod() {
        return index == 1;
    }

    public boolean isImmediatelyAfterRecess() {
        return index == recessAfterPeriod + 1;
    }

    @Override
    public int compareTo(Period other) {
        return Integer.compare(this.index, other.index);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Period period = (Period) o;
        return index == period.index;
    }

    @Override
    public int hashCode() {
        return Objects.hash(index);
    }

    @Override
    public String toString() {
        return "Period " + index + " (" + timing + ")";
    }
}
