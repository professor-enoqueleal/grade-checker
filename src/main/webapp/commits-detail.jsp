<!DOCTYPE html>
<html lang="en">
<head>
    <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
    <link href="/webjars/bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
    <meta charset="UTF-8">
    <title>Grade Checker | Group Detail</title>
</head>
<body>

<main class="container">
    <br>
    <nav style="--bs-breadcrumb-divider: '>';" aria-label="breadcrumb">
      <ol class="breadcrumb">
        <li class="breadcrumb-item"><a href="/home">Home</a></li>
        <li class="breadcrumb-item" aria-current="page"><a href="/detail?id=${groupId}">Group ${param.groupId}</a></li>
        <li class="breadcrumb-item active" aria-current="page">Commits Detail</li>
      </ol>
    </nav>
    <hr>

    <h4>Commits Details</h4>

    <form action="/contributions-detail" method="post">

    <div class="details">

        <div class="mb-3 row">
            <label for="author" class="col-sm-2 col-form-label">Contributor Name:</label>
            <div class="col-sm-10">
                <input type="hidden" id="owner" name="owner" value="${param.owner}">
                <input type="hidden" id="owner" name="repo" value="${param.repo}">
                <input type="hidden" id="owner" name="groupId" value="${param.groupId}">
                <input type="text" readonly class="form-control-plaintext" id="author" name="author" value="${author}">
            </div>
        </div>

        <div class="mb-3 row">
            <label for="total-of-commits" class="col-sm-2 col-form-label">Total of commits</label>
            <div class="col-sm-10">
                <input type="text" readonly class="form-control-plaintext" id="total-of-commits" value="${commits.size()}">
            </div>
        </div>

        <div class="mb-3 row">
            <label for="period" class="col-sm-2 col-form-label">Period:</label>
            <div class="col-sm-10">
                <input type="text" readonly class="form-control-plaintext" id="period">
            </div>
        </div>

        <div class="mb-3 row">
            <label for="since" class="col-sm-2 col-form-label">Since</label>
            <div class="col-sm-3">
                <input type="datetime-local" class="form-control" name="date-since" id="since" value="${since}">
            </div>
        </div>

        <div class="mb-3 row">
            <label for="until" class="col-sm-2 col-form-label">Until</label>
            <div class="col-sm-3">
                <input type="datetime-local" class="form-control" name="date-until" id="until" value="${until}">
            </div>
        </div>

        <br>

        <button type="submit" class="btn btn-primary btn-sm">Refresh March</button>

    <div>

    </form>

    <hr>

    <c:if test = "${commits.size() == 0}">
        <h4>No commits found for this period</h4>
    </c:if>

    <c:if test = "${commits.size() != 0}">
        <table class="table table-borderless">
            <thead>
                <tr>
                    <th scope="col">Message</th>
                    <th scope="col">URL</th>
                </tr>
            </thead>
            <tbody>
                <c:forEach var="commit" items="${commits}">
                    <tr>
                        <td>${commit.commit.message}</td>
                        <td><a href="${commit.htmlUrl}" target="_blank">${commit.htmlUrl}</a></td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </c:if>

</main>

    <script>

        var isoDateSince = "${since}";
        var isoDateUntil = "${until}";

        var dateSince = new Date(isoDateSince);
        var dateUntil = new Date(isoDateUntil);

        var offset = +3;
        var localDateSince = new Date(dateSince.getTime() - (offset * 60 * 60 * 1000));
        var localDateUntil = new Date(dateUntil.getTime() - (offset * 60 * 60 * 1000));

        var daySince = String(localDateSince.getUTCDate()).padStart(2, '0');
        var monthSince = String(localDateSince.getUTCMonth() + 1).padStart(2, '0');
        var yearSince = localDateSince.getUTCFullYear();
        var hoursSince = String(localDateSince.getUTCHours()).padStart(2, '0');
        var minutesSince = String(localDateSince.getUTCMinutes()).padStart(2, '0');

        var dayUntil = String(localDateUntil.getUTCDate()).padStart(2, '0');
        var monthUntil = String(localDateUntil.getUTCMonth() + 1).padStart(2, '0'); 
        var yearUntil = localDateUntil.getUTCFullYear();
        var hoursUntil = String(localDateUntil.getUTCHours()).padStart(2, '0');
        var minutesUntil = String(localDateUntil.getUTCMinutes()).padStart(2, '0');

        var formattedDateSince = daySince + '/' + monthSince + '/' + yearSince + ' ' + hoursSince + ':' + minutesSince;
        var formattedDateUntil = dayUntil + '/' + monthUntil + '/' + yearUntil + ' ' + hoursUntil + ':' + minutesUntil;

        document.getElementById("period").value = formattedDateSince + " to " + formattedDateUntil;

    </script>

</body>
</html>