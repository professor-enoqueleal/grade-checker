## Context

Depois da fase 4 (portas e casos de uso), a library já tem `port`, `error` e `application`, mas ficaram nomes e responsabilidades herdados da versão Jetty original. A `1.0.0` ainda não foi publicada, e as duas aplicações estão neste repositório: dá para trocar os nomes de uma vez, sem período de compatibilidade.

## Goals / Non-Goals

**Goals:**
- Pacotes coesos e sem ciclo: domínio, portas, casos de uso e o adaptador do GitHub, cada um no seu lugar.
- Nomes que dizem o que a classe é: cliente, porta, consulta, relatório.
- Regra de domínio fora do cliente HTTP.
- Nenhuma mudança de contrato HTTP, de tela ou de regra de nota.

**Non-Goals:**
- Separar o cliente do GitHub num segundo artefato (decisão 2 do `plan.md`: ele fica na library).
- Mudar `CommitRow` e `GroupCard` (`view`): as duas aplicações usam esses objetos do mesmo jeito, e dividir a formatação só duplicaria código.

## Decisions

### Pacotes finais

```
core
├── application        casos de uso (dependem só de port, error e domínio)
├── port               GroupRepository, MemberRepository, IdentityAliasRepository, GitHubGateway, ResultCache, RepositoryTakenException
├── error              DomainException
├── group              Group, Member, RepositoryUrl
├── github             Contributor, GitHubRepository, GitHubApiException (conceitos do GitHub, sem Gson)
├── history            HistoryRequest, RepositoryHistory, HistoryCommit, ContributionsReport, ContributorStats, CommitActivity
├── attribution        AuthorAggregator, ContributionAnalyzer, AttributedCommit, AttributedHistory, AttributionSource
├── grading            GradeCalculator, GradingConfig, GradingExplanation
├── period             PeriodResolver, Milestones
├── view               CommitRow, GroupCard
├── adapter.github     GitHubClient (implementa GitHubGateway), GitHubOAuthClient, GitHubOAuthSettings, AccessToken
└── internal           Log, Resources
```

- **Por que `adapter.github` e não `github`:** é o mesmo nome que as aplicações usam (`adapter/outbound/...`) e deixa claro que é infraestrutura. O nome `github` fica com os conceitos que a porta devolve. Assim a porta não depende do adaptador, e o ciclo `port` ↔ `github` acaba.
- **Por que os conceitos ficam sem Gson:** o adaptador lê o JSON com a árvore do Gson (`JsonObject`) e monta os objetos do domínio, como já faz com o histórico. O cache serializa os objetos do domínio pelo nome dos campos. A chave dos contribuidores ganha `v2` para não ler entradas no formato antigo.

### Relatório fora do cliente

`ContributionAnalyzer(GradingConfig, ZoneId)`, em `attribution`, faz o que o `GitHubProxy` fazia depois de ler o histórico:
- `report(history, aliases)`: relatório do histórico inteiro;
- `report(history, since, until, aliases)`: relatório do recorte de um período;
- `attribute(history, aliases)`: os commits, cada um com o autor para quem conta.

O relatório não tem mais cache próprio. A agregação de até 1.000 commits em memória é barata, e o histórico, que é a parte cara (GraphQL paginado), continua em cache. Com isso, `GitHubClient` só fala com o GitHub e só precisa da URL, da versão da API e do cache. `GradingConfig` e o fuso saem do construtor dele.

### Casos de uso

- **Construtores:** recebem portas, `PeriodResolver` e `GradingConfig`, e montam internamente o `ContributionAnalyzer` (com o fuso do `PeriodResolver`) e o `MilestoneGrades`. As aplicações não montam serviços intermediários.
- **`MilestoneGrades`:** é public final, porque os seus resultados (`GroupGrades` e `StudentGrades`) aparecem para as aplicações. O construtor é de pacote, então só os casos de uso o montam.
- **Modo de três leituras:** sai. A leitura única do histórico já fazia a volta para um relatório por marco quando o histórico vinha truncado, e esse caminho continua.

### Convenções de nomes

| Tipo | Convenção | Exemplos |
|---|---|---|
| Caso de uso que altera | substantivo do assunto | `GroupManagement`, `Membership`, `IdentityLinking` |
| Caso de uso só de leitura | sufixo `Query` | `ContributionsQuery`, `CommitHistoryQuery`, `ClassGradesQuery` |
| Porta de persistência | sufixo `Repository`, métodos `find*`, `create`, `update`, `delete` | `GroupRepository.findActive` |
| Porta de sistema externo | sufixo `Gateway` | `GitHubGateway` |
| Cliente HTTP | sufixo `Client` | `GitHubClient`, `GitHubOAuthClient` |
| Fábrica estática | `of`, `parse`, `from*` | `GradingConfig.of`, `RepositoryUrl.parse` |
| Busca num resultado | nome do que devolve | `GroupGrades.student(login)` |

- **Login:** o login do GitHub sempre se chama `login`. Os ids são `long`.

### Teste de arquitetura

`ArchitectureTest` ganha duas regras:
- Unirest e Gson só em `core.adapter`;
- o grafo de dependência entre os pacotes de primeiro nível não tem ciclo.

A regra de `port` e `error` continua valendo.

## Risks / Trade-offs

- **[Entradas antigas no cache do DynamoDB]:** o formato JSON do `Contributor` muda. → Mitigação: a chave nova (`github.contributors.v2::`) ignora as entradas antigas, que expiram pelo TTL.
- **[Relatório sem cache]:** cada pedido recalcula a agregação. → Mitigação: o custo é em memória e linear no número de commits. O histórico continua em cache nas duas aplicações.
- **[Mudança grande de nomes]:** muitos arquivos mudam nas três partes. → Mitigação: todos os testes das três partes e os quatro E2E rodam no fim.
