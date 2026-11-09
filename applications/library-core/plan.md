# Análise: isolar o domínio numa biblioteca Maven versionada

> Estudo de **2026-11-08**. Nenhum código foi alterado.
>
> **Estrutura de pastas (2026-11-09):** o repositório foi reorganizado depois desta análise. `core/` virou `applications/library-core`, a versão Jetty (antes na raiz) virou `applications/monolith` e `serverless/backend` e `serverless/frontend` viraram `applications/serverless/lambda` e `applications/serverless/spa`. Os caminhos antigos abaixo são o registro da época.

> **Andamento (2026-11-08):** fases 1 a 3 implementadas (change `core-library`). A library `grade-checker-core` 1.0.0 está em `core/`, e as versões Jetty e serverless a usam, sem cópias do núcleo. Testes, as duas suítes E2E e a comparação de notas não mostram diferenças. Falta publicar pelo workflow manual "Release core library" (`version=1.0.0`). A fase 4 já está em andamento (change `core-ports-use-cases`, na mesma versão 1.0.0, ainda não publicada):
>
> - **Na library:** as portas de persistência e a porta do GitHub, os casos de uso e a `DomainException`.
> - **Nas duas versões:** já os usam, em portas e adapters (changes `backend-hexagonal` em `serverless/openspec` e `jetty-hexagonal` em `openspec/`).
> - **Nomes e organização finais (change `core-naming`):**
>   - a porta do histórico existe: é a `GitHubGateway`, com `getHistory`;
>   - as renomeações previstas neste plano foram feitas (`ContributorStats`, `RepositoryUrl`, sem campos de tela no `Group`);
>   - o cliente do GitHub foi para `core.adapter.github`;
>   - o relatório saiu do cliente e foi para `attribution.ContributionAnalyzer`;
>   - as portas `CommitHistorySource` e `ContributorSource` deste rascunho viraram a única porta `GitHubGateway`, e `ClassGrades` virou `ClassGradesQuery`. O restante do texto abaixo é o estudo original, com os nomes da época.
>
> **Objetivo:** tirar o domínio do Grade Checker de dentro das duas aplicações e transformá-lo num artefato Maven versionado (`grade-checker-core`), usado como dependência pela versão Jetty (raiz do repositório) e pela versão serverless (`serverless/backend`).

## 1. Situação atual

Hoje o núcleo está **copiado** nas duas versões:

| Situação | Arquivos | Linhas |
|---|---|---|
| Idênticos nas duas versões | 26 (`GradeCalculator`, `AuthorAggregator`, `PeriodResolver`, `Milestones`, `GradingConfig` e 18 modelos, entre outros) | ~2.440 |
| Copiados com acréscimos na versão serverless | 6 (`GitHubProxy`, `GroupGradesService`, `ContributorsService`, `CacheManager`, `AdminConfig`, `GitHubOAuthConfig`) | diff de ~200 |
| Regras de negócio reescritas nas duas versões | invariantes de grupos, membros e associações nos servlets e repositórios (Jetty) e nas rotas e stores (Lambda) | — |

**Problemas:**
- **Correção em dois lugares:** uma mudança na regra da nota precisa ser feita duas vezes, e as cópias podem divergir sem ninguém perceber.
- **Invariantes fora do domínio:** as regras de grupos, membros e associações vivem nos adaptadores (servlets, SQL, rotas e DynamoDB), então trocar a infraestrutura obrigou a reescrevê-las.
- **Núcleo dependente de infraestrutura:**
  - Log4j em `GradingConfig` e `Milestones`;
  - anotações do Gson em `ContributorStatsResponseBody`;
  - Unirest, Gson e o `CacheManager` estático no `GitHubProxy`;
  - leitura do classpath em `Milestones.fromClasspath()` e `GradingConfig.fromClasspath()`.
- **Modelo misturado com tela:** `Group.contributorsError` e `GroupCard` são interesses de apresentação. `ContributorStatsResponseBody` tem nome de DTO de resposta, mas é o resultado do domínio.
- **Uma decisão técnica dentro do domínio:** a leitura única do histórico no `GroupGradesService`, motivada pelo limite de 30 s do API Gateway.

