## Context

- **Origem do código:** a versão Jetty (raiz, bytecode Java 11) e a versão serverless (`serverless/backend`, Java 21) têm cópias do mesmo núcleo. São 26 arquivos idênticos e 6 diferentes: `GitHubProxy`, `GroupGradesService`, `ContributorsService`, `CacheManager`, `AdminConfig` e `GitHubOAuthConfig`. Os detalhes estão em `core/plan.md`, seção 1.
- **Equivalência já provada:**
  - a leitura única do histórico (versão serverless) dá as mesmas notas que as três leituras (versão Jetty), conferido pelo `GroupGradesSingleHistoryTest` e pela comparação com dados reais de 2026-11-08;
  - as duas suítes E2E passam.
- **Decisões do professor (2026-11-08):**
  - registro do artefato: **GitHub Packages**;
  - pacotes **renomeados para `br.com.gradechecker.core.*`**;
  - **as duas versões** adotam a library;
  - primeira change = **fases 1 a 3** do plano. Portas, invariantes e casos de uso ficam para a change seguinte.
- **Builds de hoje:**
  - **CI:** `.github/workflows/maven.yml` compila a versão Jetty no push.
  - **Dockerfile da raiz:** compila a versão Jetty a partir da árvore local.
  - **EC2:** o CloudFormation faz `git clone` e compila na instância com o `mvnw`.
  - **Serverless:** o `sam build` usa o Maven da máquina, e o `serverless/backend/Dockerfile` (Compose) compila no Docker.

## Goals / Non-Goals

**Goals:**
- uma única fonte do núcleo, versionada e publicada;
- as duas aplicações só com o que é particular de cada uma;
- nenhuma mudança de comportamento;
- todos os builds funcionando com o artefato publicado.

**Non-Goals (change seguinte, fase 4 de `core/plan.md`):**
- portas de persistência (`Groups`, `Members`, `IdentityLinks`);
- invariantes de grupos e membros no domínio;
- casos de uso compartilhados (`ContributionsQuery`, `Membership`...);
- erros do domínio com código;
- portas do GitHub (`CommitHistorySource`...);
- a regra de fronteira completa, com o domínio sem depender do pacote `github`;
- renomear `ContributorStatsResponseBody` e tirar `Group.contributorsError` do modelo.

## Decisions

### 1. Projeto e identidade
- **Pasta:** `core/` na raiz, projeto Maven independente, com `pom.xml` próprio que não herda das aplicações.
- **Identidade:** `br.com.gradechecker:grade-checker-core`, jar, `maven.compiler.release=11`, UTF-8.
- **Dependências de compilação:** Unirest e Gson, nas mesmas versões de hoje, ambas só para o pacote `github`. Testes com JUnit 4, Mockito, WireMock e system-rules, as mesmas bibliotecas dos testes copiados.
- **Plugins:** `maven-source-plugin` (jar de fontes) e `maven-surefire-plugin`.
- **Publicação:** `distributionManagement` apontando para `https://maven.pkg.github.com/professor-enoqueleal/grade-checker` (id `github`).

### 2. Pacotes

| Pacote | Classes |
|---|---|
| `core.grading` | `GradeCalculator`, `GradingConfig`, `GradingExplanation` |
| `core.period` | `Milestones`, `PeriodResolver` |
| `core.attribution` | `AuthorAggregator`, `AttributedCommit`, `AttributedHistory`, `AttributionSource` |
| `core.history` | `HistoryCommit`, `RepositoryHistory`, `ContributionsReport`, `ContributorStatsResponseBody`, `CommitActivity`, `CommitRequestBody` |
| `core.group` | `Group`, `Member`, `GroupRepositoryUrl` |
| `core.view` | `GroupCard`, `CommitRow` (dados prontos para a tela, iguais nas duas versões) |
| `core.application` | `ContributorsService`, `GroupGradesService` |
| `core.github` | `GitHubProxy`, `GitHubUserProxy`, `AuthenticationProxy`, `GitHubApiException`, `GitHubRepository`, `Contributor`, `AccessToken`, `GitHubOAuthSettings` (novo) |
| `core.port` | `ResultCache` (novo) |

