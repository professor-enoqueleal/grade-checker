# Changelog — grade-checker-core

Versionamento semântico:
- **patch:** correção sem mudança de regra;
- **minor:** regra nova compatível. Qualquer mudança no **valor das notas** sobe pelo menos a minor;
- **major:** mudança incompatível da API ou da regra.

## 1.0.0 (pronta para publicar pelo workflow "Release core library")

Primeira versão: o núcleo que as versões Jetty e serverless tinham copiado, num artefato só (change `core-library`).

- **Pacotes `br.com.gradechecker.core.*`:** `application`, `port`, `error`, `group`, `github`, `history`, `attribution`, `grading`, `period`, `view` e `adapter.github`.
- **Notas de All:** uma leitura do histórico por grupo, fatiada por marco, com o mesmo resultado das três leituras. Se o histórico vier truncado, o cálculo volta às três.
- **Cache:** porta `ResultCache`, implementada por cada aplicação, no lugar do `CacheManager` estático.
- **Login com GitHub:** `GitHubOAuthSettings` no lugar da leitura estática de variáveis.
- **Logs:** pelo `System.Logger`; as aplicações os levam ao Log4j com `log4j-jpl`.
- **Configuração:** `grading.properties` e `milestones.properties` continuam em cada aplicação e são lidos pelo carregador de contexto.
- **Regras de nota e de atribuição:** as mesmas das versões anteriores.
- **Portas e casos de uso** (change `core-ports-use-cases`, entrou antes da primeira publicação):
  - portas `GroupRepository`, `MemberRepository`, `IdentityAliasRepository` e a porta do GitHub;
  - casos de uso `GroupManagement`, `Membership`, `IdentityLinking`, `ContributionsQuery`, `CommitHistoryQuery` e o das notas da turma;
  - erros `DomainException`, com código estável e as mesmas mensagens da API;
  - `CommitHistoryQuery.period(...)`: o período mostrado na página de histórico, com o aviso quando o período pedido é inválido.
  - Tudo é adicionado, sem quebrar quem usa só as classes anteriores.
- **Nomes e organização** (change `core-naming`, entrou antes da primeira publicação; quebra a API das versões de desenvolvimento):
  - **cliente do GitHub:** `GitHubClient` (era `GitHubProxy`) e `GitHubOAuthClient` (junta `AuthenticationProxy` e `GitHubUserProxy`, com `exchangeCodeForToken` e `fetchLogin`), no pacote `core.adapter.github`, o único com Unirest e Gson;
  - **porta `GitHubGateway`** (era `GitHubDirectory`), com `getHistory`. Os casos de uso dependem só dela, e `getContributors` passa a receber `(owner, repo, accessToken)`;
  - **relatório e atribuição no domínio:** `attribution.ContributionAnalyzer` calcula a partir do histórico. O cliente só lê e guarda em cache, e o relatório deixa de ter cache próprio, porque o histórico continua em cache;
  - **renomeados:**
    - `HistoryRequest` (era `CommitRequestBody`) e `ContributorStats` (era `ContributorStatsResponseBody`, sem `transient`);
    - `ContributionsReport.getStats`;
    - `RepositoryUrl`;
    - `ClassGradesQuery.grades`, `CommitHistoryQuery.history`, `GroupManagement.activeGroups`/`archivedGroups`;
    - `MilestoneGrades` (era `GroupGradesService`), sem o modo de três leituras, e `GroupGrades.student(login)`;
    - `GradeCalculator.indicators` e `Result.student(login)`;
    - `MemberRepository.findLoginsByGroupId`;
  - **removidos:**
    - `ContributorsService`;
    - os campos de tela do `Group` (`contributors`, `contributorsError`, `members`). `totalOfMembers` vira `memberCount`;
    - o campo morto `Contributor.author`;
  - **`Member`:** `login`, e `groupId` como long;
  - **`ArchitectureTest`:** nenhum ciclo entre pacotes, e casos de uso sem dependência de adaptador;
  - **correção:** identidades sem conta com o mesmo nome no git agora somam na contagem de commits deixados de fora das notas. Antes, a última sobrescrevia as outras. O valor das notas não muda.
