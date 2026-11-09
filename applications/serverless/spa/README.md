# Grade Checker — frontend (SPA)

SPA em **Vue 3 + Vite + TypeScript**, com **Bootstrap 5** e **Chart.js**, que substitui as JSPs da versão Jetty. Em produção, o `dist/` fica num bucket S3 privado atrás do CloudFront, e `/api/*` vai para o API Gateway (veja `serverless/migration-plan.md`).

As telas reproduzem as JSPs atuais: mesmos textos, mesmas URLs (`/home`, `/detail?id=`, `/contributions-detail`, `/grades`, `/groups`, `/group/members?id=`) e mesmos IDs. Assim, a maior parte dos testes Playwright de `e2e/` continua valendo.

## Rodar o frontend isolado

**Pré-requisito:** Node 22 (`node -v`). Na primeira vez, nesta pasta:

```sh
npm install
```

Há três jeitos de rodar, todos nesta pasta:

| Jeito | Comando | Endereço | Backend |
|---|---|---|---|
| **A. Sem backend** | `npm run dev:mock` | http://localhost:5173 | nenhum: a API é simulada em memória |
| **B. Contra uma API rodando** | `npm run dev` | http://localhost:5173 | uma API em `http://127.0.0.1:3000` (ou em `VITE_API_TARGET`) |
| **C. Build de produção** | `npm run build && npm run preview` | http://localhost:4173 | o mesmo de B |

Abra sempre por `localhost`, e não `127.0.0.1`: o cookie de sessão é `Secure`, e o navegador só o aceita sem HTTPS em `localhost`.

### A. Sem backend (mock)

```sh
npm run dev:mock
```

Não precisa de Docker, Java nem GitHub. Todas as telas funcionam com dados fictícios. Veja como entrar em "Login no modo mock", abaixo. É o jeito de trabalhar no visual.

### B. Contra uma API que já está rodando

```sh
npm run dev
```

O Vite repassa `/api` para `http://127.0.0.1:3000`, que é onde ficam:
- o `sam local start-api` do backend (veja `applications/serverless/lambda/README.md`);
- o gateway do Docker Compose (`docker compose up -d dynamodb setup api gateway`, na pasta `serverless`).

Para outra API, use `VITE_API_TARGET`:

```sh
VITE_API_TARGET=http://localhost:3001 npm run dev
```

Em qualquer caso, a tela recarrega sozinha a cada mudança no código.

### C. Build de produção

```sh
npm run build      # checagem de tipos + dist/
npm run preview    # serve o dist/ em http://localhost:4173, com /api repassado como em B
```

Serve para conferir o build que vai para o S3 antes de publicar. Por exemplo, as rotas da SPA ao recarregar a página.

## Comandos

| Comando | O que faz |
|---|---|
| `npm install` | instala as dependências (versões fixas no `package.json`) |
| `npm run dev:mock` | sobe em `http://localhost:5173` com a **API simulada** em memória (`mock/`), sem backend nem GitHub |
| `npm run dev` | sobe em `http://localhost:5173` e repassa `/api` para `http://127.0.0.1:3000` (ou `VITE_API_TARGET`) |
| `npm run build` | checagem de tipos e build de produção em `dist/` |
| `npm run preview` | serve o `dist/` em `http://localhost:4173`, com `/api` repassado como no `npm run dev` |
| `npm test` | testes unitários (Vitest): CSV, formatação, colunas de nota, navbar |
| `npm run typecheck` | checagem de tipos (`vue-tsc`) |

A imagem Docker (`Dockerfile` + `nginx.conf`) é a usada pelo Docker Compose da pasta `serverless`: o nginx repassa `/api` para o serviço `gateway`, que só existe dentro do Compose.

### Login no modo mock

- **Continue with GitHub:** entra como administrador (`professor`).
- **Personal access token:**
  - `student` entra como aluno (sem as telas de administrador);
  - `bad` é recusado (401);
  - qualquer outro valor entra como administrador.

Os dados do mock (4 grupos, um deles arquivado, com membros, uma identidade sem conta e uma associação manual) ficam em memória até o servidor reiniciar. Alguns valores simulam erros do backend: um repositório com `does-not-exist` na URL não "existe no GitHub", e um login com `ghost` não "existe" como usuário.

## Estrutura

```
src/
  api/          contrato (types.ts), cliente HTTP (client.ts) e uma função por endpoint (index.ts)
  composables/  sessão (/api/me), configuração (/api/config), tema da tela de login, modal do Bootstrap
  components/   navbar, breadcrumb, filtro de período, gráficos, cards, tabela do dashboard,
                explicação da nota (pt-BR), modais de grupo, membro e associação de identidade
  views/        login, home, detalhe do grupo, histórico de commits, notas da turma, grupos, membros, 404
  router/       rotas (mesmas URLs das JSPs), proteção de rotas e título das páginas
  utils/        formatação, colunas de nota, CSV das notas, configuração do Chart.js
mock/           API simulada para `npm run dev:mock` (plugin do Vite)
tests/          testes unitários (Vitest)
```

## Decisões