**Ficam nas aplicações:**
- `CacheManager` e o cache no DynamoDB, que implementam `ResultCache`;
- `AdminConfig`;
- `GitHubOAuthConfig`, que monta o `GitHubOAuthSettings` a partir das variáveis (e do SSM, na versão serverless);
- os `.properties`, servlets, rotas, repositórios e stores.

### 3. Qual versão vale nos 6 arquivos que divergiram

| Arquivo | Na library |
|---|---|
| `GroupGradesService` | versão serverless: leitura única com fallback, com o mesmo resultado |
| `GitHubProxy` | versão serverless (`reportFromHistory`, contribuidores tipados, sem `getCommitDetail`), com o cache pela porta `ResultCache` |
| `ContributorsService` | versão serverless, sem construtor que dependa de repositório |
| `CacheManager` | fica nas aplicações: a Jetty mantém o dela (Caffeine); a serverless mantém o dela (Caffeine + segunda camada) |
| `AdminConfig` | fica nas aplicações (a Jetty usa `HttpSession`) |
| `GitHubOAuthConfig` | fica nas aplicações; os proxies passam a receber `GitHubOAuthSettings` (client id, segredo por `Supplier`, URLs) |

### 4. Ajustes de pureza desta change
- **Logs:** `org.apache.logging.log4j` sai e entra `java.lang.System.Logger`. As duas aplicações adicionam `org.apache.logging.log4j:log4j-jpl`, que leva esses logs ao Log4j 2 que elas já usam.
- **Cache:** o `GitHubProxy` recebe um `ResultCache` (padrão: sem cache) no lugar do `CacheManager` estático. As chaves e o que entra no cache não mudam. A implementação da serverless continua guardando os campos `transient` no DynamoDB.
- **OAuth:** `AuthenticationProxy` e `GitHubUserProxy` recebem `GitHubOAuthSettings` no lugar da leitura estática de variáveis.
- **Gson:**
  - saem as anotações `@SerializedName` do `ContributorStatsResponseBody` (os nomes são iguais aos dos campos, então nada muda);
  - os DTOs do GitHub, que precisam delas para ler o JSON do GitHub, ficam no pacote `github`.
- **Configuração:** `GradingConfig.fromClasspath()` e `Milestones.fromClasspath()` continuam lendo `/grading.properties` e `/milestones.properties`, agora pelo carregador de contexto da thread, com o carregador da library como reserva. Os arquivos continuam em cada aplicação: no WAR da versão Jetty, `WEB-INF/classes` e `WEB-INF/lib` estão no mesmo carregador; na Lambda, o classpath é único.

### 5. Regra de fronteira desta change
Um teste de arquitetura lê os fontes da library e falha quando:
- **qualquer arquivo** importa `jakarta.*`, `javax.servlet.*`, `java.sql.*`, `software.amazon.*`, `com.github.benmanes.*` ou `org.apache.logging.*`;
- **um arquivo fora de `core.github`** importa `kong.unirest.*` ou `com.google.gson.*`.

A regra completa, com o domínio sem depender do pacote `github`, entra na change seguinte, junto com as portas do GitHub.

### 6. Versionamento e publicação
- **Versionamento semântico,** registrado no `CHANGELOG.md`. A primeira versão publicada é `1.0.0`; em desenvolvimento, `x.y.z-SNAPSHOT`.
- **Mudança de nota:** qualquer mudança que altere o valor das notas sobe pelo menos a versão minor.
- **Workflow `.github/workflows/core-release.yml` ("Release core library"),** disparado só manualmente (`workflow_dispatch`) com a entrada `version`, no mesmo modelo do "Release vanilla version" da aplicação. A publicação é uma decisão explícita, e não um efeito colateral de empurrar uma tag:
  - só roda a partir do `main`; recusa uma versão fora de `X.Y.Z`, diferente da do `core/pom.xml` ou cuja tag `core-vX.Y.Z` já existe;
  - roda `mvn -f core/pom.xml verify` e depois `deploy -DskipTests` com o `GITHUB_TOKEN` (`permissions: packages: write`);
  - só depois da publicação cria a tag `core-vX.Y.Z` no commit publicado e a Release, com o jar e o jar de fontes, sem marcá-la como *Latest* (a Release mais recente do repositório continua sendo a da aplicação).
  - Se a criação da Release falhar depois do `deploy`, o pacote já está publicado: a tag e a Release são criadas à mão no mesmo commit, e a versão não é republicada.
