<!DOCTYPE html>
<html lang="en">
<head>
    <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
    <link href="/webjars/bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
    <script src="/webjars/jquery/3.7.1/jquery.min.js"></script>
    <script src="/webjars/bootstrap/5.3.0/js/bootstrap.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/axios/dist/axios.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <script src="/js/date-formatting.js"></script>
    <meta charset="UTF-8">
    <title>Grade Checker | Group Detail</title>
    <style>
        #loader {
            display: none;
            width: 50px;
            height: 50px;
            border: 5px solid #f3f3f3;
            border-radius: 50%;
            border-top: 5px solid #3498db;
            animation: spin 1s linear infinite;
            margin: 1.5rem auto;   /* respiro entre o spinner e o botão */
        }

        @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }

        /* contributor avatar: Bootstrap card-img-top, square and edge to edge (openspec/changes/cards-responsivos) */
        .avatar-card-img {
            aspect-ratio: 1 / 1;
            object-fit: cover;
        }

        .contributions-table {
            margin-top: 0.5rem;
        }

    </style>
</head>
<body>

<main class="container">
    <br>
    <nav style="--bs-breadcrumb-divider: '>';" aria-label="breadcrumb">
      <ol class="breadcrumb">
        <li class="breadcrumb-item"><a href="/home">Home</a></li>
        <li class="breadcrumb-item active" aria-current="page">Group <c:out value="${group.id}"/></li>
      </ol>
    </nav>
    <hr>

    <h4>Group information</h4>

    <div class="details row">

        <div class="col-12 col-lg-6">

            <div class="mb-2 row">
                <label for="groupName" class="col-sm-3 col-form-label">Group Name:</label>
                <div class="col-sm-9">
                    <input type="text" readonly class="form-control-plaintext" id="groupName" value="<c:out value='${group.name}'/>">
                </div>
            </div>
            <div class="mb-2 row">
                <span class="col-sm-3 col-form-label">URL:</span>
                <div class="col-sm-9">
                    <a href="<c:out value='${group.repositoryUrl}'/>" class="form-control-plaintext text-break" target="_blank" rel="noopener noreferrer"><c:out value="${group.repositoryUrl}"/></a>
                </div>
            </div>
            <div class="mb-2 row">
                <label for="owner" class="col-sm-3 col-form-label">Owner:</label>
                <div class="col-sm-9">
                    <input type="text" readonly class="form-control-plaintext" id="owner" value="<c:out value='${group.owner}'/>">
                </div>
            </div>
            <div class="mb-2 row">
                <label for="repository" class="col-sm-3 col-form-label">Repository name:</label>
                <div class="col-sm-9">
                    <input type="text" readonly class="form-control-plaintext" id="repository" value="<c:out value='${group.repository}'/>">
                </div>
            </div>

        </div>

        <div class="col-12 col-lg-6">

            <div class="mb-2 row">
                <label for="number-of-contributors" class="col-sm-4 col-form-label">Number of contributors:</label>
                <div class="col-sm-8">
                    <input type="text" readonly class="form-control-plaintext" id="number-of-contributors" value="<c:out value='${group.contributors.size()}'/>">
                </div>
            </div>

            <div class="mb-2 row">
                <label for="members" class="col-sm-4 col-form-label">Number of members:</label>
                <div class="col-sm-8">
                    <input type="text" readonly class="form-control-plaintext" id="members" value="<c:out value='${group.totalOfMembers}'/>">
                </div>
            </div>

            <div class="row">
                <a href="#" class="col-form-label" data-bs-toggle="modal" data-bs-target="#exampleModal">Add a new member</a>
            </div>

        </div>

    </div>

    <hr>

    <c:if test="${param.error == 'github_user_not_found'}">
        <div class="alert alert-danger" role="alert">
            O usuário informado não existe no GitHub; cadastro não realizado.
        </div>
    </c:if>
    <c:if test="${param.error == 'member_already_exists'}">
        <div class="alert alert-warning" role="alert">
            O membro já está cadastrado para este grupo; nenhum novo cadastro foi efetuado.
        </div>
    </c:if>

    <h4>Contributors</h4>

    <!-- 1 column < 576px, 2 >= 576px, 3 >= 768px, 4 >= 992px: narrower cards keep the square avatar small -->
    <div class="row row-cols-1 row-cols-sm-2 row-cols-md-3 row-cols-lg-4 g-4 mb-4">

        <c:forEach var="contributor" items="${group.contributors}">
            <div class="col">
                <div class="card h-100 rounded-3 shadow-sm">
                    <img src="<c:out value='${contributor.avatarUrl}'/>" class="card-img-top avatar-card-img"
                         alt="Avatar of <c:out value='${contributor.login}'/>">
                    <div class="card-body">
                        <h5 class="card-title text-break"><c:out value="${contributor.login}"/></h5>
                        <p class="card-text">Contributions: <c:out value="${contributor.contributions}"/></p>
                    </div>
                    <div class="card-footer bg-transparent border-0 pb-3">
                        <a href="<c:out value='${contributor.htmlUrl}'/>" target="_blank" rel="noopener noreferrer" class="card-link">GitHub</a>
                        <c:url var="contributionsUrl" value="/contributions-detail">
                            <c:param name="author" value="${contributor.login}"/>
                            <c:param name="repo" value="${group.repository}"/>
                            <c:param name="owner" value="${group.owner}"/>
                            <c:param name="groupId" value="${group.id}"/>
                        </c:url>
                        <a href="<c:out value='${contributionsUrl}'/>" class="card-link">Contributions details</a>
                    </div>
                </div>
            </div>
        </c:forEach>

    </div>

    <hr>

    <!-- ajax content init here -->

    <div id="content" class="text-center"
         data-owner="<c:out value='${group.owner}'/>"
         data-repo="<c:out value='${group.repository}'/>"
         data-group-id="<c:out value='${group.id}'/>">

        <!-- period buttons (openspec/changes/filtro-por-marcos): the page loads the dashboard with All on open -->
        <div class="btn-group flex-wrap mb-2" role="group" aria-label="Period" id="period-buttons">
            <c:forEach var="option" items="${periodOptions}">
                <button type="button"
                        class="btn btn-sm period-button ${option.selected ? 'btn-primary active' : 'btn-outline-primary'}"
                        data-period="<c:out value='${option.key}'/>"
                        title="<c:out value='${option.hint}'/>"
                        ${option.enabled ? '' : 'disabled'}><c:out value="${option.label}"/></button>
            </c:forEach>
        </div>

        <details id="custom-period" class="mb-3">
            <summary class="small">Custom period</summary>
            <div class="d-inline-flex flex-wrap gap-2 align-items-end mt-2">
                <div class="text-start">
                    <label for="since" class="form-label small mb-0">Since</label>
                    <input type="datetime-local" class="form-control form-control-sm" id="since">
                </div>
                <div class="text-start">
                    <label for="until" class="form-label small mb-0">Until</label>
                    <input type="datetime-local" class="form-control form-control-sm" id="until" value="<c:out value='${customUntil}'/>">
                </div>
                <button type="button" class="btn btn-sm btn-primary" id="applyCustomPeriod">Apply</button>
            </div>
        </details>

        <h5 id="dashboard-title" class="d-none"></h5>
        <p id="dashboard-summary" class="text-body-secondary small d-none"></p>
        <div id="dashboard-alert" class="alert d-none mb-4" role="alert"></div>
        <div id="grading-alert" class="alert alert-info d-none mb-4 small" role="status"></div>
    </div>

    <div class="mx-auto" id="loader"></div>

    <!-- tabela abaixo dos controles de período; oculta até a primeira carga (automática, ao abrir a página) -->
    <div class="row d-none" id="contributions-section">

        <div class="col-12">

          <div class="table-responsive">
          <table class="table table-hover contributions-table">
            <thead>
                <tr>
                    <th scope="col">Author</th>
                    <th scope="col">Commits</th>
                    <th scope="col">Additions</th>
                    <th scope="col">Deletions</th>
                    <th scope="col">Percentage</th>
                    <%-- grade columns (Grade / M1 M2 M3 Total) are added by the dashboard script per period --%>
                </tr>
            </thead>
            <tbody id="contributions-tbody">
                <c:forEach var="contributor" items="${group.contributors}">
                    <tr data-login="<c:out value='${contributor.login}'/>">
                        <td><c:out value="${contributor.login}"/></td>
                        <td>&mdash;</td>
                        <td>&mdash;</td>
                        <td>&mdash;</td>
                        <td>&mdash;</td>
                    </tr>
                </c:forEach>
            </tbody>
          </table>
          </div>
        </div>

    </div>


    <div class="row">

        <div class="col-3"></div>

        <div class="col-6">
            <canvas id="contributorPierChart"></canvas>
        </div>

        <div class="col-3"></div>

    </div>

    <!-- ajax content end here -->

