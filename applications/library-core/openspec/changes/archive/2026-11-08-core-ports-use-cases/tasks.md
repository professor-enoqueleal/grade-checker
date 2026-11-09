## 1. Portas e erros

- [x] 1.1 `core.port`: `GroupRepository`, `MemberRepository` (com `RenameResult`), `IdentityAliasRepository`, `RepositoryTakenException`, `GitHubDirectory`; `GitHubProxy implements GitHubDirectory`
- [x] 1.2 `core.error.DomainException` (tipo, código, mensagem)

## 2. Casos de uso

- [x] 2.1 `GroupManagement` (com o detalhe e os cartões)
- [x] 2.2 `Membership` e `IdentityLinking`
- [x] 2.3 `ContributionsQuery`, `CommitHistoryQuery` e `ClassGrades`
- [x] 2.4 Testes dos casos de uso com portas em memória; regra nova no `ArchitectureTest` (portas e erros só com a JDK e a library)

## 3. Fechamento

- [x] 3.1 `mvn -f core/pom.xml install`; a versão Jetty continua compilando e passando (`mvnw test`)
- [x] 3.2 Adoção pela versão serverless (change `backend-hexagonal`), com `ApiIT` e E2E verdes
- [x] 3.3 `CHANGELOG.md`, `core/README.md` e `core/plan.md` (andamento); validar e arquivar
