# core-structure Specification

## Purpose
Organização da library em pacotes coesos e sem ciclos (casos de uso, portas, domínio e o adaptador do GitHub), convenções de nomes e o relatório calculado no domínio a partir do histórico.
## Requirements
### Requirement: Pacotes da library
A library SHALL organizar o código em pacotes `br.com.gradechecker.core.*` com uma responsabilidade cada:
- `application`: casos de uso;
- `port`: portas implementadas pelas aplicações ou pelo adaptador do GitHub;
- `error`: erros do domínio;
- `group`, `github`, `history`, `attribution`, `grading` e `period`: domínio;
- `view`: dados prontos para a tela, iguais nas duas versões;
- `adapter.github`: o cliente HTTP do GitHub;
- `internal`: utilitários.

O grafo de dependência entre esses pacotes MUST NOT ter ciclos. `kong.unirest.*` e `com.google.gson.*` SHALL aparecer só em `core.adapter`. Os casos de uso SHALL depender do GitHub só pela porta `GitHubGateway`.

#### Scenario: Ciclo entre pacotes
- **WHEN** uma classe de `core.port` passa a importar uma classe de `core.adapter.github`
- **THEN** o teste de arquitetura falha e mostra o ciclo

#### Scenario: Gson no domínio
- **WHEN** alguém anota um campo de `core.github.Contributor` com `@SerializedName`
- **THEN** o teste de arquitetura falha e aponta o arquivo

### Requirement: Convenções de nomes
A library SHALL seguir estas convenções:
- **Casos de uso que alteram estado:** substantivo do assunto (`GroupManagement`, `Membership`, `IdentityLinking`).
- **Casos de uso só de leitura:** sufixo `Query` (`ContributionsQuery`, `CommitHistoryQuery`, `ClassGradesQuery`).
- **Portas de persistência:** sufixo `Repository`.
- **Porta de sistema externo:** sufixo `Gateway`.
- **Clientes HTTP:** sufixo `Client`.
- **Login e ids:** o login do GitHub SHALL se chamar `login`; os ids SHALL ser `long`.
- **`of`:** SHALL ser usado só em fábricas estáticas.
- **Proibido:** nomes de DTO HTTP (`RequestBody`, `ResponseBody`) MUST NOT aparecer no domínio.

#### Scenario: Leitura de notas da turma
- **WHEN** uma aplicação pede as notas de um grupo
- **THEN** chama `ClassGradesQuery.grades(groupId, accessToken)` e procura um aluno com `GroupGrades.student(login)`

### Requirement: Relatório calculado no domínio
O relatório de contribuições, o recorte de um período e a atribuição dos commits SHALL ser calculados por `attribution.ContributionAnalyzer` a partir de um `RepositoryHistory`. O cliente do GitHub SHALL só ler e guardar em cache o histórico, os contribuidores, o repositório e o usuário. Os números, as notas e as atribuições SHALL continuar os mesmos de antes.

#### Scenario: Mesmo relatório
- **WHEN** os testes do relatório, da atribuição e das notas rodam com o `ContributionAnalyzer`
- **THEN** os números, as notas e as atribuições são os mesmos que o `GitHubProxy` calculava

