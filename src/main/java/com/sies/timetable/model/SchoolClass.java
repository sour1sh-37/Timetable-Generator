package com.sies.timetable.model;

import java.util.Objects;

/**
 * Domain model representing a class division (e.g., Standard 5 Division A -> 5A / VA).
 */
public class SchoolClass implements Comparable<SchoolClass> {
    private final int standard; // 5, 6, 7, 8, 9, 10
    private final String division; // A, B, C, D, E
    private final String romanStandard; // V, VI, VII, VIII, IX, X
    private final String canonicalName; // e.g. "VA", "XB"
    private final String displayName; // e.g. "V/A", "X/B"

    public SchoolClass(int standard, String division) {
        this.standard = standard;
        this.division = division.trim().toUpperCase();
        this.romanStandard = toRoman(standard);
        this.canonicalName = this.romanStandard + this.division;
        this.displayName = this.romanStandard + "/" + this.division;
    }

    public int getStandard() {
        return standard;
    }

    public String getDivision() {
        return division;
    }

    public String getRomanStandard() {
        return romanStandard;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getStandardDivisionName() {
        return standard + "-" + division;
    }

    private static String toRoman(int standard) {
        return switch (standard) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            case 11 -> "XI";
            case 12 -> "XII";
            default -> String.valueOf(standard);
        };
    }

    public static int parseStandard(String stdStr) {
        String s = stdStr.trim().toUpperCase().replace("/", "").replace(" ", "").replace("-", "");
        if (s.startsWith("XII")) return 12;
        if (s.startsWith("XI")) return 11;
        if (s.startsWith("X")) return 10;
        if (s.startsWith("IX")) return 9;
        if (s.startsWith("VIII")) return 8;
        if (s.startsWith("VII")) return 7;
        if (s.startsWith("VI")) return 6;
        if (s.startsWith("V")) return 5;
        if (s.startsWith("IV")) return 4;
        if (s.startsWith("III")) return 3;
        if (s.startsWith("II")) return 2;
        if (s.startsWith("I")) return 1;

        // Try numeric prefix
        StringBuilder numDigits = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) {
                numDigits.append(c);
            } else {
                break;
            }
        }
        if (!numDigits.isEmpty()) {
            return Integer.parseInt(numDigits.toString());
        }

        throw new IllegalArgumentException("Unknown standard: " + stdStr);
    }

    @Override
    public int compareTo(SchoolClass o) {
        int stdCompare = Integer.compare(this.standard, o.standard);
        if (stdCompare != 0) return stdCompare;
        return this.division.compareTo(o.division);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchoolClass that = (SchoolClass) o;
        return standard == that.standard && Objects.equals(division, that.division);
    }

    @Override
    public int hashCode() {
        return Objects.hash(standard, division);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
