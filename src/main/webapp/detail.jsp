<!DOCTYPE html>
<html lang="en">
<head>
    <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
    <link href="/webjars/bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
    <script src="/webjars/jquery/3.7.1/jquery.min.js"></script>
    <script src="/webjars/bootstrap/5.3.0/js/bootstrap.min.js"></script>
    <meta charset="UTF-8">
    <title>Grade Checker | Group Detail</title>
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

            <div class="mb-3 row">
                <label for="groupName" class="col-sm-2 col-form-label">Group Name:</label>
                <div class="col-sm-10">
                    <input type="text" readonly class="form-control-plaintext" id="groupName" value="${group.name}">
                </div>
            </div>
            <div class="mb-3 row">
                <label for="url" class="col-sm-2 col-form-label">URL:</label>
                <div class="col-sm-10">
                    <a href="${group.repositoryUrl}" class="form-control-plaintext" target="_blank">${group.repositoryUrl}</a>
                </div>
            </div>
            <div class="mb-3 row">
                <label for="owner" class="col-sm-2 col-form-label">Owner:</label>
                <div class="col-sm-10">
                    <input type="text" readonly class="form-control-plaintext" id="owner" value="${group.owner}">
                </div>
            </div>
            <div class="mb-3 row">
                <label for="repository" class="col-sm-2 col-form-label">Repository name:</label>
                <div class="col-sm-10">
                    <input type="text" readonly class="form-control-plaintext" id="repository" value="${group.repository}">
                </div>
            </div>

        </div>

        <div class="col">

            <div class="mb-3 row">
                <label for="repository" class="col-sm-2 col-form-label">Number of contributors:</label>
                <div class="col-sm-10">
                    <input type="text" readonly class="form-control-plaintext" id="repository" value="${contributors.size()}">
                </div>
            </div>

            <div class="mb-3 row">
                <label for="members" class="col-sm-2 col-form-label">Number of members:</label>
                <div class="col-sm-10">
                    <input type="text" readonly class="form-control-plaintext" id="members" value="${members}">
                </div>
            </div>

            <div class="row">
                <a href="#" class="col-form-label" data-bs-toggle="modal" data-bs-target="#exampleModal">Add a new member</a>
            </div>

        </div>
    <div>

    <hr>

    <h4>Contributors</h4>

    <div class="row row-cols-1 row-cols-md-3 mb-3">

        <c:forEach var="contributor" items="${contributors}">
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

</main>

<!-- init modal -->

<!-- Modal -->
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

                        <div class="col">

                            <div class="mb-6">
                                <label for="user-name" class="form-label">Name</label>
                                <input type="text" class="form-control" id="user-name" name="user-name" required>
                            </div>

                        </div>

                        <div class="col">
                            <div class="mb-6">
                                <label for="github-username" class="form-label">Github Username</label>
                                <input type="text" class="form-control" id="github-username" name="github-username" required>
                            </div>
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

</body>
</html>