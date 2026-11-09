## Why

A change `core-library` levou o núcleo para a library, mas deixou de fora as portas de persistência, os casos de uso e os erros do domínio (fase 4 de `core/plan.md`). Por isso, as regras de grupos, membros e associações, e a montagem do relatório e do histórico, continuam dentro dos adaptadores: as rotas da Lambda e os servlets da Jetty. Cada versão reimplementa as regras, e as mensagens podem divergir.

## What Changes

- **Portas** (`core.port`): `GroupRepository`, `MemberRepository`, `IdentityAliasRepository` (persistência) e `GitHubDirectory` (repositório e usuário do GitHub), além do `ResultCache` que já existe. O `GitHubProxy` implementa o `GitHubDirectory`.
- **Erros do domínio** (`core.error`): `DomainException`, com tipo (`INVALID`, `NOT_FOUND`, `CONFLICT`), código estável e a mensagem em inglês de hoje.
- **Casos de uso** (`core.application`):
  - `GroupManagement`: grupos ativos e arquivados, detalhe com cartões, criação, edição, arquivamento e restauração;
  - `Membership`: membros, alunos, inclusão, edição e remoção;
  - `IdentityLinking`: associar e desassociar identidades;
  - `ContributionsQuery`: relatório com as notas;
  - `CommitHistoryQuery`: histórico de um login com o gráfico;
  - `ClassGrades`: notas da turma.
- **Fronteira:** o teste de arquitetura passa a exigir que `core.port`, `core.error` e os casos de uso não importem nada de infraestrutura.
- **Compatibilidade:** tudo é adicionado, nada é renomeado nem removido. A versão Jetty continua compilando sem mudanças e adota as portas numa change seguinte.
- **Versão:** a `1.0.0` ainda não foi publicada. Esta change entra nela, antes da primeira publicação.

## Capabilities

### New Capabilities
- `core-ports`: portas de persistência e do GitHub, implementadas pelas aplicações.
- `core-use-cases`: casos de uso compartilhados, com as regras de grupos, membros, associações, relatório, histórico e notas da turma.
- `core-errors`: erros do domínio com código estável.

### Modified Capabilities
(nenhuma)

## Impact

- `core/`: novos pacotes `port` (três interfaces novas mais `GitHubDirectory`), `error` e as classes novas de `application`, com testes contra portas em memória.
- Versão serverless: adota tudo nesta mesma rodada (change `backend-hexagonal` em `serverless/openspec`).
- Versão Jetty: sem mudança agora.
