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

    <div>

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

    <div class="row">

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
            <tbody>
                <c:forEach var="contributor" items="${group.contributors}">
                    <tr>
                        <td>${contributor.login}</td>
                        <td>${contributor.contributions}</td>
                        <td>#</td>
                        <td>#</td>
                        <td>#</td>
                    </tr>
                </c:forEach>
            </tbody>
          </table>
        </div>

    <div>

    <hr>

    <!-- ajax content init here -->

    <div class="text-center" id="loader"></div>

    <div id="content" class="text-center">
        <button id="loadDataButton" class="btn btn-primary">Load Dashboard</button>
    </div>

    <div class="row">

        <div class="col-3"></div>

        <div class="col-6">
            <canvas id="contributorPierChart"></canvas>
        </div>

        <div class="col-3"></div>

    <div>

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
        function loadData() {

            // show loader
            $("#loader").show();

            axios.get('contributors-stats', {
                params: {
                    owner: '${group.owner}',
                    repo: '${group.repository}'
                }
            })
            .then(function(response) {
                // hidden loader
                $("#loader").hide();

                // console.log('Message: ', response.data);

                // show servlet returned content in the page
                const returnedMessage = response.data.message;
                let contentHtml = '<div class="text-center"><p>' + returnedMessage + '</p></div>';
                $("#content").html(contentHtml);

                // build dashboard to show
                const stats = response.data.stats.consolidatedStats;
                const labels = stats.map(stat => stat.author);
                const data = stats.map(stat => stat.totalAdditions + stat.totalDeletions);

                // show dashboard
                const ctx = document.getElementById('contributorPierChart').getContext('2d');
                const chartData = {
                    labels: labels,
                    datasets: [{
                        data: data,
                        backgroundColor: ['#FF6384', '#36A2EB', '#FFCE56', '#4BC0C0', '#9966FF', '#FF9F40'],
                        hoverBackgroundColor: ['#FF6384', '#36A2EB', '#FFCE56', '#4BC0C0', '#9966FF', '#FF9F40']
                    }]
                };

                const contributorPierChart = new Chart(ctx, {
                    type: 'doughnut',
                    data: chartData,
                    options: {
                        responsive: true,
                        plugins: {
                            legend: {
                                position: 'bottom',
                            },
                            tooltip: {
                                callbacks: {
                                    label: function(tooltipItem) {
                                        const value = chartData.datasets[0].data[tooltipItem.dataIndex];
                                        const total = chartData.datasets[0].data.reduce((a, b) => a + b, 0);
                                        const percentage = ((value / total) * 100).toFixed(2);
                                        return value + ' (' + percentage + '%)';
                                    }
                                }
                            }
                        }
                    }
                });

            })
            .catch(function(error) {
                $("#loader").hide();
                $("#content").html("Error loading dynamic content!");
                console.error("Error loading content:", error);
            });
        }

        $(document).ready(function() {
            $("#loadDataButton").click(loadData);
        });

    </script>

</body>
</html>