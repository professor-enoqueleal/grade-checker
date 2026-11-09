# Testes E2E da versão serverless (Playwright)

Suíte de ponta a ponta da **versão serverless** do Grade Checker: a SPA (`applications/serverless/spa`) e a API (`applications/serverless/lambda`). A suíte de `applications/monolith/e2e` continua dedicada ao monolith (versão Jetty). As duas nasceram iguais e podem divergir.

A suíte usa o **Google Chrome instalado na máquina**, então não precisa baixar navegadores.

## Pré-requisitos

- Node 18+ e Docker rodando.
- Google Chrome.
- Para os testes autenticados: [GitHub CLI](https://cli.github.com) logado (`gh auth login`), ou um token em `E2E_GITHUB_TOKEN`.

## Como rodar

```bash
cd applications/serverless/e2e
npm install
npm run test:local      # como administrador (o usuário do `gh` vira GRADE_CHECKER_ADMINS)
npm run test:student    # sem administrador: testes de 403 e telas sem os links de administrador (veja "Execução como aluno")
npm run test:headed     # como administrador, com o navegador visível
npm run test:ui         # modo interativo do Playwright
npm run report          # abre o relatório HTML da última execução
npm run test:running    # contra um ambiente já no ar (padrão: http://localhost:5173, o Docker Compose da pasta serverless)
```

## Ambiente da suíte

Por padrão, a suíte **sobe o próprio ambiente** com o Docker Compose da pasta `serverless`, num **projeto separado**:

| | Ambiente da suíte | Seu ambiente (`docker compose up`) |
|---|---|---|
| Projeto do Compose | `grade-checker-e2e` | `grade-checker` |
| SPA | http://localhost:5183 | http://localhost:5173 |
| Gateway / DynamoDB | 3010 / 8010 | 3000 / 8000 |
| Banco | criado para a execução e **apagado no fim** (`down -v`) | volume `dynamodb-data`, mantido |
| GitHub App | client id falso (`Iv1.e2e-test-client`), sem secret | o do `serverless/.env` |

Por isso a suíte pode rodar com o seu ambiente no ar, sem tocar nos seus dados.

- **Início** (`support/compose-up.ts`): `docker compose -p grade-checker-e2e up --build -d`, depois espera `/api/me` responder. A primeira execução compila as imagens e leva alguns minutos.
- **Fim** (`support/compose-down.ts`): `docker compose -p grade-checker-e2e down -v`. Com `E2E_KEEP=1`, o ambiente continua no ar, o que ajuda a investigar uma falha em http://localhost:5183.
- **Contra um ambiente já no ar:** com `E2E_BASE_URL` (ou `npm run test:running`), a suíte não sobe nem apaga nada. Atenção: os testes cadastram e removem membros, associações e um grupo de teste nesse ambiente, e arquivam o grupo de teste no fim.

### Execução como aluno

O banco da suíte nasce vazio, e só um administrador marca alunos. Sem alunos, os testes de notas não teriam o que conferir. Por isso, `npm run test:student` (`E2E_STUDENT=1`) faz assim:
1. sobe o ambiente com o usuário do `gh` como administrador;
2. marca os contribuidores dos grupos de teste como membros, pela API;
3. recria só a Lambda sem administradores (os dados ficam no banco);
4. roda os testes como usuário comum.

## Variáveis

| Variável | Padrão | Uso |
|---|---|---|
| `E2E_GITHUB_TOKEN` | — | Token do login por personal access token. Sem ele, os testes autenticados são pulados. |
| `E2E_ADMIN_LOGIN` | vazio (`test:local` usa `gh api user -q .login`) | `GRADE_CHECKER_ADMINS` do ambiente da suíte. Com o usuário do token como administrador, rodam os testes de administrador; sem ele, os de usuário comum. |
| `E2E_BASE_URL` | — | Roda contra um ambiente já no ar, sem subir outro. |
| `E2E_KEEP` | — | `1`: não apaga o ambiente da suíte no fim. |
| `E2E_STUDENT` | — | `1`: execução como aluno (veja acima). `test:student` já define. |
| `E2E_GITHUB_CLIENT_ID` | `Iv1.e2e-test-client` | `GITHUB_CLIENT_ID` do ambiente (conferido no redirecionamento de `/api/auth/github`). |
| `E2E_GROUP_ID` | o id de `clinica` | Grupo dos testes do dashboard; tem commits nos três marcos de 2026-2. |
| `E2E_ALIAS_GROUP_ID` | o id de `cannamed` | Grupo com commits sem conta, usado nos testes de associação manual. |
| `E2E_BROWSER_CHANNEL` | `chrome` | `msedge`, ou `chromium` (este exige `npx playwright install chromium`). |

## Preparação dos dados (`tests/auth.setup.ts`)

Depois do login, o setup usa a API:
- confere que os grupos `clinica` e `cannamed` existem. O seed os cria; se faltarem, o administrador os cadastra, ou restaura um arquivado;
- grava os ids em `.auth/e2e-data.json`;
- como administrador, marca como membros os contribuidores desses repositórios, menos bots e `professor-enoqueleal`.

## Diferenças em relação à suíte Jetty

| Na suíte Jetty | Aqui |
|---|---|
| Sobe `mvn jetty:run` com H2 isolado | Sobe o Docker Compose num projeto isolado |
| Rotas `/groups/api`, `/group-members`, `/identity-aliases`, `/contributors-stats` com formulário | Contrato `/api/*` com JSON (`applications/serverless/spa/README.md`) |
| Páginas recarregam depois de cada alteração | Os testes esperam a resposta da API, porque a SPA recarrega só os dados |
| Status 403/404 das páginas | A SPA responde 200 em toda rota: os testes conferem a API e a mensagem da tela |
| CSV de `/grades.csv` | CSV lido do arquivo baixado (gerado no navegador) |
| `GET /logout`, `/login/github`, `/callback` | `POST /api/auth/logout`, `/api/auth/github`, `/api/auth/callback` |
| API administrativa de cache, estáticos `/css` e `/webjars` | Não existem: conferido que `/api/admin/cache` dá 404 e que os arquivos do build são públicos |
| Erro devolvido pelo GitHub no callback aparece na tela | Mensagem genérica "GitHub sign-in failed. Please try again.", sem reproduzir o texto da URL |

## O que é coberto

| Arquivo | Cobertura |
|---|---|
| `tests/public/login.spec.ts` | GitHub como opção principal, token como secundária, tema claro/escuro, centralização, UTF-8 |
| `tests/public/github-sign-in.spec.ts` | Botão aponta para `/api/auth/github`, sem `client_id` na página, 302 para o GitHub com o `client_id` do ambiente, `state` aleatório e cookie `__Host-gc_oauth`, callback forjado recusado, erro do GitHub com mensagem genérica |
| `tests/public/sign-out.spec.ts` | `POST /api/auth/logout` encerra a sessão; "Sign out" da navbar com alerta de sucesso e sem navbar no login; mensagens de erro em vermelho |
| `tests/public/token-sign-in.spec.ts` | Token em branco ou ausente: 400 com mensagem; token válido: 200 só com login e `isAdmin`, cookie `__Host-gc_session` HttpOnly; token recusado: 401 sem sessão; sem `X-Requested-With`: 403; formulário aberto e com foco depois de erro |
| `tests/public/route-protection.spec.ts` | Páginas protegidas levam ao login com "Please sign in to continue.", a API responde 401 JSON sem sessão, a API administrativa de cache não existe, arquivos do build públicos e 404 para os inexistentes |
| `tests/authenticated/dashboard.spec.ts` | Carga automática ao abrir (All), sem botão Load Dashboard, botões de período acima da tabela e dos gráficos, tabela igual à resposta em **cada** período, Custom em horário local, gráficos iguais à resposta e casos simulados (marco sem nota, "(in progress)", período sem linhas, grupo sem alunos) |
| `tests/authenticated/layout.spec.ts` | Cards sem sobreposição e sem rolagem horizontal (home e detalhe em 360/576/800/1000/1280px), rodapés alinhados, avatar quadrado, `alt` das imagens, gráficos com a largura da página |
| `tests/authenticated/grades.spec.ts` | Notas por marco iguais à resposta, M1/M2/M3/Total no All, "(in progress)", sem notas no Custom |
| `tests/authenticated/class-gradebook.spec.ts` | Notas da turma: link na navbar, notas iguais às do detalhe (All), ordenação, filtro, CSV pt-BR baixado; não administrador recebe 403 da API e a mensagem na tela |
| `tests/authenticated/group-management.spec.ts` | Grupos: link na navbar, tabela com "Edit"/"Archive", `?add=1`, 360px; cadastro com a grafia do GitHub, erros do formulário, edição mantendo os membros, arquivar e restaurar; CSRF; não administrador |
| `tests/authenticated/group-members.spec.ts` | Cards e contadores, Mark/Unmark, tela "Manage members", cadastro, edição de login (a associação segue o login), remoção bloqueada por associação; CSRF; não administrador |
| `tests/authenticated/grading-explanation.spec.ts` | "Como as notas são calculadas" em português no fim do detalhe, com os números de `lambda/src/main/resources/grading.properties` e `milestones.properties` |
| `tests/authenticated/navigation-bar.spec.ts` | Navbar nas 6 páginas, links de administrador com o item atual marcado, login e "Sign out", breadcrumb abaixo, Bootstrap sem cópias extras, menu do celular que abre e fecha sem erros |
| `tests/authenticated/identity-aliases.spec.ts` | Associação manual: CSRF e não administrador recusados, botões só para o administrador, associar, persistir e desfazer |
| `tests/authenticated/contributions-detail.spec.ts` | Histórico: All por padrão, marco na URL, Custom, total igual às linhas e à coluna Commits do dashboard, Identity e Also counted, associação manual, mensagem numa linha, hash curto, celular, gráfico de frequência |

Os testes autenticados consultam o GitHub de verdade. As asserções comparam a tela com a resposta da própria API, sem números fixos.
