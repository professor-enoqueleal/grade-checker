## RENAMED Requirements

- FROM: `### Requirement: Porta do diretório do GitHub`
- TO: `### Requirement: Porta do GitHub`

## MODIFIED Requirements

### Requirement: Portas de persistência
A library SHALL definir, em `br.com.gradechecker.core.port`, as interfaces `GroupRepository`, `MemberRepository` e `IdentityAliasRepository`, com as operações de grupos, membros e associações manuais usadas pelos casos de uso. Cada aplicação SHALL implementá-las com a sua persistência. Os logins dos membros SHALL ser lidos por `MemberRepository.findLoginsByGroupId`. A atomicidade SHALL ser garantida pelo adaptador:
- criar ou editar um grupo reserva a URL do repositório e lança `RepositoryTakenException` quando outro grupo a usa;
- trocar o login de um membro leva junto as suas associações manuais.

#### Scenario: Adaptadores diferentes, mesma regra
- **WHEN** os casos de uso rodam com as portas em memória dos testes da library e, na Lambda, com os adaptadores do DynamoDB
- **THEN** as regras e as respostas são as mesmas

### Requirement: Porta do GitHub
A library SHALL definir a porta `GitHubGateway`, com:
- `getRepository(owner, repo, accessToken)` e `getUser(login, accessToken)`, que devolvem `null` quando o repositório ou o usuário não existe ou não está acessível;
- `getContributors(owner, repo, accessToken)`;
- `getHistory(HistoryRequest)`, com os commits do branch padrão no período.

As leituras que falham SHALL lançar `GitHubApiException`. O `adapter.github.GitHubClient` SHALL implementá-la.

#### Scenario: Testes sem GitHub
- **WHEN** os testes dos casos de uso usam um `GitHubGateway` em memória
- **THEN** nenhuma chamada ao GitHub é feita
