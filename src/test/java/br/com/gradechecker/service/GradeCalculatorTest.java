package br.com.gradechecker.service;

import br.com.gradechecker.config.GradingConfig;
import br.com.gradechecker.service.GradeCalculator.Indicators;
import br.com.gradechecker.service.GradeCalculator.Result;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;

/** Scenarios of openspec capability milestone-grading. */
public class GradeCalculatorTest {

    private final GradeCalculator calculator = new GradeCalculator(GradingConfig.defaults());

    private static Indicators ind(double lines, int days, int relevant) {
        return new Indicators(lines, days, relevant, relevant, (int) lines);
    }

    @Test
    public void reproducesTheApprovedSimulationOfGroup6Milestone2() {
        Map<String, Indicators> authors = new LinkedHashMap<>();
        authors.put("matheusmbn070906", ind(1546, 7, 6));
        authors.put("Pedroasmfodsj", ind(894, 8, 6));
        authors.put("thiagofp77", ind(1294, 4, 6));
        authors.put("LucasMoura-dev777", ind(878, 5, 4));

        Result r = calculator.calculate(authors, List.of("matheusmbn070906", "Pedroasmfodsj", "thiagofp77", "LucasMoura-dev777"));

        assertEquals(0.25, r.getFairShare(), 0);
        assertEquals(3.00, r.of("matheusmbn070906").getGrade(), 0);
        assertEquals(3.00, r.of("pedroasmfodsj").getGrade(), 0);
        assertEquals(3.00, r.of("thiagofp77").getGrade(), 0);
        assertEquals(2.91, r.of("LucasMoura-dev777").getGrade(), 0);
        assertEquals(0.78, r.of("LucasMoura-dev777").getRatio(), 0);
    }

    @Test
    public void equalContributionGivesEveryoneMaxPoints() {
        Map<String, Indicators> authors = new LinkedHashMap<>();
        for (String s : List.of("a", "b", "c", "d")) authors.put(s, ind(100, 3, 2));

        Result r = calculator.calculate(authors, List.of("a", "b", "c", "d"));

        for (String s : List.of("a", "b", "c", "d")) assertEquals(3.00, r.of(s).getGrade(), 0);
    }

    @Test
    public void aboveFairShareIsCappedAndBelowToleranceIsProportional() {
        Map<String, Indicators> authors = new LinkedHashMap<>();
        authors.put("top", ind(400, 4, 4));   // 40%
        authors.put("b", ind(250, 3, 2));
        authors.put("c", ind(250, 2, 3));
        authors.put("low", ind(100, 1, 1));   // ~10%

        Result r = calculator.calculate(authors, List.of("top", "b", "c", "low"));

        assertEquals(3.00, r.of("top").getGrade(), 0);
        double ratio = r.of("low").getIndexShare() / 0.25;
        assertEquals(GradeCalculator.round(3 * Math.min(1, ratio / 0.8)), r.of("low").getGrade(), 0);
        assertTrue(r.of("low").getGrade() < 3);
    }

    @Test
    public void tenPercentShareGivesOneAndAHalf() {
        // only one indicator so that the share is exactly 10%
        Properties p = new Properties();
        p.setProperty("grading.weight.effectiveLines", "1");
        p.setProperty("grading.weight.activeDays", "0");
        p.setProperty("grading.weight.relevantCommits", "0");
        GradeCalculator linesOnly = new GradeCalculator(GradingConfig.of(p));

        Map<String, Indicators> authors = new LinkedHashMap<>();
        authors.put("a", ind(10, 1, 1));
        authors.put("b", ind(30, 1, 1));
        authors.put("c", ind(30, 1, 1));
        authors.put("d", ind(30, 1, 1));

        assertEquals(1.50, linesOnly.calculate(authors, List.of("a", "b", "c", "d")).of("a").getGrade(), 0);
    }

    @Test
    public void studentWithoutCommitsGetsZeroAndCountsInFairShare() {
        Map<String, Indicators> authors = new LinkedHashMap<>();
        authors.put("a", ind(100, 3, 2));
        authors.put("b", ind(100, 3, 2));
        authors.put("c", ind(100, 3, 2));

        Result r = calculator.calculate(authors, List.of("a", "b", "c", "absent"));

        assertEquals(0.25, r.getFairShare(), 0);
        assertEquals(0.00, r.of("absent").getGrade(), 0);
        assertEquals(3.00, r.of("a").getGrade(), 0);
    }

    @Test
    public void nobodyContributedGivesZeroToEveryone() {
        Result r = calculator.calculate(Map.of(), List.of("a", "b"));
        assertEquals(0.00, r.of("a").getGrade(), 0);
        assertEquals(0.00, r.of("b").getGrade(), 0);
    }

    @Test
    public void botsExcludedLoginsAndUnlinkedIdentitiesAreNotStudents() {
        Properties p = new Properties();
        p.setProperty("grading.excludedLogins", "professor-enoqueleal");
        GradeCalculator calc = new GradeCalculator(GradingConfig.of(p));

        Map<String, Indicators> authors = new LinkedHashMap<>();
        authors.put("a", ind(100, 3, 2));
        authors.put("b", ind(100, 3, 2));
        authors.put("professor-enoqueleal", new Indicators(50, 1, 1, 2, 60));
        authors.put("dependabot[bot]", new Indicators(10, 1, 0, 1, 10));
        authors.put("MATHEUS MEDEIROS BARBOSA NOVAES", new Indicators(28, 2, 1, 3, 28));

        Result r = calc.calculate(authors, List.of("a", "b", "professor-enoqueleal", "dependabot[bot]"));

        assertEquals(0.5, r.getFairShare(), 0);
        assertNull(r.of("professor-enoqueleal"));
        assertNull(r.of("dependabot[bot]"));
        assertNull(r.of("MATHEUS MEDEIROS BARBOSA NOVAES"));
        assertEquals(6, r.getExcludedCommits());
        assertEquals(98, r.getExcludedLines());
        assertEquals(3.00, r.of("a").getGrade(), 0);
    }

    @Test
    public void rosterMatchIsCaseInsensitive() {
        Map<String, Indicators> authors = Map.of("ThiagoFP77", ind(100, 1, 1));
        assertEquals(3.00, calculator.calculate(authors, List.of("thiagofp77")).of("THIAGOFP77").getGrade(), 0);
    }
}
