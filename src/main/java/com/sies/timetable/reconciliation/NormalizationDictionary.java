package com.sies.timetable.reconciliation;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages deterministic normalization of teacher aliases, subject abbreviations, and class notations.
 */
public class NormalizationDictionary {

    private static final Map<String, String> TEACHER_ALIAS_TO_CODE = new HashMap<>();
    private static final Map<String, String> CODE_TO_CANONICAL_NAME = new HashMap<>();
    private static final Map<String, String> SUBJECT_NORMALIZATION_MAP = new HashMap<>();

    static {
        // Teacher code / alias mappings (Sanitized generic format)
        registerTeacher("AAK", "Teacher AAK", "Teacher AAK", "AAK");
        registerTeacher("ACK", "Teacher ACK", "Teacher ACK", "ACK");
        registerTeacher("AD", "Teacher AD", "Teacher AD", "AD");
        registerTeacher("AJ", "Teacher AJ", "Teacher AJ", "TEACHER : Teacher AJ", "AJ");
        registerTeacher("AM", "Teacher AM", "Teacher AM", "AM");
        registerTeacher("AY", "Teacher AY", "Teacher AY", "AY");
        registerTeacher("BSS", "Teacher BSS", "Teacher BSS", "BSS");
        registerTeacher("CR", "Teacher CR", "Teacher CR", "CR");
        registerTeacher("DS", "Teacher DS", "Teacher DS", "DS");
        registerTeacher("FK", "Teacher FK", "Teacher FK", "FK");
        registerTeacher("GB", "Teacher GB", "Teacher GB", "GB");
        registerTeacher("HD", "Teacher HD", "Teacher HD", "HD", "NEW", "Teacher HD (NEW)");
        registerTeacher("JN", "Teacher JN", "Teacher JN", "JN");
        registerTeacher("JP", "Teacher JP", "Teacher JP", "JP");
        registerTeacher("MB", "Teacher MB", "Teacher MB", "MB");
        registerTeacher("MP", "Teacher MP", "Teacher MP", "MP");
        registerTeacher("MR", "Teacher MR", "Teacher MR", "MR");
        registerTeacher("NM", "Teacher NM", "Teacher NM", "NM");
        registerTeacher("NN", "Teacher NN", "Teacher NN", "NN");
        registerTeacher("NU", "Teacher NU", "Teacher NU", "NU");
        registerTeacher("PI", "Teacher PI", "Teacher PI", "PI");
        registerTeacher("PMA", "Teacher PMA", "Teacher PMA", "PMA");
        registerTeacher("PPT", "Teacher PPT", "Teacher PPT", "PPT");
        registerTeacher("PT", "Teacher PT", "Teacher PT", "PT");
        registerTeacher("RI", "Teacher RI", "Teacher RI", "RI");
        registerTeacher("SD", "Teacher SD", "Teacher SD", "SD");
        registerTeacher("SG", "Teacher SG", "Teacher SG", "SG");
        registerTeacher("SI", "Teacher SI", "Teacher SI", "SI");
        registerTeacher("SK", "Teacher SK", "Teacher SK", "SK");
        registerTeacher("SM", "Teacher SM", "Teacher SM", "SM");
        registerTeacher("SN", "Teacher SN", "Teacher SN", "SN");
        registerTeacher("SP", "Teacher SP", "Teacher SP", "SP");
        registerTeacher("SS", "Teacher SS", "Teacher SS", "SS");
        registerTeacher("SSP", "Teacher SSP", "Teacher SSP", "SSP");
        registerTeacher("SSS", "Teacher SSS", "Teacher SSS", "SSS");
        registerTeacher("SL", "Teacher SL", "Teacher SL", "TEACHER : Teacher SL", "SL");
        registerTeacher("TP", "Teacher TP", "Teacher TP", "TP");
        registerTeacher("UP", "Teacher UP", "Teacher UP", "UP");
        registerTeacher("VM", "Teacher VM", "Teacher VM", "VM");
        registerTeacher("VR", "Teacher VR", "Teacher VR", "VR");
        registerTeacher("VS", "Teacher VS", "Teacher VS", "VS");
        registerTeacher("VT", "Teacher VT", "Teacher VT", "VT");
        registerTeacher("VV", "Teacher VV", "Teacher VV", "VV");
        registerTeacher("YP", "Teacher YP", "Teacher YP", "YP");

        // Subject normalizations
        SUBJECT_NORMALIZATION_MAP.put("ENGLISH", "ENGLISH");
        SUBJECT_NORMALIZATION_MAP.put("ENG", "ENGLISH");
        SUBJECT_NORMALIZATION_MAP.put("MARATHI", "MARATHI");
        SUBJECT_NORMALIZATION_MAP.put("MAR", "MARATHI");
        SUBJECT_NORMALIZATION_MAP.put("HINDI", "HINDI");
        SUBJECT_NORMALIZATION_MAP.put("HIN", "HINDI");
        SUBJECT_NORMALIZATION_MAP.put("HIND", "HINDI");
        SUBJECT_NORMALIZATION_MAP.put("SANSKRIT", "SANSKRIT");
        SUBJECT_NORMALIZATION_MAP.put("SKT", "SANSKRIT");
        SUBJECT_NORMALIZATION_MAP.put("MATHS", "MATHS");
        SUBJECT_NORMALIZATION_MAP.put("MATH", "MATHS");
        SUBJECT_NORMALIZATION_MAP.put("MAT", "MATHS");
        SUBJECT_NORMALIZATION_MAP.put("MATHS ", "MATHS");
        SUBJECT_NORMALIZATION_MAP.put("GEOMETRY", "GEOMETRY");
        SUBJECT_NORMALIZATION_MAP.put("GEOM", "GEOMETRY");
        SUBJECT_NORMALIZATION_MAP.put("ALGEBRA", "ALGEBRA");
        SUBJECT_NORMALIZATION_MAP.put("ALG", "ALGEBRA");
        SUBJECT_NORMALIZATION_MAP.put("SCIENCE", "SCIENCE");
        SUBJECT_NORMALIZATION_MAP.put("SCI", "SCIENCE");
        SUBJECT_NORMALIZATION_MAP.put("SCIENCE I", "SCIENCE I");
        SUBJECT_NORMALIZATION_MAP.put("SCIENCE I ", "SCIENCE I");
        SUBJECT_NORMALIZATION_MAP.put("SCI-I", "SCIENCE I");
        SUBJECT_NORMALIZATION_MAP.put("SCI I", "SCIENCE I");
        SUBJECT_NORMALIZATION_MAP.put("SCIENCE II", "SCIENCE II");
        SUBJECT_NORMALIZATION_MAP.put("SCI-II", "SCIENCE II");
        SUBJECT_NORMALIZATION_MAP.put("SCI II", "SCIENCE II");
        SUBJECT_NORMALIZATION_MAP.put("EVS- I", "EVS I");
        SUBJECT_NORMALIZATION_MAP.put("EVS-I", "EVS I");
        SUBJECT_NORMALIZATION_MAP.put("EVS I", "EVS I");
        SUBJECT_NORMALIZATION_MAP.put("EVS- II", "EVS II");
        SUBJECT_NORMALIZATION_MAP.put("EVS-II", "EVS II");
        SUBJECT_NORMALIZATION_MAP.put("EVS II", "EVS II");
        SUBJECT_NORMALIZATION_MAP.put("HISTORY", "HISTORY");
        SUBJECT_NORMALIZATION_MAP.put("HIST", "HISTORY");
        SUBJECT_NORMALIZATION_MAP.put("GEOGRAPHY", "GEOGRAPHY");
        SUBJECT_NORMALIZATION_MAP.put("GEOG", "GEOGRAPHY");
        SUBJECT_NORMALIZATION_MAP.put("GFOG", "GEOGRAPHY");
        SUBJECT_NORMALIZATION_MAP.put("P.E", "P.E");
        SUBJECT_NORMALIZATION_MAP.put("P.E.", "P.E");
        SUBJECT_NORMALIZATION_MAP.put("PE", "P.E");
        SUBJECT_NORMALIZATION_MAP.put("P.E '", "P.E");
        SUBJECT_NORMALIZATION_MAP.put("P.E'", "P.E");
        SUBJECT_NORMALIZATION_MAP.put("COMPUTER", "COMPUTER");
        SUBJECT_NORMALIZATION_MAP.put("COMP", "COMPUTER");
        SUBJECT_NORMALIZATION_MAP.put("VALUE EDUCATION", "VALUE EDUCATION");
        SUBJECT_NORMALIZATION_MAP.put("VALUE", "VALUE EDUCATION");
        SUBJECT_NORMALIZATION_MAP.put("CRAFT", "CRAFT");
        SUBJECT_NORMALIZATION_MAP.put("CRAFT ", "CRAFT");
        SUBJECT_NORMALIZATION_MAP.put("MUSIC", "MUSIC");
        SUBJECT_NORMALIZATION_MAP.put("ART", "ART");
        SUBJECT_NORMALIZATION_MAP.put("ART ", "ART");
        SUBJECT_NORMALIZATION_MAP.put("LIBRARY", "LIBRARY");
        SUBJECT_NORMALIZATION_MAP.put("LIB", "LIBRARY");
        SUBJECT_NORMALIZATION_MAP.put("SCOUT & GUIDE", "SCOUT & GUIDE");
        SUBJECT_NORMALIZATION_MAP.put("SCOUT & Guide", "SCOUT & GUIDE");
        SUBJECT_NORMALIZATION_MAP.put("SC.G", "SCOUT & GUIDE");
        SUBJECT_NORMALIZATION_MAP.put("SELF DEFENCE", "SELF DEFENCE");
        SUBJECT_NORMALIZATION_MAP.put("SELF DEF", "SELF DEFENCE");
        SUBJECT_NORMALIZATION_MAP.put("SELF", "SELF DEFENCE");
        SUBJECT_NORMALIZATION_MAP.put("WATER SECURITY", "WATER SECURITY");
        SUBJECT_NORMALIZATION_MAP.put("WATER", "WATER SECURITY");
        SUBJECT_NORMALIZATION_MAP.put("WATTR", "WATER SECURITY");
        SUBJECT_NORMALIZATION_MAP.put("CCA", "CCA");
    }

