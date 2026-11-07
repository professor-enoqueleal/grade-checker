<!DOCTYPE html>
<html lang="en">
<head>
    <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
    <link href="/webjars/bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
    <meta charset="UTF-8">
    <title>Grade Checker | Class grades</title>
    <style>
        .sort-button {
            border: 0;
            background: none;
            padding: 0;
            font-weight: 600;
            color: inherit;
        }
        .sort-button[data-direction="asc"]::after { content: " \25B2"; }
        .sort-button[data-direction="desc"]::after { content: " \25BC"; }
        .grade-cell { white-space: nowrap; }
    </style>
</head>
<body>

<main class="container">
    <br>
    <nav style="--bs-breadcrumb-divider: '>';" aria-label="breadcrumb">
      <ol class="breadcrumb">
        <li class="breadcrumb-item"><a href="/home">Home</a></li>
        <li class="breadcrumb-item active" aria-current="page">Class grades</li>
      </ol>
    </nav>
    <hr>

    <%-- openspec capability class-gradebook: administrators only --%>
    <c:choose>
    <c:when test="${not empty forbidden}">
        <div class="alert alert-danger" role="alert" id="grades-forbidden"><c:out value="${forbidden}"/></div>
    </c:when>
    <c:otherwise>

    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h4 class="mb-0">Class grades</h4>
        <div class="d-flex flex-wrap gap-2">
            <input type="search" class="form-control form-control-sm" id="grades-filter" placeholder="Filter by group or student"
                   aria-label="Filter by group or student" style="min-width: 16rem;">
            <a class="btn btn-sm btn-outline-primary" id="export-csv" href="/grades.csv" download>Export CSV</a>
        </div>
    </div>

    <p class="small text-body-secondary" id="grades-help">
        Grades of every student of every group, calculated as in the group detail (period All).
        Milestones not started show "—" and do not count in the total.
    </p>

    <div class="table-responsive">
        <table class="table table-sm table-hover align-middle" id="grades-table">
            <thead>
            <tr>
                <th scope="col"><button type="button" class="sort-button" data-sort="group">Group</button></th>
                <th scope="col"><button type="button" class="sort-button" data-sort="login">Student</button></th>
                <th scope="col"><button type="button" class="sort-button" data-sort="m1" id="header-m1">M1</button></th>
                <th scope="col"><button type="button" class="sort-button" data-sort="m2" id="header-m2">M2</button></th>
                <th scope="col"><button type="button" class="sort-button" data-sort="m3" id="header-m3">M3</button></th>
                <th scope="col"><button type="button" class="sort-button" data-sort="total">Total (0–9)</button></th>
                <th scope="col">Notes</th>
            </tr>
            </thead>
            <tbody id="grades-tbody">
            <c:forEach var="group" items="${groups}">
                <c:url var="detailUrl" value="/detail"><c:param name="id" value="${group.id}"/></c:url>
                <c:choose>
                    <c:when test="${empty group.students}">
                        <tr class="grade-row empty-group" data-group-id="${group.id}" data-group-name="<c:out value='${group.name}'/>" data-login="">
                            <td><a href="<c:out value='${detailUrl}'/>"><c:out value="${group.name}"/></a></td>
                            <td colspan="6" class="text-body-secondary">No students registered</td>
                        </tr>
                    </c:when>
                    <c:otherwise>
                        <c:forEach var="student" items="${group.students}">
                            <tr class="grade-row pending" data-group-id="${group.id}" data-group-name="<c:out value='${group.name}'/>" data-login="<c:out value='${student}'/>">
                                <td><a href="<c:out value='${detailUrl}'/>"><c:out value="${group.name}"/></a></td>
                                <td class="student-login"><c:out value="${student}"/></td>
                                <td class="grade-cell" data-key="m1">Loading…</td>
                                <td class="grade-cell" data-key="m2">Loading…</td>
                                <td class="grade-cell" data-key="m3">Loading…</td>
                                <td class="grade-cell fw-semibold" data-key="total">Loading…</td>
                                <td class="group-notes small"></td>
                            </tr>
                        </c:forEach>
                    </c:otherwise>
                </c:choose>
            </c:forEach>
            </tbody>
        </table>
    </div>

    <c:if test="${empty groups}">
        <p class="text-center text-body-secondary" id="no-groups">No groups to show.</p>
    </c:if>

    </c:otherwise>
    </c:choose>
</main>

