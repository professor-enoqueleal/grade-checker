<!DOCTYPE html>
<html lang="en">
<head>
    <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
    <link href="/webjars/bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
    <script src="/webjars/jquery/3.7.1/jquery.min.js"></script>
    <script src="/webjars/bootstrap/5.3.0/js/bootstrap.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/axios/dist/axios.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
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

        .contributions-table {
            margin-top: 85px;
        }

    </style>
</head>
<body>

<main class="container">
    <br>
    <nav style="--bs-breadcrumb-divider: '>';" aria-label="breadcrumb">
      <ol class="breadcrumb">
        <li class="breadcrumb-item"><a href="/home">Home</a></li>
        <li class="breadcrumb-item active" aria-current="page">Group ${group.id}</li>
      </ol>
    </nav>
    <hr>

    <h4>Group information</h4>

    <div class="details row">

        <div class="col">

            <div class="mb-2 row">
                <label for="groupName" class="col-sm-3 col-form-label">Group Name:</label>
                <div class="col-sm-9">
                    <input type="text" readonly class="form-control-plaintext" id="groupName" value="${group.name}">
                </div>
            </div>
            <div class="mb-2 row">
                <label for="url" class="col-sm-3 col-form-label">URL:</label>
                <div class="col-sm-9">
                    <a href="${group.repositoryUrl}" class="form-control-plaintext" target="_blank">${group.repositoryUrl}</a>
                </div>
            </div>
            <div class="mb-2 row">
                <label for="owner" class="col-sm-3 col-form-label">Owner:</label>
                <div class="col-sm-9">
                    <input type="text" readonly class="form-control-plaintext" id="owner" value="${group.owner}">
                </div>
            </div>
            <div class="mb-2 row">
                <label for="repository" class="col-sm-3 col-form-label">Repository name:</label>
                <div class="col-sm-9">
                    <input type="text" readonly class="form-control-plaintext" id="repository" value="${group.repository}">
                </div>
            </div>

        </div>

        <div class="col">

            <div class="mb-2 row">
                <label for="repository" class="col-sm-4 col-form-label">Number of contributors:</label>
                <div class="col-sm-8">
                    <input type="text" readonly class="form-control-plaintext" id="repository" value="${group.contributors.size()}">
                </div>
            </div>

            <div class="mb-2 row">
                <label for="members" class="col-sm-4 col-form-label">Number of members:</label>
                <div class="col-sm-8">
                    <input type="text" readonly class="form-control-plaintext" id="members" value="${group.totalOfMembers}">
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

    <div class="row row-cols-1 row-cols-md-3 mb-3">

        <c:forEach var="contributor" items="${group.contributors}">
            <div class="col">
                <div class="card mb-4 rounded-3 shadow-sm" style="width: 18rem;">
                    <img src="${contributor.avatarUrl}" class="card-img-top" alt="...">
                    <div class="card-body">
                        <h5 class="card-title">${contributor.login}</h5>
                        <p class="card-text">Contributions: ${contributor.contributions}</p>
                    </div>
                      <div class="card-body">
                        <a href="${contributor.htmlUrl}" target="_blank" class="card-link">GitHub</a>
                        <a href="/contributions-detail?author=${contributor.login}&repo=${group.repository}&owner=${group.owner}&groupId=${group.id}" class="card-link">Contributions details</a>
                      </div>
                </div>
            </div>
        </c:forEach>

    </div>

    <!-- tabela oculta até o Load Dashboard trazer os dados -->
    <div class="row d-none" id="contributions-section">

        <div class="col-12">

          <table class="table table-hover contributions-table">
            <thead>
                <tr>
                    <th scope="col">Author</th>
                    <th scope="col">Commits</th>
                    <th scope="col">Additions</th>
                    <th scope="col">Deletions</th>
                    <th scope="col">Percentage</th>
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

    <hr>

    <!-- ajax content init here -->

    <div class="mx-auto" id="loader"></div>

    <div id="content" class="text-center">
        <h5 id="dashboard-title" class="d-none"></h5>
        <p id="dashboard-summary" class="text-body-secondary small d-none"></p>
        <div id="dashboard-alert" class="alert d-none" role="alert"></div>
        <button id="loadDataButton" class="btn btn-primary mb-4">Load Dashboard</button>
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

        // datas da API são UTC: formatar em UTC evita que 2024-01-01T00:01Z vire 31/12/2023 no fuso do Brasil
        function formatDate(iso) {
            const d = new Date(iso);
            return isNaN(d) ? iso : d.toLocaleDateString('en-US', { timeZone: 'UTC' });
        }

        function showAlert(type, text) {
            $("#dashboard-alert")
                .removeClass("d-none alert-warning alert-danger alert-info")
                .addClass("alert-" + type)
                .text(text);
        }

        function cell(text) {
            return $("<td>").text(text);
        }

        function fillTable(stats) {
            const tbody = $("#contributions-tbody");

            const seen = new Set();
            tbody.empty();

            stats.forEach(function (stat) {
                seen.add(stat.author);
                $("<tr>").attr("data-login", stat.author)
                    .append(cell(stat.author))
                    .append(cell(stat.commits))
                    .append(cell(stat.totalAdditions))
                    .append(cell(stat.totalDeletions))
                    .append(cell(Number(stat.percentage).toFixed(2) + "%"))
                    .appendTo(tbody);
            });

            // contribuidores sem commits no período aparecem zerados
            groupContributors.filter(function (login) { return !seen.has(login); }).forEach(function (login) {
                $("<tr>").attr("data-login", login).addClass("text-body-secondary")
                    .append(cell(login)).append(cell(0)).append(cell(0)).append(cell(0)).append(cell("0.00%"))
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

        function loadData() {
            const button = $("#loadDataButton");

            button.prop("disabled", true);
            $("#dashboard-alert").addClass("d-none").text("");
            $("#loader").show();

            axios.get('/contributors-stats', {
                params: {
                    owner: '${group.owner}',
                    repo: '${group.repository}'
                }
            })
            .then(function (response) {
                const data = response.data;
                const stats = (data.stats && data.stats.consolidatedStats) || [];

                $("#dashboard-title").text(data.message).removeClass("d-none");
                $("#dashboard-summary")
                    .text(data.totalCommits + " commits from " + formatDate(data.since) + " to " + formatDate(data.until) + " (merge commits excluded)")
                    .removeClass("d-none");

                fillTable(stats);
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
                button.prop("disabled", false).text("Reload Dashboard");
            });
        }

        $(document).ready(function () {
            groupContributors = $("#contributions-tbody tr[data-login]").map(function () {
                return $(this).attr("data-login");
            }).get();
            $("#loadDataButton").click(loadData);
        });

    </script>

</body>
</html>