- **Imutabilidade:** uma versão publicada não muda; uma correção vira patch.

### 7. Como cada build obtém a library (decidido na implementação)

A library está no mesmo repositório das aplicações. Por isso, **todo build que parte do repositório compila a library a partir de `core/` antes da aplicação** e não precisa de token enquanto a versão pedida no pom da aplicação for a versão de `core/pom.xml`. O GitHub Packages só é lido quando a aplicação pede outra versão; aí entra o token. O segredo no Secrets Manager da EC2, previsto no início, deixou de ser necessário.

| Build | Como |
|---|---|
| Desenvolvimento | `mvn -f core/pom.xml install` coloca a library no `~/.m2`. Para uma versão publicada que não está em `core/`, use o `~/.m2/settings.xml` com um token `read:packages` |
| CI (`maven.yml`) | `mvn -f core/pom.xml install` e depois o build da versão Jetty. Para outra versão, `actions/setup-java` com `server-id: github` e o `GITHUB_TOKEN` (`permissions: packages: read`) |
| `Dockerfile` da raiz | `COPY core/pom.xml` e `core/src`, `mvnw -f core/pom.xml install`, depois o WAR. Secret opcional `maven_settings` (`--mount=type=secret,...,required=false`) para outra versão |
| `serverless/backend/Dockerfile` (Compose) | contexto de build adicional `core` (`additional_contexts: core: ../core`), com a library instalada dentro da imagem. Secret `maven_settings` opcional, que aponta por padrão para um `settings.xml` vazio (`MAVEN_SETTINGS` troca o arquivo) |
| EC2 | não compila: baixa o `grade-checker.war` da Release do GitHub (change `ec2-release-war`, no OpenSpec da raiz; substituiu o build de `core/` no `git clone`) |
| `sam build` | Maven da máquina, com a library instalada no `~/.m2` (ou o `settings.xml` com token) |

O repositório `github` dos poms das aplicações tem `<snapshots><enabled>false</enabled></snapshots>`: versões `-SNAPSHOT` nunca são buscadas no GitHub Packages, só no `~/.m2`.

### 8. Repositório nos poms das aplicações
- **Dependência:** as duas aplicações declaram `grade-checker-core` com a versão fixa (`1.0.0`) e o `<repository>` `github`.
- **Desenvolvimento antes da publicação:** com `1.0.0-SNAPSHOT` instalada localmente, o Maven encontra a library no `~/.m2` sem token.

### 9. Testes
- **Na library:** os testes do núcleo que hoje existem nas duas versões, uma vez só, e o `GroupGradesSingleHistoryTest`.
  - Os testes que usavam o `CacheManager` passam a usar uma implementação de `ResultCache` em memória.
  - O teste de arquitetura da seção 5.
- **Nas aplicações:** continuam os testes do que é particular, como `AdminConfigTest`, `GitHubOAuthConfigTest`, servlets, rotas, stores e o cache.
- **Aceite:**
  - `mvn test` nas duas aplicações;
  - integração da serverless (`-Pintegration`);
  - as duas suítes E2E, de administrador e de aluno;
  - uma nova comparação das notas Jetty × serverless, sem diferenças.

## Risks / Trade-offs

- **Token:** o GitHub Packages exige token mesmo com o repositório público, mas os builds do repositório compilam a library de `core/` e só precisam do token para uma versão diferente da de `core/`.
- **Mudança de comportamento na versão Jetty:** ela passa a ler o histórico uma vez por grupo para as notas de All. O resultado é o mesmo, provado; e, se o histórico vier truncado, o cálculo volta às três leituras.
- **Classes duplicadas durante a transição:** na mesma tarefa em que cada aplicação passa a depender da library, as cópias são apagadas e os imports trocados, para nunca haver duas classes com o mesmo papel.
- **Java 11 na library:** o código vindo da serverless precisa compilar com `--release 11`. Ele não usa nada além do Java 11 (conferir no build).
- **EC2:** depois da change `ec2-release-war` (OpenSpec da raiz), a instância não compila nada: usa o WAR da Release. Não precisa de token nem de segredo novo.
