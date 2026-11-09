<%@ tag description="Navbar of the authenticated pages (openspec capability navigation-bar)" pageEncoding="UTF-8" %>
<%@ attribute name="active" required="false" description="Current page in the navbar: groups, grades or empty" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%-- One navbar for every authenticated page: <gc:navbar active="grades"/>.
     Login and administrator flag come from the session (set at sign-in by AdminConfig.signIn); the flag only shows
     or hides links, authorization is always checked on the server. Needs Bootstrap JS on the page (menu on phones). --%>
<nav class="navbar navbar-expand-md bg-body-tertiary border-bottom" aria-label="Main navigation" id="main-navbar">
    <div class="container">
        <a class="navbar-brand" href="/home" id="navbar-brand">Grade Checker</a>
        <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#main-navbar-items"
                aria-controls="main-navbar-items" aria-expanded="false" aria-label="Toggle navigation">
            <span class="navbar-toggler-icon"></span>
        </button>
        <div class="collapse navbar-collapse" id="main-navbar-items">
            <ul class="navbar-nav me-auto">
                <c:if test="${sessionScope.isAdmin eq true}">
                    <c:set var="groupsActive" value="${active eq 'groups'}"/>
                    <c:set var="gradesActive" value="${active eq 'grades'}"/>
                    <li class="nav-item">
                        <a class="nav-link${groupsActive ? ' active' : ''}" href="/groups" id="manage-groups-link"<c:if test="${groupsActive}"> aria-current="page"</c:if>>Manage groups</a>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link${gradesActive ? ' active' : ''}" href="/grades" id="class-grades-link"<c:if test="${gradesActive}"> aria-current="page"</c:if>>Class grades</a>
                    </li>
                </c:if>
            </ul>
            <ul class="navbar-nav align-items-md-center">
                <c:if test="${not empty sessionScope.userLogin}">
                    <li class="nav-item">
                        <span class="navbar-text me-md-3" id="navbar-user"><c:out value="${sessionScope.userLogin}"/></span>
                    </li>
                </c:if>
                <li class="nav-item">
                    <a class="nav-link" href="/logout" id="sign-out-link">Sign out</a>
                </li>
            </ul>
        </div>
    </div>
</nav>
