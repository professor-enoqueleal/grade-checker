## Context

- **Situação hoje:**
  - a Lambda tem as regras de grupos, membros e associações em `GroupRoutes`, a montagem do relatório e do histórico em `ReportRoutes`, e a lista de alunos em `GradebookRoutes`;
  - a versão Jetty tem as mesmas regras nos servlets e no `GroupServiceImpl`.
- **Decisões do professor (2026-11-08):**
  - fazer agora as portas e os casos de uso, junto com a reorganização da Lambda;
  - a Jetty adota depois;
  - os adaptadores de persistência se chamam *Repository*.

## Goals / Non-Goals

**Goals:**
- as regras e as mensagens numa única fonte;
- adaptadores finos, que só convertem HTTP ou banco;
- casos de uso testáveis sem banco e sem GitHub, com portas em memória;
- nenhuma mudança de comportamento na API.

**Non-Goals:**
- **Portas do histórico do GitHub** (`CommitHistorySource`, `ContributorSource`): os casos de uso de relatório continuam usando o `ContributorsService` e o `GitHubProxy`, que já estão na library.
- **Renomeações** que quebrariam a versão Jetty (`ContributorStatsResponseBody`, `GroupRepositoryUrl`): ficam para a change da Jetty.
- **Tirar `Group.contributorsError`, `GroupCard` e `CommitRow` da library:** também ficam para depois.

## Decisions

### 1. Portas de persistência (`core.port`)
- **Nomes e métodos:** iguais aos dos repositórios de hoje, para a adoção pela Jetty ser direta.
- **Retorno:** `null` quando não encontra, como no resto do código.
- **Atomicidade:** quem a garante é o adaptador.

```java
interface GroupRepository {
    List<Group> findActive(); List<Group> findArchived();
    Group find(long id); Group findActive(long id);
    Group findByRepository(String repositoryUrl, Long exceptGroupId);   // outro grupo (ativo ou arquivado) com a URL
    long create(Group g);          // RepositoryTakenException
    boolean update(Group g);       // false: ausente ou arquivado; RepositoryTakenException
    boolean archive(long id); boolean restore(long id);
}
interface MemberRepository {
    List<Member> findByGroupId(long groupId); List<String> findUsernamesByGroupId(long groupId);
    boolean isMember(long groupId, String login);
    boolean addIfAbsent(long groupId, String login, String name, boolean manual);
    boolean delete(long groupId, String login);
    RenameResult rename(long groupId, String login, String newLogin, String name);   // as associações acompanham, na mesma transação
}
interface IdentityAliasRepository {
    Map<String, String> findByGroupId(long groupId);
    void save(long groupId, String identityKey, String login, String createdBy);
    boolean delete(long groupId, String identityKey);
    default List<String> keysOfLogin(long groupId, String login);   // a partir de findByGroupId
    default boolean existsForLogin(long groupId, String login);
}
interface GitHubDirectory {
    GitHubRepository getRepository(String owner, String repo, String token);   // null: não existe ou sem acesso
    Contributor getUser(String login, String token);                          // null: não existe
}
```

- **`RepositoryTakenException`** fica em `core.port`: o adaptador a lança quando a reserva da URL falha na transação.
- **`RenameResult`** é o enum `OK`, `NOT_MEMBER`, `ALREADY_MEMBER`.

### 2. Erros (`core.error`)
- **`DomainException(Kind, code, message)`**, com `Kind` igual a `INVALID`, `NOT_FOUND` ou `CONFLICT`.
- **Tradução:** o adaptador HTTP traduz para 400, 404 ou 409. Código e mensagem são os de hoje, conferidos pelo `ApiIT` e pelo E2E.
- **Erros do GitHub:** o `GitHubApiException` continua passando como hoje, com o status e a mensagem do GitHub.

### 3. Casos de uso (`core.application`)

| Caso de uso | Dependências | O que faz |
|---|---|---|
| `GroupManagement` | `GroupRepository`, `MemberRepository`, `GitHubDirectory`, `GitHubProxy` (contribuidores) | `active()`, `archived()`, `activeGroup(id)`, `detail(id, token)`, `create(name, url, token)`, `update(id, name, url, token)`, `archive(id)`, `restore(id)`. Valida nome e URL, confere duplicata antes e depois da grafia do GitHub e grava com a grafia do GitHub |
| `Membership` | `GroupRepository`, `MemberRepository`, `IdentityAliasRepository`, `GitHubDirectory`, `GradingConfig` | `members(id)`, `students(id)`, `add(...)`, `edit(...)`, `remove(...)`. Formato do login, login existente no GitHub, sem duplicata, não remove aluno com associação |
| `IdentityLinking` | `GroupRepository`, `MemberRepository`, `IdentityAliasRepository`, `GradingConfig` | `link(id, key, login, by)` e `unlink(id, key)`. Só para aluno do grupo, com a chave `email:` ou `name:` |
| `ContributionsQuery` | `GroupManagement`, `MemberRepository`, `ContributorsService`, `GroupGradesService`, `PeriodResolver`, `GradingConfig` | relatório do período com as notas do marco ou de All, incluindo os alunos sem commits |
| `CommitHistoryQuery` | `GroupManagement`, `IdentityAliasRepository`, `GitHubProxy`, `PeriodResolver` | commits de um login no período, "Also counted" e dados do gráfico. Um período inválido vira All com aviso |
| `ClassGrades` | `GroupManagement`, `Membership`, `GroupGradesService` | grupos com os seus alunos e as notas de um grupo |

- **Retorno:** os casos de uso devolvem objetos do domínio ou classes de resultado simples, sem mapas e sem JSON. A serialização fica no adaptador.
- **Ordem das validações:** a mesma de hoje. Exemplo: no `add`, o formato do login é conferido antes da busca do grupo.

### 4. Fronteira
- **Regra nova do `ArchitectureTest`:** os arquivos de `core.port` e `core.error` só importam a JDK e a própria library.
- **`core.application`:** já era coberto pela regra existente, que não deixa Unirest nem Gson fora de `core.github`.

### 5. Versão
A versão `1.0.0` nunca foi publicada: a tag `core-v1.0.0` ainda não existe e `core/` ainda não está no Git. Por isso, esta change entra na `1.0.0`, e o `CHANGELOG.md` a descreve. A versão Jetty compila sem mudanças, porque tudo é adicionado.

## Risks / Trade-offs

- **Mudança de comportamento por engano.** Mitigação: o `ApiIT` da Lambda (DynamoDB Local mais WireMock) e as duas suítes E2E rodam antes e depois.
- **Casos de uso dependendo do `GitHubProxy` concreto** (relatório e histórico). É aceito agora, porque as portas do histórico ficam para depois. O `GitHubDirectory` cobre o que as regras de grupos e membros precisam.
