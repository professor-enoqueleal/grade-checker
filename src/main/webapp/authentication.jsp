<!doctype html>
<html lang="en" data-bs-theme="auto">
<head>
    <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
    <script src="/js/color-modes.js"></script>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="Sign in to Grade Checker">
    <meta name="author" content="Enoque Leal">
    <title>Sign in · Grade Checker</title>

    <link href="/webjars/bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
    <link href="/css/style.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.10.5/font/bootstrap-icons.css">

    <meta name="theme-color" content="#712cf9">

    <style>
        :root {
            --gh-btn-bg: #24292f;
            --gh-btn-hover-bg: #32383f;
            --gh-btn-color: #ffffff;
            --gh-btn-border: #24292f;
        }

        [data-bs-theme="dark"] {
            --gh-btn-bg: #f6f8fa;
            --gh-btn-hover-bg: #ffffff;
            --gh-btn-color: #1f2328;
            --gh-btn-border: #f6f8fa;
        }

        html, body {
            height: 100%;
        }

        .auth-wrapper {
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 2rem 1rem;
        }

        .auth-card {
            width: 100%;
            max-width: 400px;
        }

        .auth-logo {
            width: 56px;
            height: 56px;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            border-radius: 14px;
            background-color: #712cf9;
            color: #fff;
            font-size: 1.75rem;
        }

        .btn-github {
            --bs-btn-color: var(--gh-btn-color);
            --bs-btn-bg: var(--gh-btn-bg);
            --bs-btn-border-color: var(--gh-btn-border);
            --bs-btn-hover-color: var(--gh-btn-color);
            --bs-btn-hover-bg: var(--gh-btn-hover-bg);
            --bs-btn-hover-border-color: var(--gh-btn-hover-bg);
            --bs-btn-active-color: var(--gh-btn-color);
            --bs-btn-active-bg: var(--gh-btn-hover-bg);
            --bs-btn-active-border-color: var(--gh-btn-hover-bg);
            --bs-btn-focus-shadow-rgb: 112, 44, 249;
            font-weight: 600;
        }

        .auth-divider {
            display: flex;
            align-items: center;
            gap: .75rem;
            color: var(--bs-secondary-color);
            font-size: .875rem;
        }

        .auth-divider::before,
        .auth-divider::after {
            content: "";
            flex: 1;
            border-top: 1px solid var(--bs-border-color);
        }

        .btn-token-toggle .bi-chevron-down {
            transition: transform .2s ease-in-out;
        }

        .btn-token-toggle[aria-expanded="true"] .bi-chevron-down {
            transform: rotate(180deg);
        }
    </style>
</head>
<body class="bg-body-tertiary">
<svg xmlns="http://www.w3.org/2000/svg" style="display: none;">
    <symbol id="check2" viewBox="0 0 16 16">
        <path d="M13.854 3.646a.5.5 0 0 1 0 .708l-7 7a.5.5 0 0 1-.708 0l-3.5-3.5a.5.5 0 1 1 .708-.708L6.5 10.293l6.646-6.647a.5.5 0 0 1 .708 0z"/>
    </symbol>
    <symbol id="circle-half" viewBox="0 0 16 16">
        <path d="M8 15A7 7 0 1 0 8 1v14zm0 1A8 8 0 1 1 8 0a8 8 0 0 1 0 16z"/>
    </symbol>
    <symbol id="moon-stars-fill" viewBox="0 0 16 16">
        <path d="M6 .278a.768.768 0 0 1 .08.858 7.208 7.208 0 0 0-.878 3.46c0 4.021 3.278 7.277 7.318 7.277.527 0 1.04-.055 1.533-.16a.787.787 0 0 1 .81.316.733.733 0 0 1-.031.893A8.349 8.349 0 0 1 8.344 16C3.734 16 0 12.286 0 7.71 0 4.266 2.114 1.312 5.124.06A.752.752 0 0 1 6 .278z"/>
        <path d="M10.794 3.148a.217.217 0 0 1 .412 0l.387 1.162c.173.518.579.924 1.097 1.097l1.162.387a.217.217 0 0 1 0 .412l-1.162.387a1.734 1.734 0 0 0-1.097 1.097l-.387 1.162a.217.217 0 0 1-.412 0l-.387-1.162A1.734 1.734 0 0 0 9.31 6.593l-1.162-.387a.217.217 0 0 1 0-.412l1.162-.387a1.734 1.734 0 0 0 1.097-1.097l.387-1.162zM13.863.099a.145.145 0 0 1 .274 0l.258.774c.115.346.386.617.732.732l.774.258a.145.145 0 0 1 0 .274l-.774.258a1.156 1.156 0 0 0-.732.732l-.258.774a.145.145 0 0 1-.274 0l-.258-.774a1.156 1.156 0 0 0-.732-.732l-.774-.258a.145.145 0 0 1 0-.274l.774-.258c.346-.115.617-.386.732-.732L13.863.1z"/>
    </symbol>
    <symbol id="sun-fill" viewBox="0 0 16 16">
        <path d="M8 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM8 0a.5.5 0 0 1 .5.5v2a.5.5 0 0 1-1 0v-2A.5.5 0 0 1 8 0zm0 13a.5.5 0 0 1 .5.5v2a.5.5 0 0 1-1 0v-2A.5.5 0 0 1 8 13zm8-5a.5.5 0 0 1-.5.5h-2a.5.5 0 0 1 0-1h2a.5.5 0 0 1 .5.5zM3 8a.5.5 0 0 1-.5.5h-2a.5.5 0 0 1 0-1h2A.5.5 0 0 1 3 8zm10.657-5.657a.5.5 0 0 1 0 .707l-1.414 1.415a.5.5 0 1 1-.707-.708l1.414-1.414a.5.5 0 0 1 .707 0zm-9.193 9.193a.5.5 0 0 1 0 .707L3.05 13.657a.5.5 0 0 1-.707-.707l1.414-1.414a.5.5 0 0 1 .707 0zm9.193 2.121a.5.5 0 0 1-.707 0l-1.414-1.414a.5.5 0 0 1 .707-.707l1.414 1.414a.5.5 0 0 1 0 .707zM4.464 4.465a.5.5 0 0 1-.707 0L2.343 3.05a.5.5 0 1 1 .707-.707l1.414 1.414a.5.5 0 0 1 0 .708z"/>
    </symbol>
    <symbol id="github" viewBox="0 0 16 16">
        <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.012 8.012 0 0 0 16 8c0-4.42-3.58-8-8-8z"/>
    </symbol>
