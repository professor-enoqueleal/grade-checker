<!DOCTYPE html>
<html lang="en">
<head>
    <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
    <link href="/webjars/bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
    <meta charset="UTF-8">
    <title>Grade Checker</title>
    <style>
        /* same illustration as before, smaller and never cropped (openspec/changes/cards-responsivos) */
        .group-card-img {
            height: 160px;
            object-fit: contain;
            padding: 1rem 1rem 0;
        }
    </style>
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

    <!-- 1 column < 576px, 2 columns 576-991px, 3 columns >= 992px; cards fill their column -->
    <div class="row row-cols-1 row-cols-sm-2 row-cols-lg-3 g-4 mb-4">

      <c:forEach var="group" items="${groups}">

        <div class="col">
          <div class="card h-100 shadow-sm">
            <img class="card-img-top group-card-img" src="/img/group.png" alt="Group <c:out value='${group.id}'/>">
            <div class="card-body text-center">
              <h5 class="card-title">Group <c:out value="${group.id}"/></h5>
            </div>
            <ul class="list-group list-group-flush">
              <li class="list-group-item"><b>Name:</b> <c:out value="${group.name}"/></li>
            </ul>
            <div class="card-footer bg-transparent border-0 text-center py-3">
              <c:url var="detailUrl" value="/detail"><c:param name="id" value="${group.id}"/></c:url>
              <a href="<c:out value='${detailUrl}'/>" class="card-link">Details</a>
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
