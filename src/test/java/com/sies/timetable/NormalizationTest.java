package com.sies.timetable;

import com.sies.timetable.reconciliation.NormalizationDictionary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NormalizationTest {

    @Test
    public void testTeacherAliases() {
        assertEquals("HD", NormalizationDictionary.normalizeTeacherCode("NEW"));
        assertEquals("HD", NormalizationDictionary.normalizeTeacherCode("Teacher HD (NEW)"));
        assertEquals("HD", NormalizationDictionary.normalizeTeacherCode("Teacher HD"));

        assertEquals("AJ", NormalizationDictionary.normalizeTeacherCode("TEACHER : Teacher AJ"));
        assertEquals("AJ", NormalizationDictionary.normalizeTeacherCode("Teacher AJ"));

        assertEquals("SL", NormalizationDictionary.normalizeTeacherCode("TEACHER : Teacher SL"));
        assertEquals("SL", NormalizationDictionary.normalizeTeacherCode("Teacher SL"));

        assertEquals("SM", NormalizationDictionary.normalizeTeacherCode("Teacher SM"));

        assertEquals("NN", NormalizationDictionary.normalizeTeacherCode("Teacher NN"));

        assertEquals("BSS", NormalizationDictionary.normalizeTeacherCode("Teacher BSS"));

        assertEquals("ACK", NormalizationDictionary.normalizeTeacherCode("Teacher ACK"));

        assertEquals("JP", NormalizationDictionary.normalizeTeacherCode("Teacher JP"));
    }

    @Test
    public void testSubjectNormalizations() {
        assertEquals("P.E", NormalizationDictionary.normalizeSubject("P.E."));
        assertEquals("P.E", NormalizationDictionary.normalizeSubject("P.E '"));
        assertEquals("P.E", NormalizationDictionary.normalizeSubject("PE"));

        assertEquals("EVS I", NormalizationDictionary.normalizeSubject("EVS- I"));
        assertEquals("EVS I", NormalizationDictionary.normalizeSubject("EVS-I"));

        assertEquals("SCIENCE I", NormalizationDictionary.normalizeSubject("SCI I"));
        assertEquals("SCIENCE I", NormalizationDictionary.normalizeSubject("SCI-I"));

        assertEquals("MATHS", NormalizationDictionary.normalizeSubject("MATH"));
        assertEquals("MATHS", NormalizationDictionary.normalizeSubject("MATHS "));

        assertEquals("SCOUT & GUIDE", NormalizationDictionary.normalizeSubject("SCOUT & Guide"));
        assertEquals("SCOUT & GUIDE", NormalizationDictionary.normalizeSubject("SC.G"));
    }
}