</main>

<!-- init modal -->

<div class="modal fade" id="exampleModal" tabindex="-1" aria-labelledby="exampleModalLabel" aria-hidden="true">

    <form action="/member/create" method="post">

        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <h1 class="modal-title fs-5" id="exampleModalLabel">Add group member</h1>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                </div>
                <div class="modal-body">
                <!-- init modal body -->

                    <div class="row">

                        <input type="hidden" class="form-control" id="group-id" name="group-id" value="${param.id}">

                        <div class="mb-3">
                            <label for="user-name" class="form-label">Name</label>
                            <input type="text" class="form-control" id="user-name" name="user-name" required>
                        </div>

                        <div class="mb-3">
                            <label for="github-username" class="form-label">Github Username</label>
                            <input type="text" class="form-control" id="github-username" name="github-username" required>
                        </div>

                    </div>

                <!-- end modal body -->
                </div>
                <div class="modal-footer">
                    <button type="submit" class="btn btn-primary">Save</button>
                </div>
            </div>
        </div>
    </form>
</div>
<!-- end modal -->

<script>
        // Dashboard de contribuições (openspec/changes/corrigir-dashboard-contribuicoes)
        let contributorChart = null;
        let groupContributors = []; // logins renderizados pelo servidor, lidos uma vez ao carregar a página

        function showAlert(type, text) {
            $("#dashboard-alert")
                .removeClass("d-none alert-warning alert-danger alert-info")
                .addClass("alert-" + type)
                .text(text);
        }

        function cell(text) {
            return $("<td>").text(text);
        }

        // author name + "No GitHub account" badge for commits not linked to a login (openspec change identidades-autores)
        function authorCell(stat) {
            const td = $("<td>").append($("<span>").addClass("author-name").text(stat.author));
            const identities = (stat.gitIdentities || []).join(", ");
            if (stat.linked === false) {
                $("<span>").addClass("badge text-bg-warning fw-normal ms-2 no-account-badge")
                    .text("No GitHub account")
                    .attr("title", "Commits not linked to a GitHub account: " + identities)
                    .appendTo(td);
            } else if (identities) {
                // unlinked commits added to this login (same e-mail or git name equal to the login)
                $("<span>").addClass("badge text-bg-secondary fw-normal ms-2 includes-unlinked-badge")
                    .text("Includes unlinked commits")
                    .attr("title", "Also counted here (commits not linked to the account): " + identities)
                    .appendTo(td);
            }
            return td;
        }

        // ---------------- milestone grades (openspec capability milestone-grading) ----------------

        function formatGrade(value) {
            return value === null || value === undefined ? "—" : Number(value).toFixed(2);
        }

        // grade columns for the selected period: milestone → Grade (0–3); all → M1, M2, M3, Total (0–9); custom → none
        function gradeColumns(data) {
            const grading = data.grading;
            if (!grading) return [];
            if (data.period && data.period.startsWith("m")) {
                return [{ header: "Grade (0–3)" + (grading.provisional ? " (in progress)" : ""), value: function (stat) {
                    return { text: formatGrade(stat.grade) + (stat.grade !== null && stat.grade !== undefined && grading.provisional ? " (in progress)" : ""),
                             title: gradeTitle(stat.gradeDetails) };
                } }];
            }
            if (data.period === "all") {
                const provisional = grading.provisionalMilestones || [];
                const columns = ["m1", "m2", "m3"].map(function (key, i) {
                    return { header: "M" + (i + 1) + (provisional.indexOf(key) >= 0 ? " (in progress)" : ""), value: function (stat) {
                        return { text: formatGrade(stat.milestoneGrades ? stat.milestoneGrades[key] : null) };
                    } };
                });
                columns.push({ header: "Total (0–9)", value: function (stat) { return { text: formatGrade(stat.total) }; } });
                return columns;
            }
            return [];
        }

        function gradeTitle(d) {
            if (!d) return "Not a student of this group: no grade";
            return "Effective lines: " + d.effectiveLines + " · Active days: " + d.activeDays + " · Relevant commits: " + d.relevantCommits
                + " · Index share: " + (d.indexShare * 100).toFixed(1) + "% · Ratio to fair share: " + d.ratio;
        }

        function renderGradeHeaders(columns) {
            const headerRow = $(".contributions-table thead tr");
            headerRow.find("th.grade-col").remove();
            columns.forEach(function (c) { $("<th>").attr("scope", "col").addClass("grade-col").text(c.header).appendTo(headerRow); });
        }

        function showGradingNotice(data) {
            const notice = $("#grading-alert").addClass("d-none").text("");
            const g = data.grading;
            if (g && g.excludedCommits > 0) {
                notice.text(g.excludedCommits + " commits not counted in grades (authors not linked to group members).").removeClass("d-none");
            } else if (g && g.students === 0) {
                notice.text("No students registered in this group: grades are not available.").removeClass("d-none");
            }
        }

        function fillTable(stats, columns) {
            columns = columns || [];
            renderGradeHeaders(columns);
            const tbody = $("#contributions-tbody");

            const seen = new Set();
            tbody.empty();

            stats.forEach(function (stat) {
                seen.add(stat.author);
                $("<tr>").attr("data-login", stat.author)
                    .append(authorCell(stat))
                    .append(cell(stat.commits))
                    .append(cell(stat.totalAdditions))
                    .append(cell(stat.totalDeletions))
                    .append(cell(Number(stat.percentage).toFixed(2) + "%"))
                    .append(columns.map(function (c) {
                        const v = c.value(stat);
                        const td = $("<td>").addClass("grade-cell fw-semibold").text(v.text);
                        if (v.title) td.attr("title", v.title);
                        return td;
                    }))
                    .appendTo(tbody);
            });

            // contribuidores sem commits no período aparecem zerados
            groupContributors.filter(function (login) { return !seen.has(login); }).forEach(function (login) {
                $("<tr>").attr("data-login", login).addClass("text-body-secondary")
                    .append(cell(login)).append(cell(0)).append(cell(0)).append(cell(0)).append(cell("0.00%"))
                    .append(columns.map(function () { return $("<td>").addClass("grade-cell").text("—"); }))
                    .appendTo(tbody);
            });

            $("#contributions-section").removeClass("d-none");
        }

        function drawChart(stats) {
            if (contributorChart) {
                contributorChart.destroy();
                contributorChart = null;
            }

            const canvas = document.getElementById('contributorPierChart');
            const total = stats.reduce(function (sum, s) { return sum + s.totalAdditions + s.totalDeletions; }, 0);

            if (stats.length === 0 || total === 0) {
                $(canvas).addClass("d-none");
                showAlert("info", "No contributions found in this period.");
                return;
            }

            $(canvas).removeClass("d-none");

            contributorChart = new Chart(canvas.getContext('2d'), {
                type: 'doughnut',
                data: {
                    labels: stats.map(function (s) { return s.author; }),
                    datasets: [{
                        data: stats.map(function (s) { return s.totalAdditions + s.totalDeletions; }),
                        backgroundColor: ['#FF6384', '#36A2EB', '#FFCE56', '#4BC0C0', '#9966FF', '#FF9F40'],
                        hoverBackgroundColor: ['#FF6384', '#36A2EB', '#FFCE56', '#4BC0C0', '#9966FF', '#FF9F40']
                    }]
                },
                options: {
                    responsive: true,
                    plugins: {
                        legend: { position: 'bottom' },
                        tooltip: {
                            callbacks: {
                                label: function (item) {
                                    const s = stats[item.dataIndex];
                                    return (s.totalAdditions + s.totalDeletions) + ' lines changed (' + Number(s.percentage).toFixed(2) + '%)';
                                }
                            }
                        }
                    }
                }
            });
        }

        let selectedPeriod = "all";

        function selectPeriodButton(period) {
            $(".period-button").each(function () {
                const active = $(this).data("period") === period;
                $(this).toggleClass("btn-primary active", active).toggleClass("btn-outline-primary", !active);
            });
        }

        function loadData() {
            const content = $("#content");

            const params = {
                owner: content.data("owner"),
                repo: content.data("repo"),
                groupId: content.data("group-id"),
                period: selectedPeriod
            };

            if (selectedPeriod === "custom") {
                params["date-since"] = $("#since").val();
                params["date-until"] = $("#until").val();
            }

            $(".period-button, #applyCustomPeriod").prop("disabled", true);
            $("#dashboard-alert").addClass("d-none").text("");
            $("#loader").show();

            axios.get('/contributors-stats', { params: params })
            .then(function (response) {
                const data = response.data;
                const stats = (data.stats && data.stats.consolidatedStats) || [];

                $("#dashboard-title").text(data.message).removeClass("d-none");
                $("#dashboard-summary")
                    .text(data.totalCommits + " commits · " + data.periodText + " (merge commits excluded)")
                    .removeClass("d-none");

                fillTable(stats, gradeColumns(data));
                showGradingNotice(data);
                drawChart(stats);

                if (data.truncated) {
                    showAlert("warning", "Only the " + data.totalCommits + " most recent commits in this period were considered (configured limit).");
                }
            })
            .catch(function (error) {
                const body = error.response && error.response.data;
                const message = body && body.message ? body.message : "Could not load the dashboard. Please try again.";
                $("#contributions-section").addClass("d-none"); // não exibir dados de uma carga anterior junto do erro
                showAlert("danger", message);
                console.error("Error loading contributions dashboard:", error);
            })
            .finally(function () {
                $("#loader").hide();
                // re-enable only the periods the server allowed (disabled = future or not configured)
                $(".period-button").each(function () {
                    $(this).prop("disabled", $(this).data("server-disabled") === true);
                });
                $("#applyCustomPeriod").prop("disabled", false);
            });
        }

        $(document).ready(function () {
            groupContributors = $("#contributions-tbody tr[data-login]").map(function () {
                return $(this).attr("data-login");
            }).get();
            $(".period-button").each(function () {
                $(this).data("server-disabled", $(this).prop("disabled"));
            });

            // the dashboard loads as soon as the page opens (All); period buttons reload it
            loadData();

            $(".period-button").click(function () {
                selectedPeriod = $(this).data("period");
                selectPeriodButton(selectedPeriod);
                $("#custom-period").prop("open", false);
                loadData();
            });

            $("#applyCustomPeriod").click(function () {
                selectedPeriod = "custom";
                selectPeriodButton(null);
                loadData();
            });
        });

    </script>

</body>
</html>