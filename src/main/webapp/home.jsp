<!DOCTYPE html>
<html lang="en">
<head>
    <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
    <link href="/webjars/bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
    <meta charset="UTF-8">
    <title>Grade Checker</title>
</head>
<body>

<main class="container">
    <br>
    <nav style="--bs-breadcrumb-divider: '>';" aria-label="breadcrumb">
      <ol class="breadcrumb">
        <li class="breadcrumb-item active"><a href="/home">Home</a></li>
      </ol>
    </nav>
    <hr>

    <h4>Groups</h4>

    <div class="row row-cols-1 row-cols-md-3 mb-3">

        <c:forEach var="group" items="${groups}">
            <div class="col">
                <div class="card mb-4 rounded-3 shadow-sm">
                    <div class="card-header py-3 text-center">
                        <h4 class="my-0 fw-normal">GRUPO ${group.id}</h4>
                    </div>
                    <div class="card-body">
                        <label>Group Name:</label>
                        <span>${group.name}</span>
                        <br>
                        <br>
                        <label>Repository URL:</label>
                        <span><a href="${group.repositoryUrl}" target="_blank">${group.name}</a></span>
                        <br>
                        <br>
                        <a class="w-100 btn btn-lg btn-outline-primary" href="/detail?id=${group.id}" role="button">Details</a>
                    </div>
                </div>
            </div>
        </c:forEach>
    </div>
  </main>
<script src="/webjars/bootstrap/5.3.0/js/bootstrap.bundle.js"></script>
<script src="/js/navbar.js"></script>
</body>
</html>
