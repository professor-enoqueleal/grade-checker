package br.com.gradechecker.adapter.inbound.http.routes;

import br.com.gradechecker.adapter.inbound.http.Call;
import br.com.gradechecker.adapter.inbound.http.Maps;
import br.com.gradechecker.adapter.inbound.http.Request;
import br.com.gradechecker.adapter.inbound.http.Response;
import br.com.gradechecker.adapter.inbound.http.Router;
import br.com.gradechecker.core.application.CommitHistoryQuery;
import br.com.gradechecker.core.application.ContributionsQuery;
import br.com.gradechecker.core.application.ContributionsQuery.GradeKind;
import br.com.gradechecker.core.attribution.AttributedCommit;
import br.com.gradechecker.core.attribution.AttributionSource;
import br.com.gradechecker.core.grading.GradeCalculator.StudentGrade;
import br.com.gradechecker.core.grading.GradingConfig;
import br.com.gradechecker.core.grading.GradingExplanation;
import br.com.gradechecker.core.history.CommitActivity;
import br.com.gradechecker.core.history.HistoryCommit;
import br.com.gradechecker.core.period.Milestones;
import br.com.gradechecker.core.period.PeriodResolver;
import br.com.gradechecker.core.period.PeriodResolver.ResolvedPeriod;
import br.com.gradechecker.core.view.CommitRow;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static br.com.gradechecker.adapter.inbound.http.Router.Access.USER;

/**
 * Configuration of the pages, contributions report and commit history (capability serverless-contributions-api): reads the
 * request, calls the use cases of grade-checker-core (ContributionsQuery, CommitHistoryQuery) and writes JSON.
 */
public final class ReportRoutes {

    private final ContributionsQuery contributions;
    private final CommitHistoryQuery history;
    private final PeriodResolver periods;
    private final GradingConfig grading;

    public ReportRoutes(ContributionsQuery contributions, CommitHistoryQuery history, PeriodResolver periods, GradingConfig grading) {
        this.contributions = contributions;
        this.history = history;
        this.periods = periods;
        this.grading = grading;
    }

    public void register(Router router) {
        router.get("/api/config", USER, this::config)
                .get("/api/groups/{id}/contributions", USER, this::contributions)
                .get("/api/groups/{id}/commits", USER, this::commits);
    }

    private List<Object> periodOptions(String selected) {
        List<Object> options = new ArrayList<>();
        for (PeriodResolver.PeriodOption o : periods.options(selected)) {
            Milestones.Milestone m = milestone(o.getKey());
            boolean dated = m != null && m.isConfigured();
            options.add(Maps.of("key", o.getKey(), "label", o.getLabel(), "enabled", o.isEnabled(), "hint", o.getHint(),
                    "start", dated ? m.getStartDate().toString() : null, "end", dated ? m.getEndDate().toString() : null));
        }
        return options;
    }

    private Milestones.Milestone milestone(String key) {
        for (Milestones.Milestone m : periods.getMilestones().getAll()) {
            if (m.getKey().equals(key)) return m;
        }
        return null;
    }

    /** Start of the period as local date-time in the milestone zone (yyyy-MM-ddTHH:mm); null for All. The SPA formats it. */
    private String local(Instant instant) {
        return instant == null ? null : periods.toInputValue(instant);
    }

