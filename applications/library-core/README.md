# grade-checker-core

Library Maven com o **domínio** do Grade Checker (notas por marco, atribuição de autores, períodos, grupos e membros) e o **cliente do GitHub**. É usada como dependência pelas duas aplicações:
- **monolith** (versão Jetty), em `applications/monolith`;
- **versão serverless**, em `applications/serverless/lambda`.

Cada aplicação fica só com o que é particular dela: HTTP, sessão, persistência, cache e configuração.

- **Plano e decisões:** `plan.md`.
- **Specs:** `openspec/`, com os comandos rodados a partir desta pasta.
- **Histórico de versões:** `CHANGELOG.md`.

## Uso

```xml
<dependency>
  <groupId>br.com.gradechecker</groupId>
  <artifactId>grade-checker-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

A aplicação entrega à library o que é dela:

| O quê | Como |
|---|---|
| Persistência de grupos, membros e associações | implementações de `GroupRepository`, `MemberRepository` e `IdentityAliasRepository` (`core.port`), passadas aos casos de uso |
| Leitura do GitHub | o `GitHubClient` da library (`core.adapter.github`), que implementa a porta `GitHubGateway`, montado com a URL da API e o cache da aplicação |
| Cache das respostas do GitHub | uma implementação de `core.port.ResultCache`, passada ao `GitHubClient` |
| Login com o GitHub App | `GitHubOAuthClient`, montado com `GitHubOAuthSettings.of(baseUrl, apiUrl, clientId, clientSecret)`, com suppliers lidos a cada chamada |
| Marcos e parâmetros da nota | `milestones.properties` e `grading.properties` no classpath da aplicação |
| Logs | `org.apache.logging.log4j:log4j-jpl` no classpath, para os logs da library (`System.Logger`) chegarem ao Log4j 2 |

## Pacotes

| Pacote | Conteúdo |
|---|---|
| `core.application` | **casos de uso:** `GroupManagement`, `Membership` e `IdentityLinking` (alteram estado); `ContributionsQuery`, `CommitHistoryQuery` e `ClassGradesQuery` (só leitura); `MilestoneGrades` (notas por marco, montado pelos casos de uso) |
| `core.port` | **portas:** `GroupRepository`, `MemberRepository`, `IdentityAliasRepository`, `RepositoryTakenException`, `GitHubGateway`, `ResultCache` |
| `core.error` | `DomainException` (tipo `INVALID`, `NOT_FOUND` ou `CONFLICT`, código estável e mensagem) |
| `core.group` | `Group`, `Member`, `RepositoryUrl` |
| `core.github` | o que a porta do GitHub devolve: `Contributor`, `GitHubRepository`, `GitHubApiException` |
| `core.history` | `HistoryRequest`, `RepositoryHistory`, `HistoryCommit`, `ContributionsReport`, `ContributorStats`, `CommitActivity` |
| `core.attribution` | `ContributionAnalyzer` (relatório, recorte de período e atribuição a partir do histórico), `AuthorAggregator`, `AttributedCommit`, `AttributedHistory`, `AttributionSource` |
| `core.grading` | `GradeCalculator`, `GradingConfig`, `GradingExplanation` |
| `core.period` | `Milestones`, `PeriodResolver` |
| `core.view` | `GroupCard`, `CommitRow`: dados prontos para a tela, iguais nas duas aplicações |
| `core.adapter.github` | **adaptador** do GitHub (REST e GraphQL): `GitHubClient`, `GitHubOAuthClient`, `GitHubOAuthSettings`, `AccessToken` |
| `core.internal` | utilitários internos (logs, leitura de recursos); não fazem parte da API |

**Regras de dependência,** conferidas pelo `ArchitectureTest`:
- nada de Jakarta, JDBC, AWS, Caffeine ou Log4j na library;
- Unirest e Gson só em `core.adapter`;
- `core.port` e `core.error` importam só a JDK e a própria library;
- os casos de uso nunca dependem de um adaptador: o GitHub entra pela porta `GitHubGateway`;
- nenhum ciclo entre os pacotes.

**Convenções de nomes:**

| Tipo | Convenção | Exemplos |
|---|---|---|
| Caso de uso que altera estado | substantivo do assunto | `GroupManagement`, `Membership` |
| Caso de uso só de leitura | sufixo `Query` | `ContributionsQuery`, `ClassGradesQuery` |
| Porta de persistência | sufixo `Repository` | `GroupRepository.findActive(id)` |
| Porta de sistema externo | sufixo `Gateway` | `GitHubGateway` |
| Cliente HTTP | sufixo `Client` | `GitHubClient`, `GitHubOAuthClient` |
| Fábrica estática | `of`, `parse`, `from*` | `GradingConfig.of`, `RepositoryUrl.parse` |

- O login do GitHub sempre se chama `login`, e os ids são `long`.

**Como uma aplicação usa:**
1. monta as portas com a sua infraestrutura e o `GitHubClient` com o seu cache;
2. cria os casos de uso;
3. em cada entrada (rota ou servlet), chama o caso de uso e traduz a `DomainException` (400, 404, 409) e a `GitHubApiException` (status do GitHub).

Exemplos: `applications/serverless/lambda/.../lambda/AppContext.java` (Lambda) e `applications/monolith/src/main/java/.../config/AppContext.java` (monolith).

## Build e publicação

Os comandos rodam em `applications/library-core`.

| Comando | O que faz |
|---|---|
| `mvn test` | testes da library (Java 11) |
| `mvn install` | instala no `~/.m2`, para as aplicações usarem a versão local (por exemplo, `1.0.0-SNAPSHOT`) |
| `gh workflow run core-release.yml --ref main -f version=X.Y.Z` | workflow manual "Release core library" (`.github/workflows/core-release.yml`): testa, publica no GitHub Packages e cria a tag `core-vX.Y.Z` e a Release; a versão precisa ser igual à do `pom.xml` |

**Ler do GitHub Packages exige um token com `read:packages`, mesmo com o repositório público.** Localmente, use o `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>SEU_LOGIN</username>
      <password>TOKEN_COM_read:packages</password>   <!-- gh auth refresh -s read:packages; gh auth token -->
    </server>
  </servers>
</settings>
```