</svg>

<div class="dropdown position-fixed bottom-0 end-0 mb-3 me-3 bd-mode-toggle">
    <button class="btn btn-bd-primary py-2 dropdown-toggle d-flex align-items-center"
            id="bd-theme"
            type="button"
            aria-expanded="false"
            data-bs-toggle="dropdown"
            aria-label="Toggle theme (auto)">
        <svg class="bi my-1 theme-icon-active" width="1em" height="1em">
            <use href="#circle-half"></use>
        </svg>
        <span class="visually-hidden" id="bd-theme-text">Toggle theme</span>
    </button>
    <ul class="dropdown-menu dropdown-menu-end shadow" aria-labelledby="bd-theme-text">
        <li>
            <button type="button" class="dropdown-item d-flex align-items-center" data-bs-theme-value="light"
                    aria-pressed="false">
                <svg class="bi me-2 opacity-50 theme-icon" width="1em" height="1em">
                    <use href="#sun-fill"></use>
                </svg>
                Light
                <svg class="bi ms-auto d-none" width="1em" height="1em">
                    <use href="#check2"></use>
                </svg>
            </button>
        </li>
        <li>
            <button type="button" class="dropdown-item d-flex align-items-center" data-bs-theme-value="dark"
                    aria-pressed="false">
                <svg class="bi me-2 opacity-50 theme-icon" width="1em" height="1em">
                    <use href="#moon-stars-fill"></use>
                </svg>
                Dark
                <svg class="bi ms-auto d-none" width="1em" height="1em">
                    <use href="#check2"></use>
                </svg>
            </button>
        </li>
        <li>
            <button type="button" class="dropdown-item d-flex align-items-center active" data-bs-theme-value="auto"
                    aria-pressed="true">
                <svg class="bi me-2 opacity-50 theme-icon" width="1em" height="1em">
                    <use href="#circle-half"></use>
                </svg>
                Auto
                <svg class="bi ms-auto d-none" width="1em" height="1em">
                    <use href="#check2"></use>
                </svg>
            </button>
        </li>
    </ul>
</div>

<main class="auth-wrapper">
    <div class="auth-card">

        <div class="text-center mb-4">
            <span class="auth-logo mb-3" aria-hidden="true"><i class="bi bi-mortarboard-fill"></i></span>
            <h1 class="h4 fw-semibold mb-1">Sign in to Grade Checker</h1>
            <p class="text-body-secondary small mb-0">Use your GitHub account to continue</p>
        </div>

        <c:if test="${hasMessage == true or not empty message}">
            <div class="alert alert-danger alert-dismissible fade show small" role="alert">
                <i class="bi bi-exclamation-circle me-1"></i>
                <c:out value="${message}"/>
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            </div>
        </c:if>

        <div class="card border-0 shadow-sm rounded-3">
            <div class="card-body p-4">

                <a class="btn btn-github btn-lg w-100 d-flex align-items-center justify-content-center gap-2 fs-6 py-2"
                   href="https://github.com/login/oauth/authorize?client_id=Iv1.51b5bb131e5cd47a">
                    <svg class="bi" width="20" height="20" aria-hidden="true"><use href="#github"></use></svg>
                    Continue with GitHub
                </a>

                <div class="auth-divider my-4">or</div>

                <button class="btn btn-link btn-token-toggle w-100 text-body-secondary text-decoration-none small d-flex align-items-center justify-content-center gap-1 p-0"
                        type="button"
                        data-bs-toggle="collapse"
                        data-bs-target="#token-form"
                        aria-expanded="false"
                        aria-controls="token-form">
                    <i class="bi bi-key"></i>
                    Sign in with a personal access token
                    <i class="bi bi-chevron-down small"></i>
                </button>

                <div class="collapse" id="token-form">
                    <form action="/login" method="post" class="pt-3">
                        <label for="access-token" class="form-label small fw-semibold">Personal access token</label>
                        <input type="password" name="access-token" class="form-control" id="access-token"
                               placeholder="ghp_xxxxxxxxxxxxxxxxxxxx" autocomplete="off" required>
                        <div class="form-text">
                            Generate one in
                            <a href="https://github.com/settings/tokens" target="_blank" rel="noopener noreferrer">GitHub settings</a>.
                        </div>
                        <button type="submit" class="btn btn-outline-secondary w-100 mt-3">Sign in with token</button>
                    </form>
                </div>

            </div>
        </div>

        <p class="text-center text-body-secondary small mt-4 mb-0">
            Grade Checker uses GitHub to read repositories, commits and contributors.
        </p>

    </div>
</main>

<script src="/webjars/bootstrap/5.3.0/js/bootstrap.bundle.js"></script>
<script>
    document.getElementById('token-form')
        .addEventListener('shown.bs.collapse', () => document.getElementById('access-token').focus());
</script>
</body>
</html>
