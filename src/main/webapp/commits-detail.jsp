<!DOCTYPE html>
<html lang="en">
<head>
    <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
    <link href="/webjars/bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
    <meta charset="UTF-8">
    <title>Grade Checker | Commits Detail</title>
    <style>
        .period-filter .btn.disabled {
            pointer-events: auto; /* keep the tooltip (title) on disabled milestones */
            cursor: not-allowed;
        }
        .commit-message {
            white-space: pre-line;
        }
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

                <div class="form-text" id="period-text"><c:out value="${periodText}"/></div>

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
                    Only the <c:out value="${totalCommits}"/> most recent commits in this period are listed (configured limit).
                </div>
            </c:if>

            <table class="table table-striped table-hover">
                <thead>
                    <tr>
                        <th scope="col">Date</th>
                        <th scope="col">Message</th>
                        <th scope="col">Commit</th>
                    </tr>
                </thead>
                <tbody>
                    <c:forEach var="commit" items="${commits}">
                        <tr>
                            <td class="text-nowrap"><c:out value="${commit.date}"/></td>
                            <td class="commit-message"><c:out value="${commit.message}"/></td>
                            <td>
                                <c:choose>
                                    <c:when test="${not empty commit.url}">
                                        <a href="<c:out value='${commit.url}'/>" target="_blank" rel="noopener noreferrer"><code><c:out value="${commit.sha}"/></code></a>
                                    </c:when>
                                    <c:otherwise><code><c:out value="${commit.sha}"/></code></c:otherwise>
                                </c:choose>
                            </td>
                        </tr>
                    </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>

</main>

<script src="/js/date-formatting.js"></script>

</body>
</html>