## 2. Arquitetura alvo: hexagonal na medida certa

**A ideia:** a versão Jetty e a versão Lambda ficam só com o que é **particular** de cada uma, e a library fica com o **core**, usado do mesmo jeito pelas duas.

```
        versão Jetty (só o particular)                 versão Lambda (só o particular)
   ┌───────────────────────────────────┐        ┌───────────────────────────────────────┐
   │ ENTRADA  servlets + JSPs          │        │ ENTRADA  ApiHandler + Router + Routes │
   │          filtro + HttpSession     │        │          SessionStore (DynamoDB)      │
   │ SAÍDA    JDBC/H2  ──implementa──┐ │        │ SAÍDA    DynamoDB ──implementa──┐     │
   │          Caffeine ──implementa─┐│ │        │          Caffeine+DynamoDB ─────┤     │
   │ CONFIG   .properties, admins   ││ │        │ CONFIG   .properties, SSM       │     │
   └──────────────────┬─────────────┼┼─┘        └──────────────────┬──────────────┼─────┘
                      │ usa         ││                             │ usa          │
                      ▼             ▼▼                             ▼              ▼
   ┌──────────────────────────────────────────────────────────────────────────────────┐
   │ grade-checker-core  (um artefato, Java 11, versionado)                           │
   │                                                                                  │
   │  domain/       regras: nota, atribuição, períodos, grupos, membros, associações  │
   │                (só a JDK; não importa nada de fora)                              │
   │  application/  casos de uso: relatório, histórico, notas da turma, membros...    │
   │  port/         interfaces: Groups, Members, IdentityLinks, ResultCache,          │
   │                CommitHistorySource, ContributorSource, GitHubDirectory           │
   │  github/       cliente GraphQL/REST do GitHub, que implementa as portas do       │
   │                GitHub (Unirest + Gson, as mesmas bibliotecas de hoje)            │
   └──────────────────────────────────────────────────────────────────────────────────┘
```

### 2.1 Hexagonal e Clean Architecture

| Clean Architecture | Hexagonal | No Grade Checker |
|---|---|---|
| Entities | domínio (centro) | `domain/`: `GradeCalculator`, `AuthorAggregator`, `PeriodResolver`, `Group`, `Member`, invariantes |
| Use cases | aplicação (centro) | `application/`: `ContributionsQuery`, `ClassGrades`, `Membership`... |
| Interface adapters | adaptadores | servlets e JSPs, Router e Routes, JDBC, DynamoDB, `github/` |
| Frameworks & drivers | infraestrutura | Jetty, H2, AWS Lambda, API Gateway, DynamoDB |
| **Regra de dependência** (setas para dentro) | portas no centro | as aplicações dependem da library; o domínio não conhece Jetty, Lambda, H2, DynamoDB nem GitHub |

### 2.2 O que fica de fora, de propósito, para ficar simples

- **Um artefato só**, e não um por camada. O pacote `github/` fica dentro da library porque as duas aplicações usam o mesmo cliente e as mesmas bibliotecas; separá-lo num segundo artefato pode ser feito depois, se for preciso.
- **Portas só onde as aplicações diferem:** persistência (H2 × DynamoDB) e cache (Caffeine × Caffeine + DynamoDB). As portas do GitHub existem para o domínio não depender do cliente, e a library já traz a implementação.
- **Sem interfaces de entrada e saída por caso de uso,** sem camada de DTO e sem mapeadores entre camadas. Os casos de uso recebem parâmetros simples e devolvem os objetos do domínio; cada aplicação converte para HTML (JSP) ou JSON.
- **Sem framework de injeção:** cada aplicação monta as dependências à mão. A Jetty faz isso nos construtores dos servlets; a Lambda, no `AppContext`.
- **A fronteira é garantida por um teste simples dentro da library,** que falha se algum arquivo de `domain/` ou `application/` importar algo de fora da JDK ou do pacote `github/`.

