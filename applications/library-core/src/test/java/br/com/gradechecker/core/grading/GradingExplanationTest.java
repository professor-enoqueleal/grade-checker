package br.com.gradechecker.core.grading;

import br.com.gradechecker.core.grading.GradeCalculator;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.period.Milestones;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;

/** openspec capability milestone-grading: explanation of the grade, from the configuration in use, in pt-BR. */
public class GradingExplanationTest {

    private static Milestones milestones() {
        Properties p = new Properties();
        p.setProperty("milestone.timezone", "America/Sao_Paulo");
        p.setProperty("milestone.1.label", "Milestone 1");
        p.setProperty("milestone.1.start", "2026-08-13");
        p.setProperty("milestone.1.end", "2026-09-17");
        p.setProperty("milestone.2.label", "Milestone 2");
        p.setProperty("milestone.2.start", "2026-09-18");
        p.setProperty("milestone.2.end", "2026-10-29");
        p.setProperty("milestone.3.label", "Milestone 3");
        p.setProperty("milestone.3.start", "2026-10-30");
        p.setProperty("milestone.3.end", "2026-11-19");
        return Milestones.of(p);
    }

    private static GradingConfig grading(String... keyValues) {
        Properties p = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) p.setProperty(keyValues[i], keyValues[i + 1]);
        return GradingConfig.of(p);
    }

    @Test
    public void defaultsOfTheSpecificationFormattedInPortuguese() {
        GradingExplanation e = GradingExplanation.of(grading("grading.excludedLogins", "professor-enoqueleal"), milestones());

        assertEquals("3", e.getMaxPoints());
        assertEquals("9", e.getTotalPoints());
        assertEquals("50%", e.getWeightEffectiveLines());
        assertEquals("30%", e.getWeightActiveDays());
        assertEquals("20%", e.getWeightRelevantCommits());
        assertEquals(400, e.getLineCap());
        assertEquals(30, e.getReorganizationMinFiles());
        assertEquals("60%", e.getReorganizationBalance());
        assertEquals("10%", e.getReorganizationFactor());
        assertEquals(20, e.getRelevantCommitMinLines());
        assertEquals("80%", e.getTolerance());
        assertEquals("horário de Brasília", e.getTimeZone());
        assertTrue(e.isExcludesLogins());

        List<GradingExplanation.MilestoneRow> rows = e.getMilestones();
        assertEquals(3, rows.size());
        assertEquals("Milestone 1", rows.get(0).getLabel());
        assertEquals("13/08/2026", rows.get(0).getStart());
        assertEquals("17/09/2026", rows.get(0).getEnd());

        assertEquals(4, e.getExampleStudents());
        assertEquals("25%", e.getExampleFairShare());
        assertEquals("20%", e.getExampleFullShare());
        assertEquals("3,00", e.getExampleFullGrade());
        assertEquals("10%", e.getExampleLowShare());
        assertEquals("1,50", e.getExampleLowGrade());
    }

    @Test
    public void changedConfigurationChangesTheTextAndTheExample() {
        GradingExplanation e = GradingExplanation.of(grading(
                "grading.weight.effectiveLines", "1", "grading.weight.activeDays", "1", "grading.weight.relevantCommits", "1",
                "grading.tolerance", "0.5", "grading.milestone.maxPoints", "2.5", "grading.commit.lineCap", "300"), milestones());

        assertEquals("33,3%", e.getWeightEffectiveLines());      // normalized
        assertEquals("33,3%", e.getWeightActiveDays());
        assertEquals("50%", e.getTolerance());
        assertEquals("2,5", e.getMaxPoints());
        assertEquals("7,5", e.getTotalPoints());
        assertEquals(300, e.getLineCap());
        assertEquals("12,5%", e.getExampleFullShare());
        assertEquals("2,50", e.getExampleFullGrade());
        assertEquals("6,3%", e.getExampleLowShare());           // 6,25% shown with one decimal
        assertEquals("1,25", e.getExampleLowGrade());
        assertFalse(e.isExcludesLogins());
    }

    @Test
    public void exampleIsTheSameAsTheRealGradeCalculation() {
        GradingConfig config = grading();
        GradingExplanation e = GradingExplanation.of(config, milestones());

        // 4 students; every indicator split 10/30/30/30 (index share 10%) and 20/20/30/30 (20%)
        Map<String, GradeCalculator.Indicators> low = new LinkedHashMap<>();
        low.put("a", new GradeCalculator.Indicators(10, 10, 10, 10, 10));
        for (String s : new String[]{"b", "c", "d"}) low.put(s, new GradeCalculator.Indicators(30, 30, 30, 30, 30));
        double lowGrade = new GradeCalculator(config).calculate(low, low.keySet()).student("a").getGrade();

        Map<String, GradeCalculator.Indicators> full = new LinkedHashMap<>();
        full.put("a", new GradeCalculator.Indicators(20, 20, 20, 20, 20));
        full.put("b", new GradeCalculator.Indicators(20, 20, 20, 20, 20));
        full.put("c", new GradeCalculator.Indicators(30, 30, 30, 30, 30));
        full.put("d", new GradeCalculator.Indicators(30, 30, 30, 30, 30));
        double fullGrade = new GradeCalculator(config).calculate(full, full.keySet()).student("a").getGrade();

        assertEquals(String.format(new java.util.Locale("pt", "BR"), "%.2f", lowGrade), e.getExampleLowGrade());
        assertEquals(String.format(new java.util.Locale("pt", "BR"), "%.2f", fullGrade), e.getExampleFullGrade());
    }
}
