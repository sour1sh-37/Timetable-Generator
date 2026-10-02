package com.sies.timetable.model;

/**
 * Days of the instructional week (Monday to Saturday).
 */
public enum Day {
    MONDAY(1, "Monday", "Mon"),
    TUESDAY(2, "Tuesday", "Tue"),
    WEDNESDAY(3, "Wednesday", "Wed"),
    THURSDAY(4, "Thursday", "Thu"),
    FRIDAY(5, "Friday", "Fri"),
    SATURDAY(6, "Saturday", "Sat");

    private final int index;
    private final String displayName;
    private final String shortName;

    Day(int index, String displayName, String shortName) {
        this.index = index;
        this.displayName = displayName;
        this.shortName = shortName;
    }

    public int getIndex() {
        return index;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getShortName() {
        return shortName;
    }

    public static Day fromIndex(int index) {
        for (Day day : values()) {
            if (day.index == index) {
                return day;
            }
        }
        throw new IllegalArgumentException("Invalid day index: " + index);
    }
}
