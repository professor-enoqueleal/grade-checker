<!DOCTYPE html>
<html lang="en">
<head>
    <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
    <link href="/webjars/bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
    <script src="/webjars/jquery/3.7.1/jquery.min.js"></script>
    <script src="/webjars/bootstrap/5.3.0/js/bootstrap.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/axios/dist/axios.min.js"></script>
    <meta charset="UTF-8">
    <title>Grade Checker | Manage groups</title>
    <style>
        .group-actions a + a { margin-left: 1rem; }
        .repository-cell, .repository-inline { overflow-wrap: anywhere; }
    </style>
</head>
<body>

<main class="container">
    <br>
    <nav style="--bs-breadcrumb-divider: '>';" aria-label="breadcrumb">
      <ol class="breadcrumb">
        <li class="breadcrumb-item"><a href="/home">Home</a></li>
        <li class="breadcrumb-item active" aria-current="page">Manage groups</li>
      </ol>
    </nav>
    <hr>

    <%-- openspec capability group-management: administrators only --%>
    <c:choose>
    <c:when test="${not empty pageError}">
        <div class="alert alert-danger" role="alert" id="groups-page-error"><c:out value="${pageError}"/></div>
    </c:when>
    <c:otherwise>

    <div id="content" data-open-add="<c:out value='${openAddForm}'/>">

        <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
            <h4 class="mb-0">Manage groups</h4>
            <a href="#" id="add-group-link">Add a new group</a>
        </div>

        <div id="groups-alert" class="alert alert-danger d-none" role="alert"></div>

        <c:choose>
        <c:when test="${empty groups}">
            <p class="text-body-secondary" id="no-groups">No groups yet.</p>
        </c:when>
        <c:otherwise>
        <div class="table-responsive">
            <table class="table table-sm table-hover align-middle" id="groups-table">
                <thead>
                <tr>
                    <th scope="col">Group</th>
                    <th scope="col">Name</th>
                    <th scope="col" class="d-none d-sm-table-cell">Repository</th>
                    <th scope="col" class="d-none d-sm-table-cell">Members</th>
                    <th scope="col">Actions</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="group" items="${groups}">
                    <tr data-group-id="<c:out value='${group.id}'/>">
                        <td class="group-id"><a href="/detail?id=<c:out value='${group.id}'/>"><c:out value="${group.id}"/></a></td>
                        <td>
                            <span class="group-name"><c:out value="${group.name}"/></span>
                            <%-- phones: the Repository column is hidden and the repository goes below the name --%>
                            <div class="repository-inline d-sm-none small"><c:out value="${group.repositoryLabel}"/></div>
                        </td>
                        <td class="repository-cell d-none d-sm-table-cell">
                            <c:choose>
                                <c:when test="${not empty group.repositoryLink}">
                                    <a href="<c:out value='${group.repositoryLink}'/>" target="_blank" rel="noopener noreferrer" title="<c:out value='${group.repositoryUrl}'/>"><c:out value="${group.repositoryLabel}"/></a>
                                </c:when>
                                <c:otherwise><c:out value="${group.repositoryLabel}"/></c:otherwise>
                            </c:choose>
                        </td>
                        <td class="group-members d-none d-sm-table-cell"><c:out value="${group.members}"/></td>
                        <td class="group-actions text-nowrap">
                            <a href="#" class="edit-group-link" data-group-id="<c:out value='${group.id}'/>"
                               data-name="<c:out value='${group.name}'/>" data-repository-url="<c:out value='${group.repositoryUrl}'/>">Edit</a>
                            <a href="#" class="archive-group-link" data-group-id="<c:out value='${group.id}'/>"
                               data-name="<c:out value='${group.name}'/>">Archive</a>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
        </c:otherwise>
        </c:choose>

        <c:if test="${not empty archivedGroups}">
            <h5 class="mt-4" id="archived-groups-title">Archived groups</h5>
            <div class="table-responsive">
                <table class="table table-sm align-middle text-body-secondary" id="archived-groups-table">
                    <thead>
                    <tr>
                        <th scope="col">Group</th>
                        <th scope="col">Name</th>
                        <th scope="col" class="d-none d-sm-table-cell">Repository</th>
                        <th scope="col" class="d-none d-sm-table-cell">Archived on</th>
                        <th scope="col">Actions</th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach var="group" items="${archivedGroups}">
                        <tr data-group-id="<c:out value='${group.id}'/>">
                            <td><c:out value="${group.id}"/></td>
                            <td>
                                <span class="group-name"><c:out value="${group.name}"/></span>
                                <div class="repository-inline d-sm-none small"><c:out value="${group.repositoryLabel}"/></div>
                            </td>
                            <td class="repository-cell d-none d-sm-table-cell" title="<c:out value='${group.repositoryUrl}'/>"><c:out value="${group.repositoryLabel}"/></td>
                            <td class="d-none d-sm-table-cell"><c:out value="${group.archivedOn}"/></td>
                            <td class="group-actions text-nowrap">
                                <a href="#" class="restore-group-link" data-group-id="<c:out value='${group.id}'/>">Restore</a>
                            </td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:if>

    </div>

    </c:otherwise>
    </c:choose>