**Do ponto de vista de cada aplicação, o uso é transparente.** Ela adiciona a dependência, implementa duas ou três portas com a sua infraestrutura e chama os casos de uso. Exemplo:
- `classGrades.of(groupId, token)` devolve as notas, venham os grupos do H2 ou do DynamoDB;
- `membership.add(groupId, login, name, token)` aplica as mesmas regras e lança as mesmas exceções nas duas versões.

## 3. O que vai para cada lugar

### 3.1 `grade-checker-core`: pacotes `domain`, `application` e `port` (só a JDK)

| Pacote | Conteúdo | De onde vem |
|---|---|---|
| `…core.grading` | `GradeCalculator`, `GradingConfig`, `GradingExplanation` | idênticos hoje |
| `…core.period` | `Milestones`, `PeriodResolver`, `ResolvedPeriod`, `PeriodOption` | idênticos hoje |
| `…core.attribution` | `AuthorAggregator`, `AttributedCommit`, `AttributedHistory`, `AttributionSource`, chave de identidade (`email:` / `name:`) | idênticos hoje |
| `…core.history` | `HistoryCommit`, `RepositoryHistory`, `ContributionsReport`, `ContributorStats` (o atual `ContributorStatsResponseBody`, renomeado), `CommitActivity` | idênticos hoje (com renomeação) |
| `…core.group` | `Group`, `Member`, `RepositoryUrl` (o atual `GroupRepositoryUrl`), `IdentityLink` e as **invariantes** (seção 4) | hoje espalhadas nos adaptadores |
| `…core.application` | casos de uso que hoje estão duplicados nos servlets e nas rotas (seção 5) | `ContributorsStatServlet` × `ReportRoutes`, etc. |
| `…core.port` | interfaces das portas (seção 6) | novo |
| `…core.error` | exceções do domínio com código estável (seção 7) | novo |

### 3.2 `grade-checker-core`: pacote `github` (integração compartilhada)

O cliente do GitHub (`GitHubProxy`, `GitHubUserProxy`, `AuthenticationProxy`, `GitHubApiException`) é quase idêntico nas duas versões e não é domínio: é integração. Ele fica no pacote `github/` da mesma library, implementando as portas `CommitHistorySource`, `ContributorSource` e `GitHubDirectory`, e usa a porta `ResultCache` no lugar do `CacheManager` estático. Assim:
- **nada do GitHub fica duplicado;**
- **o domínio continua sem Unirest e sem Gson,** porque só o pacote `github/` os importa;
- **cada aplicação injeta o seu cache:** Caffeine na versão Jetty; Caffeine mais DynamoDB na serverless.

### 3.3 Fica em cada aplicação

| Jetty | Serverless |
|---|---|
| servlets, JSPs, `AuthenticationFilter`, `HttpSession` | `ApiHandler`, `Router`, `*Routes`, `SessionStore` |
| `GroupRepository`, `MemberRepository`, `IdentityAliasRepository` (JDBC), implementando as portas | `GroupStore`, `MemberStore`, `AliasStore` (DynamoDB), implementando as portas |
| `CacheManager` (Caffeine), implementando `ResultCache` | Caffeine + `DynamoCache`, implementando `ResultCache` |
| `AdminConfig` (sessão), `GitHubOAuthConfig` | idem, com o secret do SSM |
| leitura de `grading.properties` e `milestones.properties` do classpath | idem |
| `GroupCard` e as linhas de tela (`CommitRow`), que são de apresentação | o mesmo, ou o mapeamento direto para JSON |

## 4. Invariantes que entram no domínio

Hoje estas regras são reimplementadas em cada versão. No core, viram serviços de domínio que falam com as portas:

