<!DOCTYPE html>
<html lang="en">
<head>
    <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
    <link href="/webjars/bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
    <script src="/webjars/jquery/3.7.1/jquery.min.js"></script>
    <script src="/webjars/bootstrap/5.3.0/js/bootstrap.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/axios/dist/axios.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
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

        /* bar charts (openspec change graficos-e-explicacao-da-nota): the height follows the number of bars */
        .chart-box {
            position: relative;
            width: 100%;
            min-height: 160px;
        }

        /* "Como as notas são calculadas" (pt-BR, for the students) */
        #grading-explanation h6 {
            margin-top: 1rem;
        }
        #grading-explanation ul {
            margin-bottom: 0.5rem;
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
                    <input type="text" readonly class="form-control-plaintext" id="number-of-contributors" value="<c:out value='${empty group.contributorsError ? group.contributors.size() : "—"}'/>">
                </div>
            </div>

            <div class="mb-2 row">
                <label for="members" class="col-sm-4 col-form-label">Number of members:</label>
                <div class="col-sm-8">
                    <input type="text" readonly class="form-control-plaintext" id="members" value="<c:out value='${group.totalOfMembers}'/>">
                </div>
            </div>

            <c:if test="${isAdmin}">
                <div class="row">
                    <a href="/group/members?id=<c:out value='${group.id}'/>" class="col-form-label" id="manage-members-link">Manage members</a>
                </div>
            </c:if>

        </div>

    </div>

    <hr>

    <%-- contributors (GitHub) and members without commits (openspec capability group-members) --%>
    <h4>Contributors and members</h4>

    <c:if test="${not empty group.contributorsError}">
        <div class="alert alert-warning" role="alert" id="contributors-error"><c:out value="${group.contributorsError}"/></div>
    </c:if>

    <div id="members-alert" class="alert alert-danger d-none small" role="alert"></div>

    <c:if test="${empty cards}">
        <p class="text-body-secondary" id="no-cards">No contributors or members yet.</p>
    </c:if>

    <!-- 1 column < 576px, 2 >= 576px, 3 >= 768px, 4 >= 992px: narrower cards keep the square avatar small -->
    <div class="row row-cols-1 row-cols-sm-2 row-cols-md-3 row-cols-lg-4 g-4 mb-4">

        <c:forEach var="card" items="${cards}">
            <div class="col">
                <div class="card h-100 rounded-3 shadow-sm" data-login="<c:out value='${card.login}'/>">
                    <img src="<c:out value='${card.avatarUrl}'/>" class="card-img-top avatar-card-img"
                         alt="Avatar of <c:out value='${card.login}'/>">
                    <div class="card-body">
                        <h5 class="card-title text-break"><c:out value="${card.login}"/></h5>
                        <p class="card-text contributions-count">Contributions: <c:out value="${empty card.contributions ? '—' : card.contributions}"/></p>
                        <c:if test="${card.member}"><span class="badge text-bg-success member-badge">Member</span></c:if>
                    </div>
                    <div class="card-footer bg-transparent border-0 pb-3 small">
                        <a href="<c:out value='${card.htmlUrl}'/>" target="_blank" rel="noopener noreferrer" class="card-link">GitHub</a>
                        <c:url var="contributionsUrl" value="/contributions-detail">
                            <c:param name="author" value="${card.login}"/>
                            <c:param name="repo" value="${group.repository}"/>
                            <c:param name="owner" value="${group.owner}"/>
                            <c:param name="groupId" value="${group.id}"/>
                        </c:url>
                        <a href="<c:out value='${contributionsUrl}'/>" class="card-link">Details</a>
                        <c:if test="${isAdmin}">
                            <c:choose>
                                <c:when test="${card.member}"><a href="#" class="card-link unmark-member-link" data-login="<c:out value='${card.login}'/>" title="Unmark as member" aria-label="Unmark <c:out value='${card.login}'/> as member">Unmark</a></c:when>
                                <c:otherwise><a href="#" class="card-link mark-member-link" data-login="<c:out value='${card.login}'/>" title="Mark as member" aria-label="Mark <c:out value='${card.login}'/> as member">Mark</a></c:otherwise>
                            </c:choose>
                        </c:if>
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
         data-group-id="<c:out value='${group.id}'/>"
         data-admin="${isAdmin ? 'true' : 'false'}">

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
                <%-- members without commits in the period appear with zeros (openspec capability group-members) --%>
                <c:forEach var="member" items="${group.members}">
                    <tr data-login="<c:out value='${member.gitHubUserName}'/>">
                        <td><c:out value="${member.gitHubUserName}"/></td>
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


    <%-- bar charts, filled by the dashboard script per period (openspec change graficos-e-explicacao-da-nota):
         grades (All and milestones, students only) above, lines changed (every period, every author) below --%>
    <div class="row">
        <div class="col-12 mb-4 d-none" id="grades-chart-section">
            <h6 id="grades-chart-title"></h6>
            <div class="chart-box" id="grades-chart-box">
                <canvas id="grades-chart" role="img"></canvas>
            </div>
        </div>
        <div class="col-12 mb-4 d-none" id="lines-chart-section">
            <h6 id="lines-chart-title">Lines changed (additions + deletions)</h6>
            <div class="chart-box" id="lines-chart-box">
                <canvas id="lines-chart" role="img" aria-label="Lines changed (additions + deletions)"></canvas>
            </div>
        </div>
    </div>

    <!-- ajax content end here -->

    <%-- how grades are calculated, for every user. Written in Portuguese for the students (exception to the English
         interface); every number comes from grading.properties / milestones.properties (openspec capability milestone-grading) --%>
    <c:set var="gx" value="${gradingExplanation}"/>
    <section class="card mb-5" id="grading-explanation" lang="pt-BR" aria-labelledby="grading-explanation-title">
        <div class="card-body">
            <h5 class="card-title" id="grading-explanation-title">Como as notas são calculadas</h5>
            <p class="mb-0">Esta explicação vale para todos os grupos. Os números abaixo são os que estão em uso no sistema.</p>

            <h6>Marcos</h6>
            <ul id="grading-explanation-milestones">
                <c:forEach var="m" items="${gx.milestones}">
                    <li><c:out value="${m.label}"/>: de <c:out value="${m.start}"/> a <c:out value="${m.end}"/></li>
                </c:forEach>
            </ul>
            <p>Cada marco vale de 0 a <c:out value="${gx.maxPoints}"/> pontos, e o total vai de 0 a <c:out value="${gx.totalPoints}"/>
                (coluna &ldquo;Total&rdquo; no período &ldquo;All&rdquo;). Enquanto um marco não termina, a nota dele é provisória
                e aparece com &ldquo;(in progress)&rdquo;: ela ainda pode mudar.</p>

            <h6>Quem recebe nota e quais commits contam</h6>
            <ul>
                <li>Recebem nota os membros do grupo, cadastrados pelo professor.</li>
                <li>Um commit conta para você quando o e-mail configurado no git é um e-mail da sua conta do GitHub. Commits feitos
                    com outro e-mail aparecem na tabela como &ldquo;No GitHub account&rdquo; e não contam para ninguém, a menos que
                    o professor associe essa identidade a você.</li>
                <li>Commits de merge não contam. Contas de bots<c:if test="${gx.excludesLogins}"> e a conta do professor</c:if> não recebem nota.</li>
            </ul>

            <h6>Os três fatores</h6>
            <ul id="grading-explanation-factors">
                <li><strong>Linhas efetivas (peso <c:out value="${gx.weightEffectiveLines}"/>):</strong> adições + remoções de cada
                    commit, contando no máximo <c:out value="${gx.lineCap}"/> linhas por commit. Commits que apenas movem ou renomeiam
                    muitos arquivos (<c:out value="${gx.reorganizationMinFiles}"/> arquivos ou mais, com adições e remoções
                    equilibradas, a menor sendo pelo menos <c:out value="${gx.reorganizationBalance}"/> da maior) contam só
                    <c:out value="${gx.reorganizationFactor}"/> das linhas.</li>
                <li><strong>Dias ativos (peso <c:out value="${gx.weightActiveDays}"/>):</strong> quantidade de dias diferentes em que
                    você fez commits no marco (<c:out value="${gx.timeZone}"/>).</li>
                <li><strong>Commits relevantes (peso <c:out value="${gx.weightRelevantCommits}"/>):</strong> commits com pelo menos
                    <c:out value="${gx.relevantCommitMinLines}"/> linhas alteradas.</li>
            </ul>

            <h6>Da participação à nota</h6>
            <ul>
                <li>Em cada fator, a sua participação é a sua parte no total dos alunos do grupo. A soma dessas participações,
                    com os pesos acima, é a sua participação no índice.</li>
                <li>A parte justa é 1 dividido pelo número de alunos do grupo. Quem atinge pelo menos
                    <c:out value="${gx.tolerance}"/> da parte justa recebe a nota máxima do marco (<c:out value="${gx.maxPoints}"/>).
                    Abaixo disso, a nota é proporcional.</li>
                <li id="grading-explanation-example">Exemplo: num grupo de <c:out value="${gx.exampleStudents}"/> alunos, a parte justa é
                    <c:out value="${gx.exampleFairShare}"/>. Quem tem <c:out value="${gx.exampleFullShare}"/> ou mais do índice recebe
                    <c:out value="${gx.exampleFullGrade}"/>; quem tem <c:out value="${gx.exampleLowShare}"/> recebe
                    <c:out value="${gx.exampleLowGrade}"/>.</li>
                <li>Na tabela, passe o mouse sobre a sua nota para ver os seus números: linhas efetivas, dias ativos, commits
                    relevantes e participação.</li>
            </ul>

            <h6>Dicas</h6>
            <ul class="mb-0">
                <li>Faça commits com frequência, em dias diferentes, e não tudo de uma vez no fim do marco.</li>
                <li>Prefira commits pequenos e com sentido: um commit gigante conta no máximo <c:out value="${gx.lineCap}"/> linhas.</li>
                <li>Configure o git com o e-mail da sua conta do GitHub:
                    <code>git config --global user.email "seu-email@exemplo.com"</code></li>
            </ul>
        </div>
    </section>