</main>

<c:if test="${empty pageError}">
<%-- add / edit group form --%>
<div class="modal fade" id="groupModal" tabindex="-1" aria-labelledby="groupModalLabel" aria-hidden="true">
    <form id="group-form" novalidate>
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <h1 class="modal-title fs-5" id="groupModalLabel">Add a new group</h1>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                </div>
                <div class="modal-body">

                    <input type="hidden" id="group-id" value="">
                    <input type="hidden" id="original-repository-url" value="">

                    <div class="mb-3">
                        <label for="group-name" class="form-label">Name</label>
                        <input type="text" class="form-control" id="group-name" maxlength="255" required>
                    </div>

                    <div class="mb-3">
                        <label for="repository-url" class="form-label">Repository URL</label>
                        <input type="url" class="form-control" id="repository-url" maxlength="255"
                               placeholder="https://github.com/owner/repository" required>
                    </div>

                    <div id="repository-change-warning" class="alert alert-warning d-none small" role="status">
                        Contributors and grades will come from the new repository. Members and linked identities stay.
                        If this group comes from a seed of <code>db.sql</code>, update <code>db.sql</code> too, or the
                        group with the old URL is created again at the next start.
                    </div>

                    <div id="group-form-error" class="alert alert-danger d-none mb-0 small" role="alert"></div>

                </div>
                <div class="modal-footer">
                    <button type="submit" class="btn btn-primary" id="save-group">Save</button>
                </div>
            </div>
        </div>
    </form>
</div>

<script>
    (function () {
        const ajaxHeaders = { "X-Requested-With": "XMLHttpRequest" };
        let modal = null;

        function errorMessage(error, fallback) {
            const body = error.response && error.response.data;
            return body && body.message ? body.message : fallback;
        }

        function showAlert(text) {
            $("#groups-alert").text(text).removeClass("d-none");
        }

        // the same form adds (no id) and edits (id in the hidden field)
        function openForm(id, name, url) {
            const editing = !!id;
            $("#groupModalLabel").text(editing ? "Edit group" : "Add a new group");
            $("#group-id").val(id || "");
            $("#group-name").val(name || "");
            $("#repository-url").val(url || "");
            $("#original-repository-url").val(url || "");
            $("#repository-change-warning").addClass("d-none");
            $("#group-form-error").addClass("d-none").text("");
            $("#save-group").prop("disabled", false);
            modal.show();
        }

        // editing: warn when the repository changes (contributors, grades and seeds)
        function repositoryChanged() {
            const original = $("#original-repository-url").val();
            const changed = !!$("#group-id").val() && $("#repository-url").val().trim() !== original;
            $("#repository-change-warning").toggleClass("d-none", !changed);
        }

        function save(event) {
            event.preventDefault();
            const id = $("#group-id").val();
            const name = $("#group-name").val().trim();
            const url = $("#repository-url").val().trim();
            const errorBox = $("#group-form-error").addClass("d-none").text("");
            if (!name || !url) {
                errorBox.text("Please fill in the name and the repository URL.").removeClass("d-none");
                return;
            }
            const button = $("#save-group").prop("disabled", true);
            const request = id
                ? axios.put("/groups/api", null, { params: { id: id, name: name, repositoryUrl: url }, headers: ajaxHeaders })
                : axios.post("/groups/api", new URLSearchParams({ name: name, repositoryUrl: url }), { headers: ajaxHeaders });
            request
                .then(function () { window.location.href = "/groups"; })
                .catch(function (error) {
                    errorBox.text(errorMessage(error, "Could not save the group. Please try again.")).removeClass("d-none");
                    button.prop("disabled", false);
                });
        }

        function change(action, id) {
            $("#groups-alert").addClass("d-none").text("");
            axios.post("/groups/api/" + action, null, { params: { id: id }, headers: ajaxHeaders })
                .then(function () { window.location.href = "/groups"; })
                .catch(function (error) {
                    showAlert(errorMessage(error, "Could not " + action + " the group. Please try again."));
                });
        }

        $(document).ready(function () {
            modal = new bootstrap.Modal(document.getElementById("groupModal"));
            $("#add-group-link").on("click", function (e) { e.preventDefault(); openForm(null, null, null); });
            $("#repository-url").on("input", repositoryChanged);
            $(document)
                .on("click", ".edit-group-link", function (e) {
                    e.preventDefault();
                    openForm($(this).attr("data-group-id"), $(this).attr("data-name"), $(this).attr("data-repository-url"));
                })
                .on("click", ".archive-group-link", function (e) {
                    e.preventDefault();
                    const name = $(this).attr("data-name");
                    if (!window.confirm("Archive " + name + "? It disappears from the home and the class grades; members and linked identities are kept.")) return;
                    change("archive", $(this).attr("data-group-id"));
                })
                .on("click", ".restore-group-link", function (e) {
                    e.preventDefault();
                    change("restore", $(this).attr("data-group-id"));
                });
            $("#group-form").on("submit", save);

            // "Add a new group" of the empty home: /groups?add=1 opens the form
            if ($("#content").data("open-add") === true) openForm(null, null, null);
        });
    })();
</script>
</c:if>

</body>
</html>
