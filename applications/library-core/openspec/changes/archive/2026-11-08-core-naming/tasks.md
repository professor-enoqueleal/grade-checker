## 1. Library: pacotes e domínio

- [x] 1.1 `github` só com `Contributor` (sem `author` e sem Gson), `GitHubRepository` (sem Gson) e `GitHubApiException`
- [x] 1.2 `adapter.github`: `GitHubClient` (era `GitHubProxy`, só I/O e cache), `GitHubOAuthClient` (junta `AuthenticationProxy` e `GitHubUserProxy`; `exchangeCodeForToken`, `fetchLogin`), `GitHubOAuthSettings`, `AccessToken`
- [x] 1.3 `history`: `HistoryRequest`, `ContributorStats` (sem `transient`), `ContributionsReport.getStats`
- [x] 1.4 `attribution.ContributionAnalyzer` com o relatório, o recorte e a atribuição tirados do cliente
- [x] 1.5 `group`: `RepositoryUrl`; `Group` sem campos de tela e com `memberCount`; `Member.login` e `groupId` long
- [x] 1.6 `grading`: `GradeCalculator.indicators` e `Result.student`
- [x] 1.7 Sem nomes de classe por extenso no código

## 2. Library: portas e casos de uso

- [x] 2.1 `port.GitHubGateway` (era `GitHubDirectory`) com `getHistory`; `getContributors(owner, repo, accessToken)`; `MemberRepository.findLoginsByGroupId`
- [x] 2.2 Remover `ContributorsService`; `GroupGradesService` vira `MilestoneGrades` sem o modo de três leituras
- [x] 2.3 `ClassGradesQuery.grades`, `CommitHistoryQuery.history`, `GroupManagement.activeGroups`/`archivedGroups`, `GroupGrades.student`
- [x] 2.4 Casos de uso dependem só de portas (`GitHubGateway`), sem o cliente concreto
- [x] 2.5 `ArchitectureTest`: Unirest e Gson só em `core.adapter`; sem ciclos entre pacotes
- [x] 2.6 Testes da library adaptados e passando; `mvn install`

## 3. Aplicações

- [x] 3.1 Jetty: `AppContext`, servlets, `Jdbc*Repository`, JSPs e testes
- [x] 3.2 Lambda: `AppContext`, rotas, adapters do DynamoDB e testes (`-Pintegration`)
- [x] 3.3 E2E da Jetty e da serverless (administrador e aluno) sem regressão

## 4. Documentação e OpenSpec

- [x] 4.1 README, CHANGELOG e `plan.md` da library; spec `core-boundaries` da change aberta `core-library`; READMEs e specs das aplicações que citam os nomes antigos
- [x] 4.2 Validar e arquivar