</main>


<%-- manual association of an identity without account (openspec capability identity-aliases): administrators only --%>
<c:if test="${isAdmin}">
<div class="modal fade" id="linkIdentityModal" tabindex="-1" aria-labelledby="linkIdentityModalLabel" aria-hidden="true">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h1 class="modal-title fs-5" id="linkIdentityModalLabel">Link to student</h1>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                <p class="small">Commits of <strong id="link-identity-label"></strong> will be counted for the selected student in every period, including grades.</p>
                <label for="link-identity-login" class="form-label">Student</label>
                <select class="form-select" id="link-identity-login">
                    <c:forEach var="login" items="${aliasTargets}">
                        <option value="<c:out value='${login}'/>"><c:out value="${login}"/></option>
                    </c:forEach>
                </select>
                <div id="link-identity-error" class="alert alert-danger d-none mt-3 mb-0 small" role="alert"></div>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                <button type="button" class="btn btn-primary" id="confirm-link-identity">Link</button>
            </div>
        </div>
    </div>
</div>
</c:if>

<script>
        // Dashboard de contribuições (openspec/changes/corrigir-dashboard-contribuicoes)
        let gradesChart = null;
        let linesChart = null;
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

        let isAdmin = false; // GRADE_CHECKER_ADMINS: may link identities (openspec capability identity-aliases)

        // author name + "No GitHub account" badge for commits not linked to a login (openspec change identidades-autores)
        function authorCell(stat) {
            const td = $("<td>").append($("<span>").addClass("author-name").text(stat.author));
            const manual = stat.manualIdentities || [];
            const manualLabels = new Set(manual.map(function (m) { return m.identity; }));
            const identities = (stat.gitIdentities || []).map(function (i) {
                return manualLabels.has(i) ? i + " (manual)" : i;
            }).join(", ");
            if (stat.linked === false) {
                $("<span>").addClass("badge text-bg-warning fw-normal ms-2 no-account-badge")
                    .text("No GitHub account")
                    .attr("title", "Commits not linked to a GitHub account: " + identities)
                    .appendTo(td);
                if (isAdmin && stat.identityKey) {
                    $("<button>").attr("type", "button")
                        .addClass("btn btn-sm btn-outline-primary py-0 ms-2 link-identity-button")
                        .text("Link to student")
                        .attr("data-identity-key", stat.identityKey)
                        .attr("data-identity-label", stat.author)
                        .appendTo(td);
                }
            } else if (identities) {
                // unlinked commits added to this login (same e-mail, git name equal to the login, or manual link)
                $("<span>").addClass("badge text-bg-secondary fw-normal ms-2 includes-unlinked-badge")
                    .text("Includes unlinked commits")
                    .attr("title", "Also counted here (commits not linked to the account): " + identities)
                    .appendTo(td);
            }
            manual.forEach(function (m) {
                const line = $(document.createElement("div")).addClass("small text-body-secondary manual-identity")
                    .text("Linked manually: " + m.identity);
                if (isAdmin) {
                    $("<button>").attr("type", "button")
                        .addClass("btn btn-link btn-sm p-0 ms-2 align-baseline unlink-identity-button")
                        .text("Unlink")
                        .attr("data-identity-key", m.key)
                        .attr("data-identity-label", m.identity)
                        .appendTo(line);
                }
                line.appendTo(td);
            });
            return td;
        }

        // ---------------- manual association (openspec capability identity-aliases) ----------------

        const ajaxHeaders = { headers: { "X-Requested-With": "XMLHttpRequest" } };
        let identityToLink = null;

        function errorMessage(error, fallback) {
            const body = error.response && error.response.data;
            return body && body.message ? body.message : fallback;
        }

        function openLinkModal(button) {
            identityToLink = button.attr("data-identity-key");
            $("#link-identity-label").text(button.attr("data-identity-label"));
            $("#link-identity-error").addClass("d-none").text("");
            const hasStudents = $("#link-identity-login option").length > 0;
            $("#confirm-link-identity").prop("disabled", !hasStudents);
            if (!hasStudents) {
                $("#link-identity-error").text("No students registered in this group.").removeClass("d-none");
            }
            bootstrap.Modal.getOrCreateInstance(document.getElementById("linkIdentityModal")).show();
        }

        function confirmLink() {
            const button = $("#confirm-link-identity").prop("disabled", true);
            const form = new URLSearchParams({
                groupId: $("#content").data("group-id"),
                identityKey: identityToLink,
                login: $("#link-identity-login").val()
            });
            axios.post("/identity-aliases", form, ajaxHeaders)
                .then(function () {
                    bootstrap.Modal.getOrCreateInstance(document.getElementById("linkIdentityModal")).hide();
                    loadData();
                })
                .catch(function (error) {
                    $("#link-identity-error").text(errorMessage(error, "Could not link the identity. Please try again.")).removeClass("d-none");
                })
                .finally(function () { button.prop("disabled", false); });
        }

        function unlink(button) {
            if (!window.confirm("Unlink " + button.attr("data-identity-label") + "? Its commits will no longer be counted for this student.")) return;
            axios.delete("/identity-aliases", {
                    params: { groupId: $("#content").data("group-id"), identityKey: button.attr("data-identity-key") },
                    headers: ajaxHeaders.headers
                })
                .then(function () { loadData(); })
                .catch(function (error) { showAlert("danger", errorMessage(error, "Could not unlink the identity. Please try again.")); });
        }

        // ---------------- members (openspec capability group-members): administrators only ----------------

        function groupId() {
            return $("#content").data("group-id");
        }

        function showMembersError(text) {
            $("#members-alert").text(text).removeClass("d-none");
            document.getElementById("members-alert").scrollIntoView({ block: "center" });
        }

        // "Mark" (as member) on a contributor card: the login becomes a member (name = login)
        function markMember(link) {
            const form = new URLSearchParams({ groupId: groupId(), login: link.attr("data-login"), manual: "false" });
            axios.post("/group-members", form, ajaxHeaders)
                .then(function () { window.location.reload(); })
                .catch(function (error) { showMembersError(errorMessage(error, "Could not add the member. Please try again.")); });
        }

        // "Unmark" (as member) on a card (the full management is in /group/members)
        function removeMember(link) {
            const login = link.attr("data-login");
            if (!window.confirm("Remove " + login + " from the members of this group? Their commits will no longer receive a grade.")) return;
            axios.delete("/group-members", { params: { groupId: groupId(), login: login }, headers: ajaxHeaders.headers })
                .then(function () { window.location.reload(); })
                .catch(function (error) { showMembersError(errorMessage(error, "Could not remove the member. Please try again.")); });
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
                notice.text(g.excludedCommits + " commits not counted in grades (authors not linked to group members)."
                    + (isAdmin ? " Use \"Link to student\" to count them for a student." : "")).removeClass("d-none");
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

        // ---------------- bar charts (openspec change graficos-e-explicacao-da-nota) ----------------

        // one color per series, never reused across the two charts: milestones are an ordered blue ramp
        // (M1 light -> M3 dark), lines changed are orange / violet (palette validated for color vision deficiency)
        const CHART_COLORS = {
            m1: "#86b6ef", m2: "#3987e5", m3: "#1c5cab",
            additions: "#eb6834", deletions: "#6250d6"
        };
        const CHART_SURFACE = "#ffffff";   // 2px gap between stacked segments

        function destroyCharts() {
            if (gradesChart) { gradesChart.destroy(); gradesChart = null; }
            if (linesChart) { linesChart.destroy(); linesChart = null; }
            $("#grades-chart-section, #lines-chart-section").addClass("d-none");
        }

        // 40px per bar plus room for the axis and the legend, at least 160px
        function chartHeight(bars, legend) {
            return Math.max(160, bars * 40 + (legend ? 80 : 50));
        }

        // round only the end of the last (non-empty) segment of each bar
        function barEndRadius(datasetCount) {
            return function (ctx) {
                const chart = ctx.chart;
                for (let d = datasetCount - 1; d > ctx.datasetIndex; d--) {
                    const v = chart.data.datasets[d].data[ctx.dataIndex];
                    if (v !== null && v !== undefined && v > 0 && chart.isDatasetVisible(d)) return 0;
                }
                return { topRight: 4, bottomRight: 4, topLeft: 0, bottomLeft: 0 };
            };
        }

        function barDataset(label, values, color, datasetCount) {
            return {
                label: label,
                data: values,
                backgroundColor: color,
                hoverBackgroundColor: color,
                borderColor: CHART_SURFACE,
                borderWidth: { top: 0, bottom: 0, left: 0, right: 2 },
                borderSkipped: false,
                borderRadius: barEndRadius(datasetCount),
                maxBarThickness: 18
            };
        }

        function barChart(canvasId, labels, datasets, max, tooltip) {
            const legend = datasets.length > 1;
            $("#" + canvasId + "-box").css("height", chartHeight(labels.length, legend) + "px");
            return new Chart(document.getElementById(canvasId).getContext("2d"), {
                type: "bar",
                data: { labels: labels, datasets: datasets },
                options: {
                    indexAxis: "y",
                    responsive: true,
                    maintainAspectRatio: false,
                    animation: false,
                    scales: {
                        x: { stacked: true, min: 0, max: max, grid: { color: "#e9ecef" }, border: { display: false },
                             ticks: { color: "#6c757d", precision: 0 } },
                        y: { stacked: true, grid: { display: false }, border: { display: false },
                             ticks: { color: "#212529", callback: function (value) { return shortLabel(this.getLabelForValue(value), this.chart.width); } } }
                    },
                    plugins: {
                        legend: { display: legend, position: "bottom", labels: { boxWidth: 12, boxHeight: 12 } },
                        tooltip: tooltip
                    }
                }
            });
        }

        // long author names (e.g. "PAULO HENRIQUE DE MORAES DORNELAS (no account)") are shortened with "…" on narrow
        // charts, so the bars keep their room; the tooltip shows the full name
        function shortLabel(label, chartWidth) {
            const max = chartWidth < 576 ? 16 : 36;
            return label.length > max ? label.substring(0, max - 1) + "…" : label;
        }

        function formatPoints(value) {
            return Number(value).toFixed(2);
        }

        // grades: All -> M1 + M2 + M3 stacked (0 to 3 x maxPoints); milestone -> its grade (0 to maxPoints); students only
        function drawGradesChart(stats, data) {
            const g = data.grading;
            const period = data.period || "";
            if (!g || !(period === "all" || period.startsWith("m"))) return;
            const max = g.maxPoints || 3;

            let rows, datasets, title;
            if (period === "all") {
                rows = stats.filter(function (s) { return s.total !== null && s.total !== undefined; });
                const provisional = g.provisionalMilestones || [];
                const keys = ["m1", "m2", "m3"];
                datasets = keys.map(function (key, i) {
                    return barDataset("M" + (i + 1) + (provisional.indexOf(key) >= 0 ? " (in progress)" : ""),
                        rows.map(function (s) { return s.milestoneGrades ? s.milestoneGrades[key] : null; }),
                        CHART_COLORS[key], keys.length);
                });
                title = "Grades by milestone (0–" + formatMax(max * keys.length) + ")";
            } else {
                rows = stats.filter(function (s) { return s.grade !== null && s.grade !== undefined; });
                datasets = [barDataset("Grade", rows.map(function (s) { return s.grade; }), CHART_COLORS[period] || CHART_COLORS.m2, 1)];
                title = "Milestone " + period.substring(1) + " grade (0–" + formatMax(max) + ")" + (g.provisional ? " (in progress)" : "");
            }
            if (rows.length === 0) return;   // nobody has a grade: no members, or no milestone started yet

            $("#grades-chart-title").text(title);
            $("#grades-chart").attr("aria-label", title);
            $("#grades-chart-section").removeClass("d-none");
            gradesChart = barChart("grades-chart", rows.map(function (s) { return s.author; }),
                datasets, period === "all" ? max * 3 : max, {
                    callbacks: {
                        label: function (item) { return item.dataset.label + ": " + formatPoints(item.raw); }
                    }
                });
        }

        function formatMax(value) {
            return Number.isInteger(value) ? String(value) : Number(value).toFixed(1);
        }

        // lines changed: additions + deletions stacked, every author of the response, in every period
        function drawLinesChart(stats) {
            const total = stats.reduce(function (sum, s) { return sum + s.totalAdditions + s.totalDeletions; }, 0);
            if (stats.length === 0 || total === 0) {
                showAlert("info", "No contributions found in this period.");
                return;
            }
            const labels = stats.map(function (s) { return s.author + (s.linked === false ? " (no account)" : ""); });
            $("#lines-chart-section").removeClass("d-none");
            linesChart = barChart("lines-chart", labels, [
                barDataset("Additions", stats.map(function (s) { return s.totalAdditions; }), CHART_COLORS.additions, 2),
                barDataset("Deletions", stats.map(function (s) { return s.totalDeletions; }), CHART_COLORS.deletions, 2)
            ], undefined, {
                callbacks: {
                    label: function (item) { return item.dataset.label + ": " + item.raw; },
                    footer: function (items) {
                        const s = stats[items[0].dataIndex];
                        return (s.totalAdditions + s.totalDeletions) + " lines (" + Number(s.percentage).toFixed(2) + "%)";
                    }
                }
            });
        }

        function drawCharts(stats, data) {
            destroyCharts();
            drawGradesChart(stats, data);
            drawLinesChart(stats);
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
                drawCharts(stats, data);

                if (data.truncated) {
                    showAlert("warning", "Only the " + data.totalCommits + " most recent commits in this period were considered (configured limit).");
                }
            })
            .catch(function (error) {
                const body = error.response && error.response.data;
                const message = body && body.message ? body.message : "Could not load the dashboard. Please try again.";
                $("#contributions-section").addClass("d-none"); // não exibir dados de uma carga anterior junto do erro
                destroyCharts();
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
            isAdmin = $("#content").data("admin") === true;
            $("#contributions-tbody")
                .on("click", ".link-identity-button", function () { openLinkModal($(this)); })
                .on("click", ".unlink-identity-button", function () { unlink($(this)); });
            $("#confirm-link-identity").on("click", confirmLink);
            $(document)
                .on("click", ".mark-member-link", function (e) { e.preventDefault(); markMember($(this)); })
                .on("click", ".unmark-member-link", function (e) { e.preventDefault(); removeMember($(this)); });

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