- **Sessão:** cookie HttpOnly definido pelo backend. O token do GitHub nunca chega ao navegador, e a SPA não guarda nada sobre a sessão. Quem está logado e se é administrador vêm de `GET /api/me`. Essa flag só mostra ou esconde links; o backend autoriza de novo cada chamada.
- **CSRF:** toda chamada envia `X-Requested-With: XMLHttpRequest`. O backend recusa, com 403, as alterações que chegarem sem esse cabeçalho.
- **401:** qualquer chamada protegida que responder 401 (sessão expirada) leva à tela de login.
- **Bootstrap JS** é carregado uma única vez (`main.ts`). Carregar duas vezes quebra o menu do celular, como aconteceu nas JSPs.
- **Formatação no backend:** datas, períodos e a explicação da nota chegam formatados pelo backend, no fuso de `milestones.properties`, como nas JSPs. A SPA só formata notas e porcentagens.
- **CSV das notas:** gerado no navegador (`utils/csv.ts`) a partir das notas já carregadas, com o mesmo formato do antigo `/grades.csv` (BOM, `;`, vírgula decimal, CRLF e proteção contra fórmulas). Assim, nenhuma requisição chega perto do limite de 30 s do API Gateway.
- **Tema:** o seletor Light/Dark/Auto continua só na tela de login, e as outras telas ficam claras, como antes, porque as cores dos gráficos são para fundo claro.

## Contrato da API

Todas as rotas ficam sob `/api`, na mesma origem da SPA. Os erros têm sempre o corpo `{ "error": "<código>", "message": "<texto para a tela>" }`, e a SPA mostra o `message`. Os tipos estão em `src/api/types.ts`. O mock (`mock/plugin.ts`) implementa o mesmo contrato e serve de referência executável para o backend.

### Sessão (públicas)

| Método e rota | Corpo / parâmetros | Resposta |
|---|---|---|
| `GET /api/me` | — | `200 { login, isAdmin }` ou `401` |
| `GET /api/auth/github` | — | `302` para o GitHub (com `state`) |
| `GET /api/auth/callback` | `code`, `state` | `302 /home` com o cookie de sessão, ou `302 /login?error=github_failed` / `github_not_verified` |
| `POST /api/auth/token` | `{ token }` | `200 { login, isAdmin }` com o cookie; `400` sem token; `401` token recusado; `502` GitHub indisponível |
| `POST /api/auth/logout` | — | `200`, apaga a sessão; a SPA vai para `/login?signedOut=1` |

### Autenticadas

| Método e rota | Parâmetros | Resposta |
|---|---|---|
| `GET /api/config` | — | `AppConfig`: botões de período, valor padrão do "Until" e números da explicação da nota |
| `GET /api/groups` | — | `GroupSummary[]`: grupos ativos (home e "Manage groups") |
| `GET /api/groups/{id}` | — | `GroupDetail`: grupo, membros, cards e alunos para associação (só administradores); `404` se não existir ou estiver arquivado |
| `GET /api/groups/{id}/contributions` | `period` (`m1`, `m2`, `m3`, `all`, `custom`), `since`, `until` | `ContributionsReport`; `400 invalid_period`; erros do GitHub com o status e a mensagem deles |
| `GET /api/groups/{id}/commits` | `author`, `period`, `since`, `until` | `CommitHistory`; um período inválido volta para All com `periodWarning` |

### Somente administradores (`403` para os demais)

| Método e rota | Corpo / parâmetros | Resposta |
|---|---|---|
| `GET /api/groups/archived` | — | `{ groups: ArchivedGroup[] }` |
| `POST /api/groups` | `{ name, repositoryUrl }` | `GroupSummary`; `400` URL inválida ou repositório inacessível; `409` repositório já cadastrado (ativo ou arquivado) |
| `PUT /api/groups/{id}` | `{ name, repositoryUrl }` | `GroupSummary`; mesmos erros; `404` |
| `POST /api/groups/{id}/archive` e `/restore` | — | `GroupSummary`; `404` se não existir ou já estiver nesse estado |
| `GET /api/groups/{id}/members` | — | `{ group: { id, name }, members: Member[] }` |
| `POST /api/groups/{id}/members` | `{ login, name, manual }` | `Member`; `400` login inválido ou inexistente; `409` já é membro |
| `PUT /api/groups/{id}/members/{login}` | `{ login, name }` (novo login e nome) | `Member`; as associações manuais seguem o novo login; `404`, `409` |
| `DELETE /api/groups/{id}/members/{login}` | — | `200`; `409 has_aliases` se houver identidades associadas ao aluno |
| `POST /api/groups/{id}/aliases` | `{ identityKey, login }` | `200`; `400` identidade inválida ou login que não é aluno do grupo |
| `DELETE /api/groups/{id}/aliases` | `identityKey` (query) | `200`; `404` se não estiver associada |
| `GET /api/grades` | — | `{ groups: [{ id, name, students }] }` (ativos, por nome) |
| `GET /api/grades/{groupId}` | — | `GroupGrades`: M1, M2, M3 e total de cada aluno, marcos em andamento e commits não contados |

As mensagens de erro são as mesmas da versão Jetty (por exemplo, "Only administrators can manage groups.", "This repository is already registered.").
