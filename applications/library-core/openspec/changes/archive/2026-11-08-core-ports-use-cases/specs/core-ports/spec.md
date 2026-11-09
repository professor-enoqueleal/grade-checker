## ADDED Requirements

### Requirement: Portas de persistência
A library SHALL definir, em `br.com.gradechecker.core.port`, as interfaces `GroupRepository`, `MemberRepository` e `IdentityAliasRepository`, com as operações de grupos, membros e associações manuais usadas pelos casos de uso. Cada aplicação SHALL implementá-las com a sua persistência. A atomicidade SHALL ser garantida pelo adaptador:
- criar ou editar um grupo reserva a URL do repositório e lança `RepositoryTakenException` quando outro grupo a usa;
- trocar o login de um membro leva junto as suas associações manuais.

#### Scenario: Adaptadores diferentes, mesma regra
- **WHEN** os casos de uso rodam com as portas em memória dos testes da library e, na Lambda, com os adaptadores do DynamoDB
- **THEN** as regras e as respostas são as mesmas

### Requirement: Porta do diretório do GitHub
A library SHALL definir a porta `GitHubDirectory`, com o repositório (`getRepository`) e o usuário (`getUser`) do GitHub, que devolvem `null` quando não existem ou não estão acessíveis. O `GitHubProxy` SHALL implementá-la.

#### Scenario: Testes sem GitHub
- **WHEN** os testes dos casos de uso usam um `GitHubDirectory` em memória
- **THEN** nenhuma chamada ao GitHub é feita
