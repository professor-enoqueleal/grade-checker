# Configuração

## Variáveis de ambiente

| Variável | Uso | Padrão |
|---|---|---|
| `GITHUB_CLIENT_ID` | client ID do GitHub App | obrigatória |
| `GITHUB_CLIENT_SECRET` | client secret do GitHub App | obrigatória |
| `GITHUB_BASE_URL` | URL do GitHub usada no login | `https://github.com` |
| `GITHUB_API_URL` | API do GitHub, usada no `GET /user` do login | `https://api.github.com` |
| `GRADE_CHECKER_ADMINS` | logins dos administradores, separados por vírgula | vazio (ninguém é administrador) |
| `DB_URL`, `DB_USER`, `DB_PASS` | banco de dados | H2 em `~/h2db/grade-checker` |
| `GH_DASHBOARD_MAX_COMMITS` | limite de commits lidos por relatório | `1000` |
| `GCACHE_ENABLED` | liga ou desliga o cache | `true` |
| `GCACHE_TTL_SECONDS` | tempo de vida das entradas do cache | `60` |
| `GCACHE_MAX_SIZE` | número máximo de entradas do cache | `10000` |
| `GCACHE_ADMIN_SECRET` | token da API administrativa do cache | vazio (API desligada) |

## GitHub App (login com GitHub)

O botão **Continue with GitHub** usa um **GitHub App** (client ID `Iv1.…`), e não um OAuth App clássico. O botão aponta para `GET /login/github`. Essa rota monta a URL de autorização com o **`GITHUB_CLIENT_ID` do ambiente** (nada fica fixo na página) e um `state` aleatório de uso único, que o `/callback` valida contra login CSRF. Para trocar de app, basta mudar `GITHUB_CLIENT_ID` e `GITHUB_CLIENT_SECRET` e reiniciar.

- **O app precisa ser público** (GitHub → Settings → Developer settings → GitHub Apps → o app → Advanced → *Make public*). Um GitHub App privado só autoriza a conta dona. Os demais usuários recebem o **404** do GitHub ("This is not the web page you are looking for") na tela de autorização.
- **Callback URL:** `http://localhost:8080/callback`. O login não envia `redirect_uri`, então o GitHub sempre volta para a **primeira** callback URL cadastrada no app. Em produção, coloque em primeiro lugar a URL do servidor.
- **Permissões:** leitura de repositório (*Contents: read*, *Metadata: read*). Repositórios públicos dos alunos são lidos com o token do usuário; repositórios privados exigem o app instalado neles.

## Administradores

Os administradores são os logins do GitHub listados em `GRADE_CHECKER_ADMINS`, separados por vírgula. Eles gerenciam grupos e membros, associam identidades e acessam as notas da turma. Sem a variável, ninguém vê esses botões e as APIs respondem 403.

```properties
GRADE_CHECKER_ADMINS=professor-enoqueleal
```

O login do usuário é obtido do GitHub (`GET /user`) no momento do login, por GitHub ou por token. Um token rejeitado pelo GitHub já é recusado na tela de login.

## Períodos de avaliação (marcos)

As datas ficam em `applications/monolith/src/main/resources/milestones.properties` (dias inteiros e inclusivos, no fuso configurado):

```properties
milestone.timezone=America/Sao_Paulo
milestone.1.label=Milestone 1
milestone.1.start=2026-08-13
milestone.1.end=2026-09-17
```

- **Trocar de semestre:** edite as datas e reinicie a aplicação.
- Um marco com data inválida, ou com início depois do fim, fica desabilitado como "Not configured", e um aviso vai para o log.
- Um marco que ainda não começou também aparece desabilitado.
- **All** cobre o histórico inteiro do repositório e é o período usado ao abrir as telas.

## Nota de contribuição por marco

```
nota do marco = 3 × min(1, (participação no índice ÷ parte justa) ÷ 0,8)      parte justa = 1 ÷ nº de alunos
```

- **Índice de contribuição efetiva** = 50% linhas efetivas + 30% dias ativos + 20% commits relevantes, cada um como fatia do grupo:
  - **linhas efetivas:** teto de 400 linhas por commit; reorganização (30 ou mais arquivos, com adições ≈ remoções) conta 10%;
  - **dias ativos:** dias distintos com commit, no fuso de São Paulo;
  - **commits relevantes:** commits com 20 linhas ou mais.