| Invariante | Serviço no core |
|---|---|
| Um repositório pertence a um só grupo, ativo ou arquivado; um grupo arquivado sugere restaurar | `GroupManagement.create` / `edit` |
| O grupo é gravado com a grafia devolvida pelo GitHub | `GroupManagement` (via `GitHubDirectory`) |
| Um login não é membro duas vezes (sem diferenciar maiúsculas) | `Membership.add` |
| O login de um membro precisa existir no GitHub | `Membership.add` / `rename` |
| Ao trocar o login, as associações manuais acompanham | `Membership.rename` (a porta `Members.rename` é atômica no adaptador) |
| Não se remove um aluno com identidade associada | `Membership.remove` |
| Associação só para aluno do grupo (sem bots e sem `excludedLogins`) | `IdentityLinking.link` |
| Grupo arquivado não aparece na home, nas notas nem no dashboard | `Groups.active()` + casos de uso |

**Atomicidade:** a regra de negócio fica no core, e a garantia de atomicidade fica no adaptador, porque depende do banco (transação SQL no H2, `TransactWriteItems` no DynamoDB). Por isso as portas têm operações com a intenção explícita, como `Members.rename(group, from, to, name)` e `Groups.create(group)`, que lança `RepositoryAlreadyRegistered` se a URL estiver em uso.

## 5. Casos de uso compartilhados

A lógica que hoje está duplicada entre os servlets da versão Jetty e as rotas da Lambda vira casos de uso, e os adaptadores só convertem a entrada e serializam a saída:

| Caso de uso | Hoje (Jetty) | Hoje (Lambda) |
|---|---|---|
| `ContributionsQuery.report(group, period, token)` com as notas por marco ou de All, alunos sem commits com zeros | `ContributorsStatServlet` (`addGrades`, `addMissingStudents`) | `ReportRoutes` (mesmo código, portado) |
| `CommitHistoryQuery.of(group, author, period, token)` com "Also counted" e o gráfico de frequência | `ContributionsDetailServlet` | `ReportRoutes.commits` |
| `ClassGrades.of(group, token)` e a lista de alunos | `GradebookServlet` + `GroupGradesService` | `GradebookRoutes` + `GroupGradesService` |
| `GroupManagement`, `Membership`, `IdentityLinking` | `GroupsApiServlet`, `GroupMembersServlet`, `IdentityAliasServlet` | `GroupRoutes` |
| `GradingExplanation.of(config, milestones)` | idêntico | idêntico |

**Estratégia de leitura do histórico:** a decisão "uma leitura ou três" sai do `GroupGradesService`. O caso de uso pede à porta `CommitHistorySource` os commits do intervalo dos marcos, e a implementação decide como buscar. Os dois jeitos já são provados equivalentes pelo teste `GroupGradesSingleHistoryTest`, então o core pode adotar a leitura única para as duas versões.

## 6. Portas (rascunho)

```java
// saída: persistência (implementadas com JDBC/H2 na Jetty, DynamoDB na Lambda)
interface Groups        { List<Group> active(); List<Group> archived(); Optional<Group> find(long id);
                          Optional<Group> byRepository(RepositoryUrl url); long create(Group g);
                          boolean update(Group g); boolean archive(long id); boolean restore(long id); }
interface Members       { List<Member> of(long group); boolean add(long group, Member m);
                          RenameResult rename(long group, String from, String to, String name); boolean remove(long group, String login); }
interface IdentityLinks { Map<String, String> of(long group); void link(long group, String key, String login, String by);
                          boolean unlink(long group, String key); List<String> keysOf(long group, String login); }

// saída: GitHub (implementadas pelo pacote github/ da própria library)
interface CommitHistorySource { RepositoryHistory history(RepositoryRef repo, Instant since, Instant until, AccessToken token); }
interface ContributorSource   { List<Contributor> contributors(RepositoryRef repo, AccessToken token); }
interface GitHubDirectory     { Optional<GitHubUser> user(String login, AccessToken token);
                                Optional<RepositoryInfo> repository(String owner, String name, AccessToken token); }

// saída: técnico
interface ResultCache { <T> Optional<T> get(String key, Type type); void put(String key, Object value); }
// tempo: java.time.Clock, injetado (os testes já usam Clock.fixed)
```

## 7. Erros do domínio

O core lança exceções com **código estável** e com a mensagem em inglês que já aparece hoje. Os adaptadores só convertem o código no status HTTP:

