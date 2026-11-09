# core-use-cases Specification

## Purpose
Casos de uso compartilhados pelas versões Jetty e serverless, com as regras de grupos, membros, associações, relatório, histórico e notas da turma.
## Requirements
### Requirement: Casos de uso compartilhados
A library SHALL oferecer, em `br.com.gradechecker.core.application`, os casos de uso `GroupManagement`, `Membership`, `IdentityLinking`, `ContributionsQuery`, `CommitHistoryQuery` e `ClassGradesQuery`. Eles SHALL receber no construtor as portas, o `PeriodResolver` e o `GradingConfig`, e devolver objetos do domínio ou de resultado, sem JSON e sem HTTP. As notas por marco SHALL ser calculadas por `MilestoneGrades`, montado pelos próprios casos de uso. As regras SHALL ser as de hoje:
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
- **Notas de All e da turma:** leem o histórico uma vez e recortam cada marco. Quando o histórico vem truncado, leem um histórico por marco.
- **Grupos arquivados** não são encontrados pelos casos de uso de leitura, edição, membros, relatório e notas.

#### Scenario: Mesmo comportamento da API
- **WHEN** a Lambda e a Jetty passam a usar os nomes novos dos casos de uso
- **THEN** o `ApiIT`, os testes das duas aplicações e as suítes E2E de administrador e de aluno passam sem mudanças nas asserções de contrato

#### Scenario: Remover aluno com associação
- **WHEN** `Membership.remove` é chamado para um aluno com identidade associada
- **THEN** lança `CONFLICT` com o código `has_aliases`, e o membro continua no grupo

