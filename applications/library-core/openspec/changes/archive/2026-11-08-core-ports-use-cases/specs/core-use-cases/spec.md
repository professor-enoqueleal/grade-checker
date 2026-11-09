## ADDED Requirements

### Requirement: Casos de uso compartilhados
A library SHALL oferecer, em `br.com.gradechecker.core.application`, os casos de uso `GroupManagement`, `Membership`, `IdentityLinking`, `ContributionsQuery`, `CommitHistoryQuery` e `ClassGrades`, que recebem as portas no construtor e devolvem objetos do domínio ou de resultado, sem JSON e sem HTTP. As regras SHALL ser as de hoje:
- **Grupos:** uma URL de repositório pertence a um só grupo, ativo ou arquivado; a duplicata é conferida com a URL digitada e com a grafia devolvida pelo GitHub; o grupo é gravado com essa grafia.
- **Membros:**
  - o login é validado pelo formato e precisa existir no GitHub;
  - um login não é membro duas vezes, sem diferenciar maiúsculas;
  - não se remove um aluno que tem identidade associada.
- **Associações:** só podem apontar para um aluno do grupo (sem bots e sem os logins excluídos), com chave `email:` ou `name:`.
- **Relatório:**
  - notas do marco ou de All, com os alunos sem commits listados com zeros;
  - sem notas no período personalizado.
- **Histórico:** um período inválido mostra todo o histórico, com aviso.
- **Grupos arquivados** não são encontrados pelos casos de uso de leitura, edição, membros, relatório e notas.

#### Scenario: Mesmo comportamento da API
- **WHEN** a Lambda passa a chamar os casos de uso no lugar das regras que estavam nas rotas
- **THEN** o `ApiIT` e as suítes E2E de administrador e de aluno passam sem mudanças nas asserções

#### Scenario: Remover aluno com associação
- **WHEN** `Membership.remove` é chamado para um aluno com identidade associada
- **THEN** lança `CONFLICT` com o código `has_aliases`, e o membro continua no grupo
