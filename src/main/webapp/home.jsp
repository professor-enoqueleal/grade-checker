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

    <c:if test="${empty groups}">
        <br>
        <br>
        <br>
        <br>
        <p class="text-center">No groups to show :(</p>
        <br>
        <div class="text-center">
            <a href="/group/create">Create group!</a>
        </div>
    </c:if>

    <div class="row row-cols-1 row-cols-md-3 mb-3">
    
      <c:forEach var="group" items="${groups}">
    
        <div class="col">
          <div class="card" style="width: 20rem;">
            <img class="card-img-top" src="/img/group.png" alt="Card image cap">
            <div class="card-body text-center">
              <h5 class="card-title">Group ${group.id}</h5>
            </div>
            <ul class="list-group list-group-flush">
              <li class="list-group-item"><b>Name:</b> ${group.name}</li>
            </ul>
            <div class="card-body text-center">
              <a href="/detail?id=${group.id}" class="card-link">Details</a>
            </div>
          </div>
          <br>
        </div>
    
      </c:forEach>
    
    </div>
  </main>
<script src="/webjars/bootstrap/5.3.0/js/bootstrap.bundle.js"></script>
<script src="/js/navbar.js"></script>
</body>
</html>