- **Alunos** = membros do grupo, menos bots e os logins de `grading.excludedLogins` (o professor). Um membro sem commits no marco tira **0**.
- **Commits que não contam:** os de identidades sem conta (selo "No GitHub account") não contam para nenhum aluno, e a tela avisa quantos ficaram de fora. O administrador pode associá-los a um aluno.
- **Ajustes:** pesos, tolerância, teto e regras ficam em `applications/monolith/src/main/resources/grading.properties`. Depois de mudar, reinicie a aplicação (no Docker, rebuild da imagem).
- **Explicação da nota:** a caixa "Como as notas são calculadas" (em português, porque é para os alunos) usa os números de `grading.properties` e `milestones.properties`. Mudou a configuração e reiniciou, o texto acompanha.

## Atribuição de commits aos alunos

O GitHub liga cada commit à conta do aluno pelo e-mail configurado no git. O sistema também faz sozinho:

- um commit sem conta com o **mesmo e-mail** de um commit vinculado no mesmo repositório é somado ao aluno certo;
- commits sem conta com o mesmo e-mail viram uma única linha.

Ele **não** junta por semelhança de nome. Nos demais casos, o administrador usa a associação manual:

- a associação vale **por grupo** e pela identidade inteira: o e-mail (quando válido) ou o nome do git;
- commits já vinculados a uma conta nunca mudam de dono;
- o histórico de commits do aluno usa a mesma atribuição do dashboard, então o total de commits é igual à coluna Commits do dashboard no mesmo período (merges excluídos).

## Dashboard de contribuições

O dashboard consulta a **API GraphQL do GitHub** e devolve, por autor, commits, adições, remoções e a porcentagem das linhas alteradas no período.

- **Escopo:** commits da branch padrão; merges não contam.
- **Custo:** cerca de 1 chamada GraphQL para cada 100 commits do período (cerca de 1 ponto do limite de 5.000/h por token). Os resultados vão para o cache.
- **Limite:** `GH_DASHBOARD_MAX_COMMITS` (padrão `1000`). Acima disso, a tela avisa que o histórico foi truncado.
- **Token:** o GraphQL sempre exige token. Para repositórios privados, o PAT clássico precisa do escopo `repo`, e o fine-grained precisa de *Contents: read*.
- **Notas da turma:** são calculadas grupo a grupo (3 por vez); um erro do GitHub aparece só nas linhas daquele grupo. Sem cache, cada grupo custa até 3 relatórios do GitHub. O cache é compartilhado com o dashboard, então baixe o CSV logo depois de abrir a página.

```bash
export GH_DASHBOARD_MAX_COMMITS=2000
```

## Cache em memória (Caffeine)

O cache é configurado pelas variáveis `GCACHE_ENABLED`, `GCACHE_TTL_SECONDS` e `GCACHE_MAX_SIZE`.

```shell
# Linux: TTL de 120 segundos e no máximo 5000 entradas
export GCACHE_ENABLED=true
export GCACHE_TTL_SECONDS=120
export GCACHE_MAX_SIZE=5000

# testes com override temporário
GCACHE_ENABLED=false GCACHE_TTL_SECONDS=10 ./mvnw test
```

```powershell
# PowerShell
$env:GCACHE_ENABLED = "true"
$env:GCACHE_TTL_SECONDS = "120"
$env:GCACHE_MAX_SIZE = "5000"
```

```shell
# CMD
set GCACHE_ENABLED=true
set GCACHE_TTL_SECONDS=120
set GCACHE_MAX_SIZE=5000
```

- As chaves do cache incluem um hash do token, para evitar vazamento entre tokens diferentes.
- Use TTLs menores para dados que mudam muito e maiores para dados imutáveis (por exemplo, detalhes de commit).
- Para desativar o cache em testes ou localmente, defina `GCACHE_ENABLED=false`.
- Para consultar estatísticas ou invalidar o cache sem reiniciar, use a [API administrativa do cache](api.md#api-administrativa-do-cache).
