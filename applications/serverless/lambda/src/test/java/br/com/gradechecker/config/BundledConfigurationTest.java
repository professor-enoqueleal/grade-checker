package br.com.gradechecker.config;

import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.period.Milestones;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The configuration files of this application (src/main/resources), read by grade-checker-core from the classpath:
 * grading parameters of the specification and the three milestones of the semester.
 */
public class BundledConfigurationTest {

    @Test
    public void gradingFileMatchesSpecificationDefaults() {
        GradingConfig c = GradingConfig.fromClasspath();

        assertEquals(3, c.getMaxPoints(), 0);
        assertEquals(0.8, c.getTolerance(), 0);
        assertEquals(0.5, c.getWeightEffectiveLines(), 1e-9);
        assertEquals(0.3, c.getWeightActiveDays(), 1e-9);
        assertEquals(0.2, c.getWeightRelevantCommits(), 1e-9);
        assertEquals(400, c.getLineCap());
        assertTrue(c.getExcludedLogins().contains("professor-enoqueleal"));
    }

    @Test
    public void milestonesFileHasTheThreeMilestonesOfTheSemester() {
        Milestones milestones = Milestones.fromClasspath();

        assertTrue(milestones.getAll().stream().allMatch(Milestones.Milestone::isConfigured));
        assertEquals(LocalDate.parse("2026-08-13"), milestones.get(1).orElseThrow().getStartDate());
        assertEquals(LocalDate.parse("2026-11-19"), milestones.get(3).orElseThrow().getEndDate());
    }
}