    /** Commit date (ISO from GitHub) as local date-time in the milestone zone; null when unparseable. */
    private String localDateTime(String iso) {
        if (iso == null || iso.trim().isEmpty()) return null;
        try {
            return periods.toInputValue(OffsetDateTime.parse(iso).toInstant());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------- config

    private Response config(Call call) {
        return Response.ok(Maps.of(
                "periods", periodOptions(null),
                "customUntil", periods.toInputValue(periods.all().getUntil()),
                "gradingExplanation", GradingExplanation.of(grading, periods.getMilestones())));
    }

    // ------------------------------------------------------------------------------------------- contributions report

    private Response contributions(Call call) {
        Request request = call.request();
        ContributionsQuery.Report report = contributions.report(GroupRoutes.id(call), request.query("period"),
                request.query("since"), request.query("until"), call.token());
        ContributionsQuery.Grading grades = report.getGrading();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ContributionsQuery.Row r : report.getRows()) {
            Map<String, Object> row = Maps.of("author", r.getAuthor(), "commits", r.getCommits(), "totalAdditions", r.getTotalAdditions(),
                    "totalDeletions", r.getTotalDeletions(), "percentage", r.getPercentage(), "linked", r.isLinked(),
                    "gitIdentities", r.getGitIdentities(), "identityKey", r.getIdentityKey(),
                    "manualIdentities", r.getManualIdentities());
            if (grades != null && grades.getKind() == GradeKind.MILESTONE) {
                StudentGrade g = r.getGrade();
                row.put("grade", g == null ? null : g.getGrade());
                row.put("gradeDetails", g == null ? null : Maps.of("effectiveLines", g.getEffectiveLines(),
                        "activeDays", g.getActiveDays(), "relevantCommits", g.getRelevantCommits(),
                        "indexShare", g.getIndexShare(), "ratio", g.getRatio()));
            } else if (grades != null) {
                row.put("milestoneGrades", r.getMilestoneGrades() == null ? null : r.getMilestoneGrades().getMilestones());
                row.put("total", r.getMilestoneGrades() == null ? null : r.getMilestoneGrades().getTotal());
            }
            rows.add(row);
        }

        return Response.ok(Maps.of(
                "period", report.getPeriod().getKey(),
                "periodText", report.getPeriodText(),
                "since", local(report.getPeriod().getSince()),
                "until", local(report.getPeriod().getUntil()),
                "totalCommits", report.getTotalCommits(),
                "truncated", report.isTruncated(),
                "grading", grading(grades),
                "rows", rows));
    }

    /** The "grading" block; null for the custom period. */
    private static Map<String, Object> grading(ContributionsQuery.Grading g) {
        if (g == null) return null;
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("maxPoints", g.getMaxPoints());
        info.put("tolerance", g.getTolerance());
        if (g.getKind() == GradeKind.MILESTONE) {
            info.put("fairShare", g.getFairShare());
            info.put("students", g.getStudents());
            info.put("excludedCommits", g.getExcludedCommits());
            info.put("excludedLines", g.getExcludedLines());
            info.put("provisional", g.isProvisional());
        } else {
            info.put("students", g.getStudents());
            info.put("provisionalMilestones", g.getProvisionalMilestones());
        }
        return info;
    }

    // ------------------------------------------------------------------------------------------- commit history

    private Response commits(Call call) {
        Request request = call.request();
        String author = request.query("author");
        long id = author == null ? 0 : GroupRoutes.id(call);              // the missing author is reported first
        CommitHistoryQuery.History h = history.history(id, author, request.query("period"), request.query("since"),
                request.query("until"), call.token());
        ResolvedPeriod period = h.getPeriod();

        List<Object> rows = new ArrayList<>();
        for (AttributedCommit commit : h.getCommits()) rows.add(row(commit));

        return Response.ok(Maps.of(
                "author", h.getAuthor(),
                "period", period.getKey(),
                "periodText", periods.describe(period),
                "since", local(period.getSince()),
                "until", local(period.getUntil()),
                "periodWarning", h.getPeriodWarning(),
                "customSince", periods.toInputValue(period.getSince()),
                "customUntil", periods.toInputValue(period.getUntil()),
                "periods", periodOptions(period.getKey()),
                "alsoCounted", h.getAlsoCounted(),
                "truncated", h.isTruncated(),
                "commits", rows,
                "activity", h.getActivity() == null ? null : activity(h.getActivity())));
    }

    private Map<String, Object> row(AttributedCommit attributed) {
        HistoryCommit commit = attributed.getCommit();
        boolean manual = attributed.getSource() == AttributionSource.MANUAL;
        String date = commit.getCommittedDate() == null ? "" : periods.formatDateTime(commit.getCommittedDate());
        String url = commit.getUrl() != null && commit.getUrl().startsWith("https://github.com/") ? commit.getUrl() : null;
        String identity = attributed.getSource() == AttributionSource.ACCOUNT ? null : attributed.getIdentity();
        CommitRow row = new CommitRow(date, commit.getMessage() == null ? "" : commit.getMessage(), commit.getOid(), url, identity, manual);
        return Maps.of("day", row.getDay(), "time", row.getTime(), "message", row.getMessage(), "sha", row.getSha(),
                "url", row.getUrl(), "identity", row.getIdentity(), "manual", manual,
                "date", localDateTime(commit.getCommittedDate()));
    }

    private static Map<String, Object> activity(CommitActivity a) {
        List<Object> series = new ArrayList<>();
        for (CommitActivity.Series s : a.getSeries()) series.add(Maps.of("key", s.getKey(), "label", s.getLabel(), "counts", s.getCounts()));
        return Maps.of("granularity", a.getGranularity(), "title", a.getTitle(), "summary", a.getSummary(),
                "ariaLabel", a.getAriaLabel(), "labels", a.getLabels(), "tooltips", a.getTooltips(), "series", series,
                "starts", a.getStarts(), "total", a.getTotal(), "activeDays", a.getActiveDays());
    }
}