| Exceção | Código | Mensagem (já usada hoje) | HTTP |
|---|---|---|---|
| `GroupNotFound` | `not_found` | Group not found. | 404 |
| `RepositoryAlreadyRegistered(archived)` | `already_registered` | This repository is already registered. / …in an archived group. Restore it instead. | 409 |
| `InvalidRepositoryUrl` | `bad_request` | Enter the URL of a GitHub repository (https://github.com/owner/repository). | 400 |
| `RepositoryNotAccessible` | `invalid_repository` | Repository not found or not accessible with this token. | 400 |
| `AlreadyMember` | `already_member` | This user is already a member of the group. | 409 |
| `NotAMember` | `not_found` | This user is not a member of the group. | 404 |
| `UnknownGitHubUser` | `invalid_login` | This GitHub user does not exist. | 400 |
| `HasLinkedIdentities` | `has_aliases` | Unlink the identities linked to this student before removing them. | 409 |
| `NotAStudent` | `invalid_login` | The login must be a student of this group. | 400 |
| `HistoryUnavailable(status)` | o do GitHub | a do GitHub | o do GitHub |

Com isso, as mensagens passam a ter uma única fonte, e as duas versões não divergem.

## 8. Ajustes de pureza no core

1. **Logs:** trocar o Log4j por `java.lang.System.Logger`, da própria JDK desde o Java 9. As aplicações ligam esse logger ao Log4j, e o core não traz dependência.
2. **Configuração:** o core recebe `Properties` ou `InputStream` (`GradingConfig.of(...)`, `Milestones.of(...)`). Cada aplicação lê os seus arquivos do próprio classpath, e os `.properties` continuam em cada aplicação.
3. **Serialização:** nenhuma anotação do Gson e nenhum `transient` "para a tela" no domínio. A serialização para JSON e para o cache fica nos adaptadores.
   - Lição já aprendida: os indicadores de nota eram `transient` e o cache no DynamoDB os perdia.
4. **Apresentação fora do domínio:** `Group.contributorsError` e `GroupCard` saem do core. `CommitRow` (data formatada) também, porque é formatação de tela.
5. **Tempo:** `Clock` sempre injetado, sem `Instant.now()` escondido no domínio.
6. **Java 11:** o artefato é compilado com `--release 11`, porque a versão Jetty roda Java 11 (bytecode) e a serverless roda Java 21 (lê bytecode 11 sem problema).

## 9. Onde fica o código e como é versionado

**Local proposto:** a pasta `core/` na raiz do repositório, como projeto Maven independente das duas aplicações:

```
core/
  pom.xml                       br.com.gradechecker:grade-checker-core (jar, --release 11)
  src/main/java/br/com/gradechecker/core/
    domain/                     regras (só a JDK)
    application/                casos de uso (só a JDK + domain + port)
    port/                       interfaces implementadas pelas aplicações
    github/                     cliente do GitHub (Unirest + Gson), implementa as portas do GitHub
  src/test/java/...             testes do domínio, testes com histórias fixas, teste de arquitetura
  CHANGELOG.md
```

**Versionamento semântico:**

| Mudança | Versão | Exemplo |
|---|---|---|
| Correção sem mudança de regra | patch (`1.2.1`) | arredondamento, log |
| Regra nova ou mudança compatível | minor (`1.3.0`) | novo fator da nota, novo caso de uso |
| Mudança de nota ou de API incompatível | major (`2.0.0`) | outra fórmula de nota, porta com assinatura nova |

**Notas mudam de valor:** qualquer mudança que altere o valor das notas sobe pelo menos a minor e entra no `CHANGELOG.md`. Cada aplicação escolhe quando adotar a nova versão.

**Publicação:** pelo workflow manual "Release core library" do GitHub Actions, disparado pelo professor com a versão. O workflow roda os testes, publica os jars e cria a tag `core-vX.Y.Z`.

## 10. Distribuição do artefato: opções

| Opção | Publicar | Consumir | Prós | Contras |
|---|---|---|---|---|
| **GitHub Packages** | `mvn deploy` no Actions com o `GITHUB_TOKEN` | **exige token mesmo com o repositório público** (`settings.xml` com um PAT `read:packages`) | oficial, versões visíveis no repositório, sem custo | o token precisa estar em todo build: Docker da Jetty, EC2 (que compila na instância), `sam build`, Docker da Lambda, CI |
| **Maven Central** (namespace `io.github.professor-enoqueleal`) | portal do Central, com assinatura GPG | sem token, de qualquer lugar | consumo sem atrito, permanente | configuração inicial mais trabalhosa (namespace, chave GPG); versões publicadas não podem ser apagadas |
| **JitPack** | nenhum: compila a partir da tag do repositório público | sem token (`https://jitpack.io`) | zero configuração | serviço de terceiros; compila na primeira requisição; precisa de um `jitpack.yml` apontando para `core/` |
| **AWS CodeArtifact** | `mvn deploy` com um token da AWS | token temporário (12 h) da AWS | integrado à AWS da versão serverless | custo pequeno; token que expira em todo build; a versão Jetty passaria a depender da AWS |
| Só instalação local (`mvn install`) | — | `~/.m2` | simples para desenvolver | não é um artefato versionado de verdade; não serve para CI, Docker nem EC2 |

**Recomendação:**
- **Desenvolvimento local:** `mvn install` em `core/`.
- **Artefato versionado:** **GitHub Packages**, se o atrito do token for aceitável, já que as versões ficam junto do repositório e o publicador já é o GitHub Actions. Se a prioridade for nenhum token nos builds, que é o caso da EC2 e dos Dockerfiles, o **Maven Central** é o mais limpo a longo prazo.
- Como a versão Jetty deve ser desligada depois da publicação serverless, o atrito do token nela é temporário.

## 11. Como cada aplicação consome

```xml
<!-- pom.xml da versão Jetty e serverless/backend/pom.xml -->
<dependency>
  <groupId>br.com.gradechecker</groupId>
  <artifactId>grade-checker-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

Cada aplicação implementa as portas de persistência e de cache e monta os casos de uso:

| Porta | Versão Jetty | Versão Lambda |
|---|---|---|
| `Groups`, `Members`, `IdentityLinks` | `GroupRepository`, `MemberRepository`, `IdentityAliasRepository` (JDBC/H2) | `GroupStore`, `MemberStore`, `AliasStore` (DynamoDB) |
| `ResultCache` | `CacheManager` (Caffeine) | Caffeine + `DynamoCache` |
| GitHub (`CommitHistorySource`, ...) | da library (`github/`) | da library (`github/`) |

Impactos nos builds:

| Build | O que muda |
|---|---|
| `mvn test` / IntelliJ | resolve do `~/.m2` (depois de `mvn install` em `core/`) ou do repositório remoto |
| `Dockerfile` da raiz (Jetty) | com GitHub Packages, o token entra como secret do BuildKit (`--mount=type=secret`), nunca numa camada da imagem |
| EC2 (CloudFormation compila na instância) | com GitHub Packages, o token iria para o Secrets Manager e para o `settings.xml` do script. Com o Maven Central, nada muda |
| `sam build` e `serverless/backend/Dockerfile` | o mesmo do Docker da raiz |
| GitHub Actions | o `GITHUB_TOKEN` já basta para ler o GitHub Packages do próprio repositório |

## 12. Testes

- **No core:** os testes unitários que hoje estão copiados nas duas versões (notas, atribuição, períodos, configuração) passam a existir uma vez só.
- **Testes de contrato com histórias fixas** (golden tests): conjuntos de commits salvos (JSON), com as notas esperadas por aluno e por marco. Eles protegem a regra da nota em todas as versões do artefato. É a versão permanente da comparação Jetty × serverless feita em 2026-11-08.
- **Teste de arquitetura:** lê os imports dos arquivos de `domain/` e `application/` e falha se algum vier de fora da JDK, do próprio core ou de `port/`. É a garantia de que a regra de dependência não é quebrada sem querer. Pode ser feito com o ArchUnit, ou com um teste simples que lê os fontes.
- **Nas portas:** um teste abstrato por porta (por exemplo, `MembersContractTest`) que cada adaptador estende. O mesmo teste roda contra o JDBC/H2 e contra o DynamoDB Local, e confirma que os dois respeitam as mesmas invariantes de atomicidade.
- **Nas aplicações:** continuam os testes dos adaptadores (servlets, rotas, stores) e as duas suítes E2E (`e2e/` e `serverless/e2e/`).

## 13. Plano em fases

| Fase | O que entra | Resultado |
|---|---|---|
| **0. Decisões** | registro do artefato, renomeação de pacotes, se a versão Jetty adota | escolhas registradas |
| **1. Core 0.1** | criar `core/` com os 26 arquivos idênticos, os ajustes de pureza (seção 8) e os testes; `--release 11`; `mvn install` | artefato local, sem mudar as aplicações |
| **2. Serverless adota** | `serverless/backend` passa a depender do core e apaga as cópias; o CI publica `0.1.0` | uma cópia a menos; testes e E2E serverless verdes |
| **3. Jetty adota** (opcional) | o mesmo na raiz, se a versão Jetty ainda for viver algum tempo | as duas versões no mesmo artefato |
| **4. Core 1.0** | portas, invariantes (seção 4), casos de uso (seção 5), erros (seção 7), cliente do GitHub no pacote `github/` com a porta `ResultCache`, teste de arquitetura e testes de contrato | as regras de negócio e o cliente do GitHub numa fonte só; cada aplicação só com o particular |

As fases 1 e 2 são mecânicas e de baixo risco, porque o código já é idêntico. A fase 4 é a que muda o desenho, e por isso pede mais cuidado e os testes de contrato.

## 14. Riscos

| Risco | Mitigação |
|---|---|
| Uma versão do core muda notas sem querer | testes de contrato com histórias fixas; mudança de nota exige minor/major e `CHANGELOG.md` |
| Token do GitHub Packages espalhado pelos builds | secret do BuildKit; Secrets Manager na EC2; ou Maven Central |
| Classes com o mesmo nome no core e na aplicação durante a transição | renomear o pacote do core (`br.com.gradechecker.core.*`) e apagar as cópias no mesmo commit |
| Fazer a fase 4 duas vezes (Jetty e serverless) | se a versão Jetty for desligada logo, ela para na fase 1 ou 2, e só a serverless vai até a fase 4 |
| Java 11 limita a sintaxe no core (sem `record`, sem `switch` com padrões) | aceitável enquanto a versão Jetty existir; depois de desligá-la, o core pode subir para Java 17 ou 21 (versão major) |
| Atomicidade diferente entre H2 e DynamoDB | as portas definem a operação atômica; os testes de contrato rodam nos dois adaptadores |

## 15. Impacto no OpenSpec

- **Novo:** um OpenSpec em `core/openspec`, como o de `serverless/`, com as capabilities do domínio: notas por marco, atribuição de autores, períodos, invariantes de grupos e membros, portas e versionamento do artefato.
- **Nas duas aplicações:** as specs de API e telas continuam onde estão e passam a citar o core como fonte das regras. As specs de nota e atribuição das aplicações viram referências à spec do core, para a regra não ser descrita em três lugares.

## 16. Decisões (2026-11-08)

1. **Registro do artefato:** GitHub Packages, com token `read:packages` nos builds: secret de build no Docker, Secrets Manager na EC2, `GITHUB_TOKEN` no CI e `~/.m2/settings.xml` localmente.
2. **Cliente do GitHub na library:** sim, no pacote `github/` do mesmo artefato.
3. **Pacotes:** renomeados para `br.com.gradechecker.core.*`.
4. **Versão Jetty:** adota a library, junto com a serverless.
5. **Escopo:** a primeira change cobre as fases 1 a 3 (change `core-library` em `core/openspec`). A fase 4 (portas, invariantes, casos de uso, erros) será uma change seguinte.
