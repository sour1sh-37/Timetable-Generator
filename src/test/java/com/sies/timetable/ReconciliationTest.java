package com.sies.timetable;

import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.model.*;
import com.sies.timetable.parser.*;
import com.sies.timetable.reconciliation.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReconciliationTest {

    private static SchoolConfig config;
    private static AllocationReconciler.ReconciliationResult recResult;

    @BeforeAll
    public static void setUp() throws Exception {
        config = SchoolConfig.load("config/school-config.yaml");
        List<StandardwiseParser.StandardwiseRecord> stdRecords = StandardwiseParser.parse(config.getStandardwiseFile());
        List<TeacherwiseParser.ParsedTeacherBlock> teacherBlocks = TeacherwiseParser.parse(config.getTeacherwiseFile());
        List<ClassTeacherListParser.ParsedClassTeacherRow> ctRows = ClassTeacherListParser.parse(config.getClassTeachersFile());

        recResult = AllocationReconciler.reconcile(stdRecords, teacherBlocks, ctRows, config);
    }

    @Test
    public void testTotalSchoolClassesCount() {
        assertEquals(26, recResult.classes().size(), "There must be exactly 26 classes across standards V to X.");
    }

    @Test
    public void testTotalAtomicAllocationsCount() {
        assertEquals(393, recResult.allocations().size(), "There must be exactly 393 atomic teaching allocations.");
    }

    @Test
    public void testTotalPeriodsSum() {
        int totalPeriods = recResult.allocations().stream().mapToInt(TeachingAllocation::getPeriodCount).sum();
        assertEquals(1248, totalPeriods, "Total reconciled periods must be exactly 1,248.");
    }

    @Test
    public void testEveryClassHasExactly48Periods() {
        for (SchoolClass sc : recResult.classes()) {
            int classSum = recResult.allocations().stream()
                    .filter(a -> a.getSchoolClass().equals(sc))
                    .mapToInt(TeachingAllocation::getPeriodCount)
                    .sum();
            assertEquals(48, classSum, "Class " + sc + " must have exactly 48 required periods.");
        }
    }

    @Test
    public void testFullLoadTeachersReconciled() {
        Teacher jp = recResult.teacherMap().get("JP");
        assertNotNull(jp, "Teacher JP (JP) must exist in reconciled teacher map.");
        assertEquals(30, jp.getTotalWorkload(), "JP must have 30 total periods.");

        Teacher ack = recResult.teacherMap().get("ACK");
        assertNotNull(ack, "Teacher ACK (ACK) must exist in reconciled teacher map.");
        assertEquals(29, ack.getTotalWorkload(), "ACK must have 29 total periods.");

        Teacher bss = recResult.teacherMap().get("BSS");
        assertNotNull(bss, "Teacher BSS (BSS) must exist in reconciled teacher map.");
        assertEquals(29, bss.getTotalWorkload(), "BSS must have 29 total periods.");

        Teacher vs = recResult.teacherMap().get("VS");
        assertNotNull(vs, "Teacher VS (VS) must exist in reconciled teacher map.");
        assertEquals(33, vs.getTotalWorkload(), "VS must have 33 total periods.");
    }
}
