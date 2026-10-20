package com.sies.timetable;

import com.sies.timetable.config.SchoolConfig;
import com.sies.timetable.model.ManualTimetableModel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ConstraintConfigurationTest {

    @Test
    public void testDefaultConstraintsInitialization() {
        SchoolConfig config = SchoolConfig.createDefault();

        // 1. All H1–H10 should be enabled by default
        for (int i = 1; i <= 10; i++) {
            String id = "H" + i;
            assertTrue(config.isHardConstraintEnabled(id), "Default configuration should have " + id + " enabled");
        }

        // 2. Mandatory constraints: H3, H4, H9
        assertTrue(config.isHardConstraintMandatory("H3"), "H3 must be mandatory");
        assertTrue(config.isHardConstraintMandatory("H4"), "H4 must be mandatory");
        assertTrue(config.isHardConstraintMandatory("H9"), "H9 must be mandatory");
        assertFalse(config.isHardConstraintMandatory("H1"), "H1 should be optional");
        assertFalse(config.isHardConstraintMandatory("H2"), "H2 should be optional");
        assertFalse(config.isHardConstraintMandatory("H5"), "H5 should be optional");
        assertFalse(config.isHardConstraintMandatory("H6"), "H6 should be optional");

        // 3. Default numerical parameters
        assertEquals(2, config.getH2MaxContinuousLectures(), "Default H2 continuous lectures should be 2");
        assertEquals(1, config.getH5MaxConcurrentLabClasses(), "Default H5 lab classes should be 1");
        assertEquals(1, config.getH6ProhibitedPeriod(), "Default H6 prohibited period should be 1");
        assertEquals(2, config.getH8MaxActivityPeriodsPerDay(), "Default H8 activity periods should be 2");

        // 4. Default soft constraints S1–S6
        for (int i = 1; i <= 6; i++) {
            String id = "S" + i;
            assertTrue(config.isSoftConstraintEnabled(id), "Default configuration should have " + id + " enabled");
        }
        assertEquals(100, config.getSoftConstraintWeight("S1"));
        assertEquals(80, config.getSoftConstraintWeight("S2"));
        assertEquals(50, config.getSoftConstraintWeight("S3"));
        assertEquals(40, config.getSoftConstraintWeight("S4"));
        assertEquals(30, config.getSoftConstraintWeight("S5"));
        assertEquals(20, config.getSoftConstraintWeight("S6"));
    }

    @Test
    public void testMandatoryHardConstraintsCannotBeDisabled() {
        SchoolConfig config = SchoolConfig.createDefault();

        assertThrows(IllegalArgumentException.class, () -> config.setHardConstraintEnabled("H3", false),
                "Disabling mandatory H3 must throw IllegalArgumentException");
        assertThrows(IllegalArgumentException.class, () -> config.setHardConstraintEnabled("H4", false),
                "Disabling mandatory H4 must throw IllegalArgumentException");
        assertThrows(IllegalArgumentException.class, () -> config.setHardConstraintEnabled("H9", false),
                "Disabling mandatory H9 must throw IllegalArgumentException");

        assertTrue(config.isHardConstraintEnabled("H3"), "H3 must remain enabled");
        assertTrue(config.isHardConstraintEnabled("H4"), "H4 must remain enabled");
        assertTrue(config.isHardConstraintEnabled("H9"), "H9 must remain enabled");
    }

    @Test
    public void testOptionalHardConstraintsCanBeToggled() {
        SchoolConfig config = SchoolConfig.createDefault();

        config.setHardConstraintEnabled("H1", false);
        config.setHardConstraintEnabled("H6", false);
        config.setHardConstraintEnabled("H8", false);

        assertFalse(config.isHardConstraintEnabled("H1"));
        assertFalse(config.isHardConstraintEnabled("H6"));
        assertFalse(config.isHardConstraintEnabled("H8"));

        // Re-enable
        config.setHardConstraintEnabled("H1", true);
        assertTrue(config.isHardConstraintEnabled("H1"));
    }

    @Test
    public void testNumericParametersValidationAndBoundaries() {
        SchoolConfig config = SchoolConfig.createDefault();

        // H2: 1..8
        config.setH2MaxContinuousLectures(3);
        assertEquals(3, config.getH2MaxContinuousLectures());
        assertThrows(IllegalArgumentException.class, () -> config.setH2MaxContinuousLectures(0));
        assertThrows(IllegalArgumentException.class, () -> config.setH2MaxContinuousLectures(9));

        // H5: 1..10
        config.setH5MaxConcurrentLabClasses(2);
        assertEquals(2, config.getH5MaxConcurrentLabClasses());
        assertThrows(IllegalArgumentException.class, () -> config.setH5MaxConcurrentLabClasses(0));
        assertThrows(IllegalArgumentException.class, () -> config.setH5MaxConcurrentLabClasses(11));

        // H6: 1..12
        config.setH6ProhibitedPeriod(2);
        assertEquals(2, config.getH6ProhibitedPeriod());
        assertThrows(IllegalArgumentException.class, () -> config.setH6ProhibitedPeriod(0));
        assertThrows(IllegalArgumentException.class, () -> config.setH6ProhibitedPeriod(13));

        // H8: 1..8
        config.setH8MaxActivityPeriodsPerDay(3);
        assertEquals(3, config.getH8MaxActivityPeriodsPerDay());
        assertThrows(IllegalArgumentException.class, () -> config.setH8MaxActivityPeriodsPerDay(0));
        assertThrows(IllegalArgumentException.class, () -> config.setH8MaxActivityPeriodsPerDay(9));
    }

    @Test
    public void testSoftConstraintWeightsAndBounds() {
        SchoolConfig config = SchoolConfig.createDefault();

        config.setSoftConstraintWeight("S1", 250);
        assertEquals(250, config.getSoftConstraintWeight("S1"));

        config.setSoftConstraintEnabled("S1", false);
        assertFalse(config.isSoftConstraintEnabled("S1"));

        assertThrows(IllegalArgumentException.class, () -> config.setSoftConstraintWeight("S1", -1));
        assertThrows(IllegalArgumentException.class, () -> config.setSoftConstraintWeight("S1", 501));
    }

    @Test
    public void testYamlConstraintsSerializationRoundtrip() {
        SchoolConfig config = SchoolConfig.createDefault();
        config.setHardConstraintEnabled("H1", false);
        config.setH2MaxContinuousLectures(3);
        config.setH5MaxConcurrentLabClasses(2);
        config.setH6ProhibitedPeriod(3);
        config.setH8MaxActivityPeriodsPerDay(1);
        config.setSoftConstraintWeight("S2", 150);
        config.setSoftConstraintEnabled("S3", false);

        Map<String, Object> map = config.toConstraintsMap();
        assertNotNull(map);
        assertTrue(map.containsKey("hard_constraints"));
        assertTrue(map.containsKey("soft_constraints"));
        assertTrue(map.containsKey("weights"));

        @SuppressWarnings("unchecked")
        Map<String, Object> hardMap = (Map<String, Object>) map.get("hard_constraints");
        @SuppressWarnings("unchecked")
        Map<String, Object> h1Entry = (Map<String, Object>) hardMap.get("H1");
        assertEquals(false, h1Entry.get("enabled"));

        @SuppressWarnings("unchecked")
        Map<String, Object> h2Entry = (Map<String, Object>) hardMap.get("H2");
        assertEquals(3, h2Entry.get("max_continuous_lectures"));

        @SuppressWarnings("unchecked")
        Map<String, Object> h5Entry = (Map<String, Object>) hardMap.get("H5");
        assertEquals(2, h5Entry.get("max_concurrent_lab_classes"));

        @SuppressWarnings("unchecked")
        Map<String, Object> h6Entry = (Map<String, Object>) hardMap.get("H6");
        assertEquals(3, h6Entry.get("prohibited_period"));

        @SuppressWarnings("unchecked")
        Map<String, Object> h8Entry = (Map<String, Object>) hardMap.get("H8");
        assertEquals(1, h8Entry.get("max_activity_periods_per_day"));

        @SuppressWarnings("unchecked")
        Map<String, Object> softMap = (Map<String, Object>) map.get("soft_constraints");
        @SuppressWarnings("unchecked")
        Map<String, Object> s2Entry = (Map<String, Object>) softMap.get("S2");
        assertEquals(150, s2Entry.get("weight"));

        @SuppressWarnings("unchecked")
        Map<String, Object> s3Entry = (Map<String, Object>) softMap.get("S3");
        assertEquals(false, s3Entry.get("enabled"));
    }

    @Test
    public void testManualTimetableModelConstraintsPropagation() {
        ManualTimetableModel model = new ManualTimetableModel();

        // Default model state
        assertEquals(2, model.getH2MaxContinuousLectures());
        assertEquals(1, model.getH5MaxConcurrentLabClasses());
        assertEquals(1, model.getH6ProhibitedPeriod());
        assertEquals(2, model.getH8MaxActivityPeriodsPerDay());

        // Update parameters
        model.setH2MaxContinuousLectures(3);
        model.setH5MaxConcurrentLabClasses(2);
        model.setH6ProhibitedPeriod(2);
        model.setH8MaxActivityPeriodsPerDay(1);
        model.setHardConstraintEnabled("H1", false);
        model.setSoftConstraintWeight("S1", 120);

        SchoolConfig builtConfig = model.buildSchoolConfig();
        assertFalse(builtConfig.isHardConstraintEnabled("H1"));
        assertEquals(3, builtConfig.getH2MaxContinuousLectures());
        assertEquals(2, builtConfig.getH5MaxConcurrentLabClasses());
        assertEquals(2, builtConfig.getH6ProhibitedPeriod());
        assertEquals(1, builtConfig.getH8MaxActivityPeriodsPerDay());
        assertEquals(120, builtConfig.getSoftConstraintWeight("S1"));
    }
}
