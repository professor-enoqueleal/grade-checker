<!DOCTYPE html>
<html lang="en">
<head>
    <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
    <%@ taglib prefix="gc" tagdir="/WEB-INF/tags" %>
    <link href="/webjars/bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
    <script src="/webjars/jquery/3.7.1/jquery.min.js"></script>
    <script src="/webjars/bootstrap/5.3.0/js/bootstrap.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/axios/dist/axios.min.js"></script>
    <meta charset="UTF-8">
    <title>Grade Checker | Manage members</title>
    <style>
        .member-actions a + a { margin-left: 1rem; }
    </style>
</head>
<body>

<gc:navbar/>

<main class="container">
    <br>
    <nav style="--bs-breadcrumb-divider: '>';" aria-label="breadcrumb">
      <ol class="breadcrumb">
        <li class="breadcrumb-item"><a href="/home">Home</a></li>
        <c:if test="${not empty groupId}">
            <li class="breadcrumb-item"><a href="/detail?id=<c:out value='${groupId}'/>" id="breadcrumb-group">Group <c:out value="${groupId}"/></a></li>
        </c:if>
        <li class="breadcrumb-item active" aria-current="page">Manage members</li>
      </ol>
    </nav>
    <hr>

    <%-- openspec capability group-members: administrators only --%>
    <c:choose>
    <c:when test="${not empty pageError}">
        <div class="alert alert-danger" role="alert" id="members-page-error"><c:out value="${pageError}"/></div>
    </c:when>
    <c:otherwise>

    <div id="content" data-group-id="<c:out value='${groupId}'/>">

        <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
            <h4 class="mb-0">Manage members <small class="text-body-secondary fs-6" id="group-name"><c:out value="${group.name}"/></small></h4>
            <a href="#" id="add-member-link">Add a new member</a>
        </div>

        <div id="members-alert" class="alert alert-danger d-none" role="alert"></div>

        <c:choose>
        <c:when test="${empty members}">
            <p class="text-body-secondary" id="no-members">No members yet.</p>
        </c:when>
        <c:otherwise>
        <div class="table-responsive">
            <table class="table table-sm table-hover align-middle" id="members-table">
                <thead>
                <tr>
                    <th scope="col">Name</th>
                    <th scope="col">GitHub login</th>
                    <th scope="col">Actions</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="member" items="${members}">
                    <tr data-login="<c:out value='${member.login}'/>">
                        <td class="member-name"><c:out value="${member.name}"/></td>
                        <td class="member-login">
                            <a href="https://github.com/<c:out value='${member.login}'/>" target="_blank" rel="noopener"><c:out value="${member.login}"/></a>
                        </td>
                        <td class="member-actions">
                            <a href="#" class="edit-member-link" data-login="<c:out value='${member.login}'/>" data-name="<c:out value='${member.name}'/>">Edit</a>
                            <a href="#" class="remove-member-link" data-login="<c:out value='${member.login}'/>">Remove</a>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
        </c:otherwise>
        </c:choose>

    </div>

    </c:otherwise>
    </c:choose>

</main>

<c:if test="${empty pageError}">
<%-- add / edit member form --%>
<div class="modal fade" id="memberModal" tabindex="-1" aria-labelledby="memberModalLabel" aria-hidden="true">

    <form id="member-form" novalidate>

        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <h1 class="modal-title fs-5" id="memberModalLabel">Add group member</h1>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                </div>
                <div class="modal-body">

                    <input type="hidden" id="current-login" value="">

                    <div class="mb-3">
                        <label for="user-name" class="form-label">Name</label>
                        <input type="text" class="form-control" id="user-name" name="user-name" maxlength="255" required>
                    </div>

                    <div class="mb-3">
                        <label for="github-username" class="form-label">GitHub username</label>
                        <input type="text" class="form-control" id="github-username" name="github-username" maxlength="39" required>
                    </div>

                    <div id="member-form-error" class="alert alert-danger d-none mb-0 small" role="alert"></div>

                </div>
                <div class="modal-footer">
                    <button type="submit" class="btn btn-primary" id="save-member">Save</button>
                </div>
            </div>
        </div>
    </form>
</div>

<script>
    (function () {
        const ajaxHeaders = { headers: { "X-Requested-With": "XMLHttpRequest" } };
        let modal = null;

        function groupId() {
            return $("#content").data("group-id");
        }

        function errorMessage(error, fallback) {
            const body = error.response && error.response.data;
            return body && body.message ? body.message : fallback;
        }

        // the same form adds (no current login) and edits (current login in the hidden field)
        function openForm(login, name) {
            const editing = !!login;
            $("#memberModalLabel").text(editing ? "Edit member" : "Add group member");
            $("#current-login").val(login || "");
            $("#user-name").val(name || "");
            $("#github-username").val(login || "");
            $("#member-form-error").addClass("d-none").text("");
            $("#save-member").prop("disabled", false);
            modal.show();
        }

        function save(event) {
            event.preventDefault();
            const current = $("#current-login").val();
            const login = $("#github-username").val().trim();
            const name = $("#user-name").val().trim();
            const errorBox = $("#member-form-error").addClass("d-none").text("");
            if (!name || !login) {
                errorBox.text("Please fill in the name and the GitHub username.").removeClass("d-none");
                return;
            }
            const button = $("#save-member").prop("disabled", true);
            const request = current
                ? axios.put("/group-members", null, {
                    params: { groupId: groupId(), login: current, newLogin: login, name: name },
                    headers: ajaxHeaders.headers })
                : axios.post("/group-members",
                    new URLSearchParams({ groupId: groupId(), login: login, name: name, manual: "true" }), ajaxHeaders);
            request
                .then(function () { window.location.reload(); })
                .catch(function (error) {
                    errorBox.text(errorMessage(error, "Could not save the member. Please try again.")).removeClass("d-none");
                    button.prop("disabled", false);
                });
        }

        function remove(link) {
            const login = link.attr("data-login");
            if (!window.confirm("Remove " + login + " from the members of this group? Their commits will no longer receive a grade.")) return;
            $("#members-alert").addClass("d-none").text("");
            axios.delete("/group-members", { params: { groupId: groupId(), login: login }, headers: ajaxHeaders.headers })
                .then(function () { window.location.reload(); })
                .catch(function (error) {
                    $("#members-alert").text(errorMessage(error, "Could not remove the member. Please try again.")).removeClass("d-none");
                });
        }

        $(document).ready(function () {
            modal = new bootstrap.Modal(document.getElementById("memberModal"));
            $("#add-member-link").on("click", function (e) { e.preventDefault(); openForm(null, null); });
            $(document)
                .on("click", ".edit-member-link", function (e) {
                    e.preventDefault();
                    openForm($(this).attr("data-login"), $(this).attr("data-name"));
                })
                .on("click", ".remove-member-link", function (e) { e.preventDefault(); remove($(this)); });
            $("#member-form").on("submit", save);
        });
    })();
</script>
</c:if>

</body>
</html>
