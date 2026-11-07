<!DOCTYPE html>
<html lang="en">
<head>
    <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
    <link href="/webjars/bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
    <meta charset="UTF-8">
    <title>Grade Checker | Commits Detail</title>
    <style>
        /* commit frequency chart: full width, fixed height */
        .commit-activity-box {
            position: relative;
            width: 100%;
            height: 220px;
        }
        .period-filter .btn.disabled {
            pointer-events: auto; /* keep the tooltip (title) on disabled milestones */
            cursor: not-allowed;
        }
        /* the message column takes the width left by the others */
        .commit-message {
            max-width: 0;
            width: 100%;
        }
        /* one line per message (and per identity on phones), "..." where the space ends */
        .commit-message-text,
        .commit-identity-inline {
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        /* phones: date and time on two lines; long identities (e-mails) break anywhere */
        @media (min-width: 576px) {
            .commit-date { white-space: nowrap; }
        }
        .commit-identity { overflow-wrap: anywhere; }
    </style>
</head>
<body>

<main class="container">
    <br>
    <nav style="--bs-breadcrumb-divider: '>';" aria-label="breadcrumb">
      <ol class="breadcrumb">
        <li class="breadcrumb-item"><a href="/home">Home</a></li>
        <li class="breadcrumb-item"><a href="<c:url value='/detail'><c:param name='id' value='${groupId}'/></c:url>">Group <c:out value="${groupId}"/></a></li>
        <li class="breadcrumb-item active" aria-current="page">Commits Detail</li>
      </ol>
    </nav>
    <hr>

    <h4>Commits Details</h4>

    <div class="details">

        <div class="mb-3 row">
            <span class="col-sm-2 col-form-label">Contributor Name:</span>
            <div class="col-sm-10">
                <span class="form-control-plaintext" id="author"><c:out value="${author}"/></span>
            </div>
        </div>

        <%-- identities without account counted for this student (openspec capability commit-history-page) --%>
        <c:if test="${not empty alsoCounted}">
            <div class="mb-3 row">
                <span class="col-sm-2 col-form-label">Also counted:</span>
                <div class="col-sm-10">
                    <span class="form-control-plaintext text-break" id="also-counted"><c:forEach var="identity" items="${alsoCounted}" varStatus="s"><c:out value="${identity}"/><c:if test="${not s.last}">, </c:if></c:forEach></span>
                </div>
            </div>
        </c:if>

        <div class="mb-3 row">
            <span class="col-sm-2 col-form-label">Total of commits</span>
            <div class="col-sm-10">
                <span class="form-control-plaintext" id="total-of-commits"><c:out value="${totalCommits}"/></span>
            </div>
        </div>

        <div class="mb-3 row">
            <span class="col-sm-2 col-form-label">Period:</span>
            <div class="col-sm-10">

                <!-- milestone buttons: each one is a GET link, so the URL represents the filter -->
                <div class="btn-group period-filter flex-wrap" role="group" aria-label="Period">
                    <c:forEach var="option" items="${periodOptions}">
                        <c:url var="periodUrl" value="/contributions-detail">
                            <c:param name="author" value="${author}"/>
                            <c:param name="owner" value="${owner}"/>
                            <c:param name="repo" value="${repo}"/>
                            <c:param name="groupId" value="${groupId}"/>
                            <c:param name="period" value="${option.key}"/>
                        </c:url>
                        <c:choose>
                            <c:when test="${option.enabled}">
                                <a class="btn btn-sm ${option.selected ? 'btn-primary active' : 'btn-outline-primary'}"
                                   href="<c:out value='${periodUrl}'/>"
                                   title="<c:out value='${option.hint}'/>"
                                   ${option.selected ? 'aria-current="true"' : ''}><c:out value="${option.label}"/></a>
                            </c:when>
                            <c:otherwise>
                                <span class="btn btn-sm btn-outline-secondary disabled" aria-disabled="true"
                                      title="<c:out value='${option.hint}'/>"><c:out value="${option.label}"/></span>
                            </c:otherwise>
                        </c:choose>
                    </c:forEach>
                </div>

                <div class="form-text" id="period-text"><c:out value="${periodText}"/> (merge commits excluded)</div>

            </div>
        </div>

        <div class="mb-3 row">
            <div class="col-sm-10 offset-sm-2">
                <details id="custom-period" ${periodKey == 'custom' ? 'open' : ''}>
                    <summary class="small">Custom period</summary>

                    <form action="/contributions-detail" method="get" class="row g-2 align-items-end mt-1">
                        <input type="hidden" name="author" value="<c:out value='${author}'/>">
                        <input type="hidden" name="owner" value="<c:out value='${owner}'/>">
                        <input type="hidden" name="repo" value="<c:out value='${repo}'/>">
                        <input type="hidden" name="groupId" value="<c:out value='${groupId}'/>">
                        <input type="hidden" name="period" value="custom">

                        <div class="col-auto">
                            <label for="since" class="form-label small">Since</label>
                            <input type="datetime-local" class="form-control form-control-sm" name="date-since" id="since" value="<c:out value='${customSince}'/>">
                        </div>
                        <div class="col-auto">
                            <label for="until" class="form-label small">Until</label>
                            <input type="datetime-local" class="form-control form-control-sm" name="date-until" id="until" value="<c:out value='${customUntil}'/>">
                        </div>
                        <div class="col-auto">
                            <button type="submit" class="btn btn-primary btn-sm">Apply</button>
                        </div>
                    </form>
                </details>
            </div>
        </div>

    </div>

    <hr>

    <c:if test="${not empty periodWarning}">
        <div class="alert alert-warning" role="alert"><c:out value="${periodWarning}"/></div>
    </c:if>

    <c:choose>
        <c:when test="${not empty githubError}">
            <div class="alert alert-danger" role="alert"><c:out value="${githubError}"/></div>
        </c:when>

        <c:when test="${totalCommits == 0}">
            <h4>No commits found for this period</h4>
        </c:when>

        <c:otherwise>
            <c:if test="${truncated}">
                <div class="alert alert-warning" role="alert">
                    Only the most recent commits of the repository in this period were considered (configured limit).
                </div>
            </c:if>

            <%-- commit frequency (openspec capability commit-history-page): same commits as the table, per day or per week --%>
            <c:if test="${not empty commitActivity}">
                <section class="mb-4" id="commit-activity" aria-labelledby="commit-activity-title">
                    <h6 class="mb-1" id="commit-activity-title"><c:out value="${commitActivity.title}"/></h6>
                    <p class="small text-body-secondary mb-2" id="commit-activity-summary"><c:out value="${commitActivity.summary}"/></p>
                    <div class="commit-activity-box">
                        <canvas id="commit-activity-chart" role="img" aria-label="<c:out value='${commitActivity.ariaLabel}'/>"></canvas>
                    </div>
                    <script type="application/json" id="commit-activity-data">${commitActivityJson}</script>
                </section>
            </c:if>

            <div class="table-responsive">
            <table class="table table-striped table-hover">
                <thead>
                    <tr>
                        <th scope="col">Date</th>
                        <th scope="col">Message</th>
                        <th scope="col">Commit</th>
                        <th scope="col" class="d-none d-sm-table-cell">Identity</th>
                    </tr>
                </thead>
                <tbody>
                    <c:forEach var="commit" items="${commits}">
                        <tr>
                            <td class="commit-date"><span class="text-nowrap"><c:out value="${commit.day}"/></span> <span class="text-nowrap"><c:out value="${commit.time}"/></span></td>
                            <td class="commit-message">
                                <div class="commit-message-text" title="<c:out value='${commit.message}'/>"><c:out value="${commit.message}"/></div>
                                <%-- phones: the Identity column is hidden and the identity (when any) goes below the message --%>
                                <c:if test="${not empty commit.identity}">
                                    <div class="commit-identity-inline d-sm-none small text-body-secondary" title="<c:out value='${commit.identity}'/>">
                                        <c:out value="${commit.identity}"/>
                                        <c:if test="${commit.manual}"><span class="badge text-bg-secondary fw-normal ms-1">manual</span></c:if>
                                    </div>
                                </c:if>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${not empty commit.url}">
                                        <a href="<c:out value='${commit.url}'/>" target="_blank" rel="noopener noreferrer" class="commit-sha" title="<c:out value='${commit.sha}'/>"><code><c:out value="${commit.shortSha}"/></code></a>
                                    </c:when>
                                    <c:otherwise><code class="commit-sha" title="<c:out value='${commit.sha}'/>"><c:out value="${commit.shortSha}"/></code></c:otherwise>
                                </c:choose>
                            </td>
                            <td class="commit-identity d-none d-sm-table-cell">
                                <c:choose>
                                    <c:when test="${empty commit.identity}">&mdash;</c:when>
                                    <c:otherwise>
                                        <c:out value="${commit.identity}"/>
                                        <c:if test="${commit.manual}"><span class="badge text-bg-secondary fw-normal ms-1 manual-badge">manual</span></c:if>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                        </tr>
                    </c:forEach>
                </tbody>
            </table>
            </div>
        </c:otherwise>
    </c:choose>

</main>

<script src="/js/date-formatting.js"></script>
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
<script>
    // commit frequency chart (openspec capability commit-history-page): the server sends the columns, the page draws
    (function () {
        const source = document.getElementById("commit-activity-data");
        if (!source || typeof Chart === "undefined") return;
        const activity = JSON.parse(source.textContent);

        // same colors as the group dashboard: milestones are an ordered blue ramp, outside the milestones is neutral gray
        const COLORS = { m1: "#86b6ef", m2: "#3987e5", m3: "#1c5cab", outside: "#868e96" };
        const series = activity.series;

        // round only the top of the last non-empty segment of each column
        function topRadius(ctx) {
            const datasets = ctx.chart.data.datasets;
            for (let d = datasets.length - 1; d > ctx.datasetIndex; d--) {
                if (datasets[d].data[ctx.dataIndex] > 0) return 0;
            }
            return { topLeft: 3, topRight: 3, bottomLeft: 0, bottomRight: 0 };
        }

        new Chart(document.getElementById("commit-activity-chart").getContext("2d"), {
            type: "bar",
            data: {
                labels: activity.labels,
                datasets: series.map(function (s) {
                    return {
                        label: s.label, key: s.key, data: s.counts,
                        backgroundColor: COLORS[s.key] || COLORS.outside,
                        hoverBackgroundColor: COLORS[s.key] || COLORS.outside,
                        borderColor: "#ffffff",
                        borderWidth: { top: 2, bottom: 0, left: 0, right: 0 },
                        borderSkipped: false,
                        borderRadius: topRadius,
                        maxBarThickness: 28
                    };
                })
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: false,
                scales: {
                    x: { stacked: true, grid: { display: false }, border: { display: false },
                         ticks: { color: "#6c757d", autoSkip: true, maxRotation: 0 } },
                    y: { stacked: true, beginAtZero: true, grid: { color: "#e9ecef" }, border: { display: false },
                         ticks: { color: "#6c757d", precision: 0 } }
                },
                plugins: {
                    legend: { display: series.length > 1, position: "bottom", labels: { boxWidth: 12, boxHeight: 12 } },
                    tooltip: {
                        filter: function (item) { return item.raw > 0; },
                        callbacks: {
                            title: function (items) { return activity.tooltips[items[0].dataIndex]; },
                            label: function (item) { return item.dataset.label + ": " + item.raw + (item.raw === 1 ? " commit" : " commits"); },
                            footer: function (items) {
                                if (series.length < 2) return "";
                                const i = items[0].dataIndex;
                                const total = series.reduce(function (sum, s) { return sum + s.counts[i]; }, 0);
                                return "Total: " + total + (total === 1 ? " commit" : " commits");
                            }
                        }
                    }
                }
            }
        });
    })();
</script>

</body>
</html>
