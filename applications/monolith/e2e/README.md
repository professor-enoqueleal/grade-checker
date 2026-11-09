# Testes E2E (Playwright)

Suíte de ponta a ponta do Grade Checker, com [Playwright Test](https://playwright.dev). Ela usa o **Google Chrome instalado na máquina**, então não precisa baixar navegadores.

## Pré-requisitos

- Node 18+ e Maven (o mesmo usado para rodar a aplicação).
- Google Chrome.
- Para os testes autenticados: [GitHub CLI](https://cli.github.com) logado (`gh auth login`), ou um token em `E2E_GITHUB_TOKEN`.

## Como rodar

```bash
cd e2e
npm install
npm run test:local      # usa `gh auth token` para os testes autenticados
npm run test:headed     # mesmo, com o navegador visível
npm run test:ui         # modo interativo do Playwright
npm run report          # abre o relatório HTML da última execução
npm run test:docker     # contra o container Docker em http://localhost:8080 (veja o readme.md da raiz)
```

Por padrão, a suíte **sobe a própria instância** (`mvn jetty:run` na porta `8099`) com um **banco H2 isolado** em `e2e/.tmp`. Ela não usa nem altera o banco de desenvolvimento (`~/h2db`) e pode rodar com a sua instância do IntelliJ ligada na 8080. No fim, `support/stop-server.ts` (`globalTeardown`) para essa instância com SIGTERM antes de o Playwright encerrá-la à força, para o banco ser fechado e as últimas escritas dos testes serem gravadas.

## Variáveis

| Variável | Padrão | Uso |
|---|---|---|
| `E2E_GITHUB_TOKEN` | — | Token usado no login por personal access token. Sem ele, os testes autenticados são pulados. |
| `E2E_ADMIN_LOGIN` | vazio (`test:local` usa `gh api user -q .login`) | `GRADE_CHECKER_ADMINS` passado à instância que a suíte sobe. Com o usuário do token como administrador, roda o teste de associar/desfazer; sem ele, o de usuário comum. Contra o Docker, vale o `GRADE_CHECKER_ADMINS` do `.env.local`. |
| `E2E_ALIAS_GROUP_ID` | `6` (cannamed) | Grupo com commits sem conta usado nos testes de associação manual. |
| `E2E_GITHUB_CLIENT_ID` | `Iv1.e2e-test-client` | `GITHUB_CLIENT_ID` passado à instância que a suíte sobe (conferido no redirecionamento de `/login/github`). |
| `E2E_GROUP_ID` | `3` (clinica) | Grupo usado nos testes do dashboard; tem commits nos três marcos de 2026-2. |
| `E2E_BASE_URL` | — | Roda contra uma instância já no ar (ex.: `http://localhost:8080`), sem subir outra. |
| `E2E_PORT` | `8099` | Porta da instância que a suíte sobe. |
| `E2E_BROWSER_CHANNEL` | `chrome` | `msedge`, ou `chromium` (este exige `npx playwright install chromium`). |

## Preparação dos dados (`tests/auth.setup.ts`)

Depois do login, o setup prepara o banco isolado, que pode vir sem grupos (o `db.sql` pode trazer só o schema) e sempre começa sem membros:
- cria os grupos `clinica` e `cannamed` quando não existem (pela API `/groups/api`, como administrador; um grupo arquivado é restaurado) e grava os ids em `.auth/e2e-data.json`, usado como padrão de `E2E_GROUP_ID` e `E2E_ALIAS_GROUP_ID`;
- como administrador, marca como membros os contribuidores atuais desses repositórios, menos bots e `professor-enoqueleal`. Sem administrador, os grupos ficam sem membros e os testes que dependem de alunos usam o que já estiver no banco.

## O que é coberto

| Arquivo | Cobertura |
|---|---|
| `tests/public/login.spec.ts` | GitHub como opção principal, token como secundária, tema claro/escuro, centralização, UTF-8 |
| `tests/public/github-sign-in.spec.ts` | Botão aponta para `/login/github`, sem `client_id` no HTML, 302 para o GitHub com `client_id` do ambiente e `state` aleatório, callback forjado rejeitado, erro do GitHub exibido |
| `tests/public/sign-out.spec.ts` | Logout com alerta de sucesso (sessão própria) e sessão realmente encerrada, inclusive pelo "Sign out" da navbar (sem navbar na tela de login); mensagens de erro continuam em vermelho |
| `tests/public/token-sign-in.spec.ts` | Login por token sem valor (espaços ou parâmetro ausente) responde 400 com mensagem, nunca 500; token rejeitado pelo GitHub responde 401 sem criar sessão; formulário reaberto e com foco |
| `tests/public/route-protection.spec.ts` | Rotas protegidas, 401 JSON para AJAX, `/admin/cache` fora do login, estáticos públicos |
| `tests/authenticated/dashboard.spec.ts` | Carga automática ao abrir (All), sem botão Load Dashboard, botões de período acima da tabela e do gráfico, tabela igual à resposta em **cada** período, Custom em horário local; gráficos de barras iguais à resposta em cada período (notas só de alunos, escala 0–9/0–3, mesma cor do marco no All e no marco, nenhuma cor repetida, volume de todos os autores, só volume no Custom) e casos simulados (marco sem nota, "(in progress)", período sem linhas, grupo sem alunos) |
| `tests/authenticated/layout.spec.ts` | Cards sem sobreposição e sem rolagem horizontal (home 1/2/3 e detail 1/2/3/4 colunas em 360/800/1000/1280px), rodapés alinhados e links do rodapé numa linha só (inclusive 576px), avatar quadrado `card-img-top` compacto, `alt` das imagens; gráficos de barras com a largura da página |
| `tests/authenticated/grades.spec.ts` | Notas por marco: coluna "Grade (0–3)" igual à resposta, M1/M2/M3/Total no All (Total = soma, ≤ 9), "(in progress)", sem notas no Custom |
| `tests/authenticated/class-gradebook.spec.ts` | Notas da turma: administrador abre `/grades` pelo link da home, as notas do grupo `E2E_GROUP_ID` são iguais às de `/detail` (All), "(in progress)", ordenação pelo Total, filtro, download do CSV no formato pt-BR; não administrador recebe 403 em `/grades`, `/grades/group` e `/grades.csv`, não vê o link e continua vendo as notas em `/detail` (pulado conforme o perfil) |
| `tests/authenticated/group-management.spec.ts` | Grupos: "Manage groups" na linha do breadcrumb da home só para o administrador; tela com "Edit"/"Archive" (hyperlinks), `?add=1` abre o cadastro, cabe em 360px; cadastro com a grafia do GitHub, erros no formulário (URL inválida, repositório inexistente, já cadastrado, de grupo arquivado), edição de nome e URL mantendo os membros (com aviso), arquivar (some da home e de `/grades`, `/detail` 404) e restaurar; API sem `X-Requested-With` (403), rota antiga `/group/create` (404), não administrador (403). Usa `octocat/Hello-World` e `octocat/Spoon-Knife` e deixa o grupo de teste arquivado |
| `tests/authenticated/group-members.spec.ts` | Membros: cards "Contributors and members" (sem seção separada), contadores, selo "Member" e links do card (só hyperlinks; "Mark"/"Unmark" com a dica do texto completo), administrador desmarca/marca pelo card; tela "Manage members" (link em `/detail` no lugar de "Add a new member", breadcrumb, tabela ordenada com "Edit"/"Remove"), cadastro (membro sem commits vira card com "Contributions: 0" e selo, soma só em members e aparece zerado no dashboard; login duplicado e inexistente recusados), edição de nome e login (a associação manual segue o login novo), remoção pela tabela bloqueada por associação manual e liberada sem ela; API sem `X-Requested-With` e de não administrador (403 em POST/PUT e na tela) |
| `tests/authenticated/grading-explanation.spec.ts` | Caixa "Como as notas são calculadas" no fim do `/detail`, em português (`lang="pt-BR"`), com as datas dos marcos, os pesos, o teto por commit, o mínimo de linhas e o exemplo com 4 alunos lidos de `grading.properties` e `milestones.properties`; o resto da página continua em inglês |
| `tests/authenticated/navigation-bar.spec.ts` | Navbar nas 6 páginas autenticadas (mesma marca com link para a home, sem item "Home", links de administrador só para ele com o item atual marcado, login e "Sign out", breadcrumb abaixo, Bootstrap JS uma vez), marca leva à home, menu recolhido no celular (360px) |
| `tests/authenticated/identity-aliases.spec.ts` | Associação manual: API recusa chamadas sem `X-Requested-With` e de não administradores; não administrador não vê os botões; administrador associa ("Link to student"), a associação persiste no recarregamento e "Unlink" desfaz |
| `tests/authenticated/contributions-detail.spec.ts` | All por padrão, marco na URL e nos campos Custom, Custom aplicado, total = linhas, links só para GitHub; total igual à coluna Commits do dashboard para cada login (All e Milestone 2); coluna Identity e linha Also counted; associação manual com o selo "manual"; mensagem numa linha com reticências e `title` completo, hash de 7 caracteres, data em até duas linhas e Identity abaixo da mensagem no celular, sem rolagem horizontal (360 e 1280px); gráfico de frequência: soma das colunas igual ao Total of commits em All e nos marcos, por dia ou por semana conforme o intervalo, dias ativos iguais às datas distintas da tabela, cores dos marcos, sem gráfico sem commits, cabe em 360px |

Os testes autenticados consultam o GitHub de verdade. As asserções comparam a tela com a resposta da própria aplicação, sem números fixos, então continuam válidas quando os alunos fazem novos commits.