<script>
(function () {
    "use strict";

    const tbody = document.getElementById("grades-tbody");
    if (!tbody) return; // forbidden

    const PARALLEL = 3;                       // at most 3 groups at a time (up to 3 GitHub reports each)
    const MILESTONES = ["m1", "m2", "m3"];

    function rowsOf(groupId) {
        return Array.prototype.slice.call(tbody.querySelectorAll('tr.grade-row[data-group-id="' + groupId + '"]'));
    }

    function format(value) {
        return value === null || value === undefined ? "—" : Number(value).toFixed(2);
    }

    function markProvisionalHeaders(provisional) {
        MILESTONES.forEach(function (key, i) {
            const header = document.getElementById("header-" + key);
            if (provisional.indexOf(key) >= 0) header.textContent = "M" + (i + 1) + " (in progress)";
        });
    }

    function fillGroup(groupId, data) {
        const provisional = data.provisionalMilestones || [];
        markProvisionalHeaders(provisional);
        const byLogin = {};
        (data.students || []).forEach(function (s) { byLogin[s.login.toLowerCase()] = s; });

        rowsOf(groupId).forEach(function (row) {
            const s = byLogin[(row.dataset.login || "").toLowerCase()] || {};
            row.querySelectorAll("td.grade-cell").forEach(function (td) {
                const key = td.dataset.key;
                const value = s[key];
                const missing = value === null || value === undefined;
                td.textContent = format(value) + (!missing && provisional.indexOf(key) >= 0 ? " (in progress)" : "");
                td.dataset.value = missing ? "" : String(value);
            });
            const notes = row.querySelector("td.group-notes");
            notes.textContent = data.excludedCommits > 0 ? data.excludedCommits + " commits not counted in grades" : "";
            notes.classList.toggle("text-warning-emphasis", data.excludedCommits > 0);
            row.classList.remove("pending");
        });
    }

    function failGroup(groupId, message) {
        rowsOf(groupId).forEach(function (row) {
            row.querySelectorAll("td.grade-cell").forEach(function (td) {
                td.textContent = "—";
                td.dataset.value = "";
            });
            const notes = row.querySelector("td.group-notes");
            notes.textContent = message;
            notes.classList.add("text-danger");
            row.classList.remove("pending");
            row.classList.add("group-error");
        });
    }

    function load(groupId) {
        return fetch("/grades/group?id=" + encodeURIComponent(groupId), { credentials: "same-origin", headers: { "Accept": "application/json" } })
            .then(function (response) {
                return response.json().catch(function () { return {}; }).then(function (body) {
                    if (response.ok) fillGroup(groupId, body);
                    else failGroup(groupId, body.message || "Could not calculate the grades of this group.");
                });
            })
            .catch(function () { failGroup(groupId, "Could not calculate the grades of this group."); });
    }

    // progressive load: groups in page order, PARALLEL at a time
    const queue = [];
    tbody.querySelectorAll("tr.grade-row.pending").forEach(function (row) {
        if (queue.indexOf(row.dataset.groupId) < 0) queue.push(row.dataset.groupId);
    });
    function worker() {
        const next = queue.shift();
        if (next === undefined) return Promise.resolve();
        return load(next).then(worker);
    }
    for (let i = 0; i < PARALLEL; i++) worker();

    // ---------------- sorting: missing grades ("—") and errors always last ----------------
    function sortValue(row, key) {
        if (key === "group") return (row.dataset.groupName || "").toLowerCase();
        if (key === "login") return (row.dataset.login || "").toLowerCase();
        const td = row.querySelector('td.grade-cell[data-key="' + key + '"]');
        if (!td || !td.dataset.value) return null;
        return Number(td.dataset.value);
    }

    document.querySelectorAll(".sort-button").forEach(function (button) {
        button.addEventListener("click", function () {
            const key = button.dataset.sort;
            const direction = button.dataset.direction === "asc" ? "desc" : "asc";
            document.querySelectorAll(".sort-button").forEach(function (b) { delete b.dataset.direction; });
            button.dataset.direction = direction;
            const sign = direction === "asc" ? 1 : -1;

            const rows = Array.prototype.slice.call(tbody.querySelectorAll("tr.grade-row"));
            rows.sort(function (a, b) {
                const va = sortValue(a, key);
                const vb = sortValue(b, key);
                const ma = va === null || va === "" || a.classList.contains("group-error");
                const mb = vb === null || vb === "" || b.classList.contains("group-error");
                if (ma !== mb) return ma ? 1 : -1;
                if (ma && mb) return 0;
                if (va < vb) return -sign;
                if (va > vb) return sign;
                return 0;
            });
            rows.forEach(function (row) { tbody.appendChild(row); });
        });
    });

    // ---------------- filter by group or student ----------------
    document.getElementById("grades-filter").addEventListener("input", function (event) {
        const text = event.target.value.trim().toLowerCase();
        tbody.querySelectorAll("tr.grade-row").forEach(function (row) {
            const match = !text || (row.dataset.groupName || "").toLowerCase().indexOf(text) >= 0
                || (row.dataset.login || "").toLowerCase().indexOf(text) >= 0;
            row.classList.toggle("d-none", !match);
        });
    });
})();
</script>
<script src="/webjars/bootstrap/5.3.0/js/bootstrap.bundle.js"></script>
</body>
</html>
