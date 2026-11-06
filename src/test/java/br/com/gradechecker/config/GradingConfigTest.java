package br.com.gradechecker.config;

import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.*;

public class GradingConfigTest {

    @Test
    public void bundledFileMatchesSpecificationDefaults() {
        GradingConfig c = GradingConfig.load("/grading.properties");

        assertEquals(3, c.getMaxPoints(), 0);
        assertEquals(0.8, c.getTolerance(), 0);
        assertEquals(0.5, c.getWeightEffectiveLines(), 1e-9);
        assertEquals(0.3, c.getWeightActiveDays(), 1e-9);
        assertEquals(0.2, c.getWeightRelevantCommits(), 1e-9);
        assertEquals(400, c.getLineCap());
        assertTrue(c.getExcludedLogins().contains("professor-enoqueleal"));
    }

    @Test
    public void invalidValueFallsBackToDefault() {
        Properties p = new Properties();
        p.setProperty("grading.tolerance", "abc");
        p.setProperty("grading.commit.lineCap", "-5");

        GradingConfig c = GradingConfig.of(p);

        assertEquals(0.8, c.getTolerance(), 0);
        assertEquals(400, c.getLineCap());
    }

    @Test
    public void zeroWeightsFallBackToDefaultsAndWeightsAreNormalized() {
        Properties zero = new Properties();
        zero.setProperty("grading.weight.effectiveLines", "0");
        zero.setProperty("grading.weight.activeDays", "0");
        zero.setProperty("grading.weight.relevantCommits", "0");
        assertEquals(0.5, GradingConfig.of(zero).getWeightEffectiveLines(), 1e-9);

        Properties unnormalized = new Properties();
        unnormalized.setProperty("grading.weight.effectiveLines", "5");
        unnormalized.setProperty("grading.weight.activeDays", "3");
        unnormalized.setProperty("grading.weight.relevantCommits", "2");
        assertEquals(0.5, GradingConfig.of(unnormalized).getWeightEffectiveLines(), 1e-9);
    }

    @Test
    public void effectiveLinesApplyReorganizationDiscountAndCap() {
        GradingConfig c = GradingConfig.defaults();

        assertEquals(400, c.effectiveLines(1000, 53, 8), 1e-9);    // giant commit capped
        assertEquals(59.8, c.effectiveLines(300, 298, 44), 1e-9);  // reorganization: 598 x 0.1
        assertEquals(400, c.effectiveLines(500, 98, 44), 1e-9); // unbalanced: not a reorganization, only capped
        assertEquals(120, c.effectiveLines(100, 20, 3), 1e-9);
    }

    @Test
    public void relevantCommitsAndStudents() {
        GradingConfig c = GradingConfig.defaults();
        assertTrue(c.isRelevantCommit(15, 5));
        assertFalse(c.isRelevantCommit(10, 9));

        assertFalse(c.isStudentLogin("dependabot[bot]"));
        assertTrue(c.isStudentLogin("thiagofp77"));

        Properties p = new Properties();
        p.setProperty("grading.excludedLogins", "Professor-EnoqueLeal, monitor1");
        GradingConfig withExcluded = GradingConfig.of(p);
        assertFalse(withExcluded.isStudentLogin("professor-enoqueleal"));
        assertFalse(withExcluded.isStudentLogin("MONITOR1"));
    }
}
