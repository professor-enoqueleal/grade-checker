## Why

A revisão da library depois da fase 4 mostrou nomes e divisões que não refletem mais o que o código faz:
- **Serviços antigos ao lado dos casos de uso:** `ContributorsService` só repassa chamadas ao cliente do GitHub. `GroupGradesService` mantém um modo (`singleHistory=false`) que só os testes usam.
- **Nomes de DTO HTTP no domínio:** `CommitRequestBody` e `ContributorStatsResponseBody`.
- **Clientes chamados de "proxy":** `GitHubProxy`, `AuthenticationProxy` e `GitHubUserProxy`. O método `exchangeCodePerToken` tem erro de inglês.
- **Cliente do GitHub com regra de domínio:** o `GitHubProxy` monta o relatório e a atribuição, além de chamar a API.
- **Ciclo de dependência:** `port` → `github` → `port`.
- **Gson e `transient` no domínio**, contra a regra 3 do item 8 do `plan.md`.
- **Campos de tela no `Group`:** `contributors`, `contributorsError` e `members`.
- **Campo morto em `Contributor`:** `author`.
- **`Member` destoa do resto:** `gitHubUserName` em vez de `login`, e `groupId` como String.
- **Métodos de instância chamados `of`:** `ClassGrades.of`, `CommitHistoryQuery.of` e `GroupGradesService.of`.
- **Nomes de classe por extenso** no meio do código.

A `1.0.0` ainda não foi publicada. Arrumar agora evita que esses nomes virem API pública.

## What Changes

- **BREAKING (API da library, antes da primeira publicação):**
  - **Pacotes:**
    - `core.github` passa a ter só os conceitos do GitHub: `Contributor`, `GitHubRepository` e `GitHubApiException`, sem Gson;
    - o cliente HTTP vai para o novo `core.adapter.github`, único pacote com Unirest e Gson;
    - nele ficam `GitHubClient` (era `GitHubProxy`) e `GitHubOAuthClient` (junta `AuthenticationProxy` e `GitHubUserProxy`, com `exchangeCodeForToken` e `fetchLogin`), além de `GitHubOAuthSettings` e `AccessToken`.
  - **Portas:**
    - `GitHubDirectory` vira `GitHubGateway`, que ganha `getHistory` e passa a ser a única dependência dos casos de uso em relação ao GitHub;
    - em `getContributors`, a ordem dos parâmetros passa a ser `(owner, repo, accessToken)`, igual a `getRepository`;
    - `MemberRepository.findUsernamesByGroupId` vira `findLoginsByGroupId`.
  - **Relatório e atribuição saem do cliente:** a nova classe `attribution.ContributionAnalyzer` monta o relatório, recorta um período e atribui os commits de um histórico. O cliente só lê e guarda em cache o histórico, os contribuidores, o repositório e o usuário. O relatório deixa de ter cache próprio, porque é recalculado em memória a partir do histórico, que continua em cache.
  - **Histórico:**
    - `CommitRequestBody` vira `HistoryRequest` (owner, repository, since, until, accessToken), sem `groupId` e sem autor;
    - `ContributorStatsResponseBody` vira `ContributorStats`, sem `transient`;
    - `ContributionsReport.getConsolidatedStats` vira `getStats`.
  - **Casos de uso:**
    - `ContributorsService` sai;
    - `GroupGradesService` vira `MilestoneGrades`, sem o modo de três leituras, construído pelos próprios casos de uso;
    - `ClassGrades` vira `ClassGradesQuery`, com o método `grades`;
    - `CommitHistoryQuery.of` vira `history`;
    - `GroupManagement.active`/`archived` viram `activeGroups`/`archivedGroups`;
    - `GroupGrades.of` e `GradeCalculator.Result.of` viram `student`;
    - `GroupGradesService.indicators` vira `GradeCalculator.indicators`.
  - **Grupo e membro:**
    - `GroupRepositoryUrl` vira `RepositoryUrl`;
    - `Group` perde `contributors`, `contributorsError` e `members`, e `totalOfMembers` vira `memberCount`;
    - em `Member`, `gitHubUserName` vira `login` e `groupId` passa a ser `long`;
    - `Contributor` perde o campo morto `author`.
  - **Sem nomes por extenso** no código da library.
- **Teste de arquitetura:**
  - Unirest e Gson só em `core.adapter`;
  - nenhum ciclo entre pacotes;
  - `port` e `error` sem dependência de infraestrutura.
- **Correção encontrada na revisão:** identidades sem conta com o mesmo nome no git são linhas diferentes do relatório. Antes, `indicators` usava o nome como chave, e a última sobrescrevia as outras, então faltavam commits na contagem de "deixados de fora das notas". Agora as linhas com o mesmo nome somam, e o valor das notas não muda.
- **Aplicações:** a Jetty e a Lambda passam a usar os nomes novos, sem mudar contrato HTTP, telas ou comportamento.

## Capabilities

### New Capabilities
- `core-structure`: organização dos pacotes da library, regra de dependência sem ciclos e convenções de nomes.

### Modified Capabilities
- `core-ports`: a porta do GitHub vira `GitHubGateway`, com o histórico.
- `core-use-cases`: novos nomes (`ClassGradesQuery`, `MilestoneGrades`), e os casos de uso passam a depender só de portas.

## Impact

- **`core/`:** todos os pacotes, testes, README, CHANGELOG e `plan.md`. A spec `core-boundaries` da change aberta `core-library` passa a citar os nomes novos.
- **Jetty:**
  - `config/AppContext`, os servlets e os testes;
  - `detail.jsp` e `group-members.jsp`, que passam a ler os campos novos;
  - `Jdbc*Repository`.
- **Lambda:** `AppContext`, as rotas, os adapters do DynamoDB e os testes.
- **Cache:** as chaves de contribuidores ganham a versão (`v2`), porque o formato do `Contributor` em JSON muda. A chave do relatório deixa de existir.