    private static void registerTeacher(String canonicalCode, String fullName, String... aliases) {
        CODE_TO_CANONICAL_NAME.put(canonicalCode, fullName);
        TEACHER_ALIAS_TO_CODE.put(canonicalCode.toUpperCase().trim(), canonicalCode);
        TEACHER_ALIAS_TO_CODE.put(fullName.toUpperCase().trim(), canonicalCode);
        for (String alias : aliases) {
            TEACHER_ALIAS_TO_CODE.put(alias.toUpperCase().trim(), canonicalCode);
        }
    }

    public static String normalizeTeacherCode(String input) {
        if (input == null) return null;
        String clean = input.trim().toUpperCase().replace("\n", " ").replaceAll("\\s+", " ");
        return TEACHER_ALIAS_TO_CODE.getOrDefault(clean, clean);
    }

    public static String getCanonicalTeacherName(String code) {
        if (code == null) return "UNKNOWN";
        String normalizedCode = normalizeTeacherCode(code);
        return CODE_TO_CANONICAL_NAME.getOrDefault(normalizedCode, normalizedCode);
    }

    public static String normalizeSubject(String input) {
        if (input == null) return null;
        String clean = input.trim().toUpperCase().replace("\n", " ").replaceAll("\\s+", " ");
        return SUBJECT_NORMALIZATION_MAP.getOrDefault(clean, clean);
    }
}
