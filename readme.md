# Grade Checker

![Build Status](https://github.com/professor-enoqueleal/grade-checker/actions/workflows/maven.yml/badge.svg)

[Log4j 2 Pattern Layout](https://logging.apache.org/log4j/2.x/manual/pattern-layout.html) (configuração em `src/main/resources/log4j2.xml`)

## Setting environment variables for Linux

```shell
export GITHUB_CLIENT_ID=your-client-id
export GITHUB_CLIENT_SECRET=your-client-secret
export GITHUB_BASE_URL=https://github.com
export GRADE_CHECKER_ADMINS=your-github-login
```

## Setting environment variables for Windows

```shell
set GITHUB_CLIENT_ID=your-client-id
set GITHUB_CLIENT_SECRET=your-client-secret
set GITHUB_BASE_URL=https://github.com
set GRADE_CHECKER_ADMINS=your-github-login
```

## Executar localmente

O uso padrão é **na sua máquina, em `http://localhost:8080`**:

```shell
sh ./mvnw jetty:run        # ou rode pelo IntelliJ (Jetty na porta 8080)
```

- Defina antes as variáveis acima (`GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GRADE_CHECKER_ADMINS`). Sem `GRADE_CHECKER_ADMINS`, ninguém vê membros, associações nem as notas da turma.
- **Banco:** H2 em arquivo, em `~/h2db/grade-checker`. Para usar outro, defina `DB_URL`, `DB_USER` e `DB_PASS`.
- **`db.sql`:** roda a cada início e cria só o que falta. Os `DROP TABLE` do começo do arquivo **precisam ficar comentados**: ativos, cada início apaga o banco inteiro (grupos, membros e associações), e dois testes unitários falham para avisar.
- **Console do H2:** `http://localhost:8080/console`, só a partir da própria máquina (veja "Limitações conhecidas" em `openspec/architecture.md`).
- Os gráficos carregam o Chart.js de `cdn.jsdelivr.net`, então a página precisa de internet para desenhá-los.

## Set execution permissions

To execute commands *maven wrapper*, permissions are necessary. To set permissions for execution, using this command:

```shell
$ chmod +x mvnw
```

---

## GitHub App (login com GitHub)

O botão **Continue with GitHub** usa um **GitHub App** (client ID `Iv1.…`), e não um OAuth App clássico. O botão aponta para `GET /login/github`. Essa rota monta a URL de autorização com o **`GITHUB_CLIENT_ID` do ambiente** (nada fica fixo na página) e um `state` aleatório de uso único, que o `/callback` valida contra login CSRF. Para trocar de app, basta mudar `GITHUB_CLIENT_ID` e `GITHUB_CLIENT_SECRET` e reiniciar.

- **O app precisa ser público** (GitHub → Settings → Developer settings → GitHub Apps → o app → Advanced → *Make public*). Um GitHub App privado só autoriza a conta dona. Os demais usuários recebem o **404** do GitHub ("This is not the web page you are looking for") na tela de autorização.
- **Callback URL:** `http://localhost:8080/callback`. O login não envia `redirect_uri`, então o GitHub sempre volta para a **primeira** callback URL cadastrada no app: deixe a de localhost em primeiro lugar. Só se for usar a EC2, coloque em primeiro a URL do output `OAuthCallbackUrl` da stack (e volte a de localhost para o topo depois).
- **Permissões:** leitura de repositório (*Contents: read*, *Metadata: read*). Repositórios públicos dos alunos são lidos com o token do usuário; repositórios privados exigem o app instalado neles.

---

## Configuração da JVM

Os **testes** precisam que os módulos não nomeados acessem o pacote `java.util`. No Maven isso já está no `pom.xml` (`maven-surefire-plugin`); para rodar os testes pela IDE, adicione à configuração da JVM do teste:

```
--add-opens java.base/java.util=ALL-UNNAMED
```

A aplicação em si (IntelliJ, Docker e EC2) não precisa dessa opção.

---

## Configuração do Cache em memória (Caffeine)

A aplicação possui um cache em memória (Caffeine) configurável por variáveis de ambiente. As variáveis disponíveis são:

- `GCACHE_ENABLED` — habilita ou desabilita o cache (boolean). Default: `true`.
- `GCACHE_TTL_SECONDS` — tempo de expiração das entradas do cache em segundos (integer). Default: `60`.
- `GCACHE_MAX_SIZE` — tamanho máximo de entradas no cache (integer). Default: `10000`.

Exemplos (Linux):

```shell
# habilitar cache com TTL de 120 segundos e máximo de 5000 entradas
export GCACHE_ENABLED=true
export GCACHE_TTL_SECONDS=120
export GCACHE_MAX_SIZE=5000

# executar testes com override temporário
GCACHE_ENABLED=false GCACHE_TTL_SECONDS=10 ./mvnw test
```

Exemplos (Windows - PowerShell/CMD):

```powershell
# PowerShell
$env:GCACHE_ENABLED = "true"
$env:GCACHE_TTL_SECONDS = "120"
$env:GCACHE_MAX_SIZE = "5000"

# CMD
set GCACHE_ENABLED=true
set GCACHE_TTL_SECONDS=120
set GCACHE_MAX_SIZE=5000
```

Notas e recomendações

- As chaves em cache incluem um hash do token (para evitar vazamento entre tokens diferentes).
- Use TTLs menores para consultas altamente voláteis (por exemplo, listagens com alta frequência de alteração) e TTLs maiores para dados imutáveis (por exemplo, detalhes de commit).
- Para desativar o cache em ambientes de teste/local, defina `GCACHE_ENABLED=false`.
- Se precisar invalidar o cache manualmente em runtime, a aplicação expõe uma API administrativa (ex.: `POST /admin/cache`) que permite verificar estatísticas, invalidar chaves e reconstruir o cache.

---

### Cache: rollout e rollback

- **Desligar sem reiniciar:** `POST /admin/cache` com `action=rebuild&enabled=false` (exige `GCACHE_ADMIN_SECRET` configurado no servidor).
- **Desligar de forma persistente na EC2:** acrescente `GCACHE_ENABLED=false` em `/etc/grade-checker/grade-checker.env` e rode `sudo systemctl restart grade-checker`.
- **Acompanhar:** `GET /admin/cache/stats` (hits/misses).
- Sem `GCACHE_ADMIN_SECRET`, a API administrativa responde 403 a tudo.
  - **Docker:** acrescente `GCACHE_ADMIN_SECRET=<token com 16+ caracteres>` ao `.env.local`.
  - **AWS:** informe o parâmetro opcional `CacheAdminSecret` ao criar a stack. Ele é guardado no Secrets Manager e escrito no arquivo de ambiente da instância, e o output `CacheAdminApi` mostra se a API ficou habilitada.

---

## Dashboard de contribuições (GitHub GraphQL)

Ao abrir a tela de detalhe do grupo, o dashboard é carregado automaticamente (período All) chamando `GET /contributors-stats?owner=&repo=`. Esse endpoint consulta a **API GraphQL do GitHub** (`POST https://api.github.com/graphql`) e devolve, por autor, commits, adições, remoções e porcentagem das linhas alteradas no período. Esses números preenchem a tabela e os gráficos (spec: `openspec/specs/contributions-dashboard`).

- **Período:** parâmetro `period` (`all`, `m1`, `m2`, `m3` ou `custom`). **All** (padrão) é o histórico inteiro, sem data de início. `date-since` / `date-until` só valem com `period=custom` (veja [Períodos de avaliação](#períodos-de-avaliação-marcos)).
- **Escopo:** commits da branch padrão; merges não contam.
- **Custo:** ~1 chamada GraphQL para cada 100 commits do período (~1 ponto do limite de 5.000/h por token). Os resultados vão para o cache.
- **Limite:** `GH_DASHBOARD_MAX_COMMITS` (padrão `1000`). Acima disso, a resposta vem com `truncated: true` e a tela avisa.
- **Token:** o GraphQL sempre exige token. Para repositórios privados, o PAT clássico precisa do escopo `repo`, e o fine-grained precisa de *Contents: read*.
- **Gráficos** (abaixo da tabela, barras horizontais na ordem da tabela, Chart.js 4.4.1 com versão fixa; change `graficos-e-explicacao-da-nota`):
  - **notas** (All e marcos, só alunos): no All, M1 + M2 + M3 empilhadas até o Total (0–9); num marco, a nota do marco (0–3). Cada marco tem sempre a mesma cor (tons de azul, do claro ao escuro);
  - **volume** (todos os períodos, todos os autores): Additions (laranja) + Deletions (violeta). Identidades sem conta aparecem com " (no account)".
- **Explicação da nota:** no fim da página, a caixa **"Como as notas são calculadas"**, em português (exceção à interface em inglês, porque é para os alunos), visível para todos. Os números (pesos, teto por commit, tolerância, datas dos marcos e o exemplo com 4 alunos) vêm de `grading.properties` e `milestones.properties`: mudou a configuração e reiniciou, o texto acompanha.

```bash
export GH_DASHBOARD_MAX_COMMITS=2000
```

Erros do GitHub voltam como JSON `{"error": "...", "message": "..."}` e são exibidos na tela:

| Status | `error` | Quando |
|---|---|---|
| 400 | `bad_request` | `owner` ou `repo` ausentes |
| 401 | `github_unauthorized` | token inválido ou expirado |
| 403 | `github_forbidden` | token sem permissão no repositório |
| 404 | `github_not_found` | repositório inexistente ou sem acesso |
| 429 | `github_rate_limited` | limite de requisições do GitHub esgotado |
| 502 | `github_error` | outro erro ou falha de rede |

---

## Nota de contribuição por marco

Cada marco vale **3 pontos** (Total máximo **9**). A nota mede o **equilíbrio da contribuição no grupo, só com o que está no git** (spec: `openspec/specs/milestone-grading`):

```
nota do marco = 3 × min(1, (participação no índice ÷ parte justa) ÷ 0,8)      parte justa = 1 ÷ nº de alunos
```

- **Índice de contribuição efetiva** = 50% linhas efetivas + 30% dias ativos + 20% commits relevantes, cada um como fatia do grupo:
  - **linhas efetivas:** teto de 400 linhas por commit; reorganização (30 ou mais arquivos, com adições ≈ remoções) conta 10%;
  - **dias ativos:** dias distintos com commit, no fuso de São Paulo;
  - **commits relevantes:** commits com 20 linhas ou mais.
- **Exemplos:** com 4 alunos contribuindo 25% cada, todos tiram **3**. Quem fez mais que a parte justa também tira 3 (teto). Quem fez menos que 80% da parte justa perde pontos na proporção.
- **Alunos** = membros do grupo, menos bots e os logins de `grading.excludedLogins` (o professor). Um membro sem commits no marco tira **0**. Veja [Membros do grupo](#membros-do-grupo-somente-administradores).
- **Commits que não contam:** os de identidades sem conta (selo "No GitHub account") **não contam** para nenhum aluno, e a tela avisa quantos ficaram de fora. Por isso é importante que os alunos sigam a orientação da seção abaixo. Exemplo real: no grupo 6, dois alunos têm M1 = 0 porque os commits do Marco 1 foram feitos com o e-mail institucional não cadastrado.
- **Onde aparece:** na tabela da página de detalhe do grupo.
  - **Milestone 1/2/3:** coluna "Grade (0–3)", com o detalhamento na dica.
  - **All:** colunas M1/M2/M3/Total.
  - Um marco em andamento aparece como "(in progress)".
- **Ajustes:** pesos, tolerância, teto e regras ficam em `src/main/resources/grading.properties`. Depois de mudar, reinicie a aplicação (no Docker, rebuild da imagem).

### Notas da turma (somente administradores)

A página **`/grades`** ("Class grades", link na navbar) lista **todos os alunos de todos os grupos** com M1, M2, M3 e Total, com as mesmas notas do detalhe de cada grupo no período All, incluindo as associações manuais (spec: `openspec/specs/class-gradebook`).

- **Acesso:** só os logins de `GRADE_CHECKER_ADMINS`. Para os outros usuários, `/grades`, `/grades/group` e `/grades.csv` respondem 403. As notas no detalhe de cada grupo continuam visíveis para todos.
- **Carga progressiva:** as notas são calculadas grupo a grupo (3 por vez). Um erro do GitHub aparece só nas linhas daquele grupo, e as demais continuam.
- **Tabela:** clique no cabeçalho para ordenar (notas ausentes ficam no fim); o campo de filtro procura por grupo ou aluno.
- **Export CSV** (`/grades.csv`): formato pt-BR (`;`, vírgula decimal, UTF-8 com BOM), que abre direto no Excel e no LibreOffice. Colunas: `group;student;m1;m2;m3;total;provisional;error`. Grupos sem alunos não geram linhas, e nomes que começam com `=`, `+`, `-` ou `@` recebem `'` (proteção contra fórmulas).
- **Custo:** sem cache, até 3 relatórios do GitHub por grupo. O cache (`GCACHE_TTL_SECONDS`) é compartilhado com o dashboard, então baixe o CSV logo depois de abrir a página.

---

## Navbar

Todas as páginas autenticadas têm a mesma navbar (spec: `openspec/specs/navigation-bar`): a marca **"Grade Checker"** (leva à home), os links de administrador **"Manage groups"** e **"Class grades"** (só para quem está em `GRADE_CHECKER_ADMINS`), o login do usuário e **"Sign out"**. No celular, os itens recolhem num botão de menu. O breadcrumb continua em todas as páginas, abaixo da navbar.

- **Um arquivo só:** `src/main/webapp/WEB-INF/tags/navbar.tag`. Cada página usa `<%@ taglib prefix="gc" tagdir="/WEB-INF/tags" %>` e `<gc:navbar/>` (ou `<gc:navbar active="grades"/>` / `active="groups"` para destacar o item atual). Um item novo no arquivo aparece em todas as páginas.
- **Dados da sessão:** no login (GitHub ou token), `AdminConfig.signIn` grava `userLogin` e `isAdmin` na sessão, e a navbar lê os dois. A flag só mostra ou esconde links: telas e APIs continuam verificando o administrador pelo login.
- As páginas com navbar precisam do JavaScript do Bootstrap (`bootstrap.min.js`) para o menu do celular, **uma única vez**: com duas cópias (por exemplo, `bootstrap.min.js` e `bootstrap.bundle.js`), o menu abre e não fecha. O `JspStructureTest` confere isso.

## Grupos (somente administradores)

A home é a tela dos alunos: ela só lista os grupos ativos. O administrador gerencia os grupos na tela **"Manage groups"** (`/groups`), aberta pelo link da navbar (spec: `openspec/specs/group-management`):

- **Lista:** id, nome, repositório (`dono/repositório`, com link) e quantidade de membros, com os links **"Edit"** e **"Archive"**. No celular, o repositório aparece abaixo do nome.
- **"Add a new group":** nome e URL do repositório (`https://github.com/dono/repositório`, com ou sem `.git`). O repositório é validado no GitHub com o seu token e gravado com a grafia do GitHub. URL inválida, repositório inexistente e repositório já cadastrado (inclusive num grupo arquivado) são recusados no próprio formulário.
- **Home vazia:** o link **"Add a new group"** no meio da tela (só para o administrador) abre essa tela já com o formulário de cadastro (`/groups?add=1`).
- **"Edit":** muda o nome e a URL. O grupo mantém o mesmo id, então membros, associações manuais e notas continuam. Ao trocar a URL, o formulário avisa que contribuidores e notas passam a vir do repositório novo.
- **"Archive":** o grupo sai da home, de `/grades` e do CSV, e `/detail` responde 404. Nada é apagado: ele fica em **"Archived groups"**, com **"Restore"**, que traz tudo de volta.
- **Seeds do `db.sql`:** um grupo arquivado continua no banco com a mesma URL, então o seed não o recria. **Mas**, se você trocar a URL de um grupo que veio de um seed, no próximo início o seed não acha mais a URL antiga e recria o grupo original: atualize o `db.sql` junto.
- API usada pela tela: `POST /groups/api` (`name`, `repositoryUrl`), `PUT /groups/api?id=&name=&repositoryUrl=`, `POST /groups/api/archive?id=` e `POST /groups/api/restore?id=`. Exige sessão de administrador e o header `X-Requested-With: XMLHttpRequest`. A antiga rota `/group/create` não existe mais.

## Membros do grupo (somente administradores)

Na página de detalhe do grupo e na tela **"Manage members"** (spec: `openspec/specs/group-members`):

- **Contribuidores** são as contas do GitHub com commits no repositório. Eles vêm sempre do GitHub (com o cache em memória) e não são gravados no banco. É o **Number of contributors**.
- **Membros** são os alunos do grupo, os que recebem nota. Só o administrador marca. É o **Number of members**, e um membro sem commits conta aqui, mas não em contributors.
- **Como marcar no início do semestre:** em cada grupo, use o link **"Mark"** (dica "Mark as member") no card de cada aluno. Para um aluno que ainda não commitou, use **"Manage members" → "Add a new member"** (nome e login do GitHub, validado no GitHub).
- **Tela "Manage members"** (`/group/members?id={id}`, link junto dos contadores; breadcrumb Home > Group {id} > Manage members): lista os membros (nome e login) com os links:
  - **"Add a new member"**: cadastra pelo nome e login do GitHub;
  - **"Edit"**: muda o nome e o login. O login novo é validado no GitHub, e as identidades associadas manualmente ao login antigo passam para o novo, então as notas não se perdem. Use para corrigir um login digitado errado;
  - **"Remove"**: remove o membro, depois de confirmar.
- **Cards "Contributors and members":** um card para cada contribuidor e, depois, um para cada membro que ainda não commitou ("Contributions: 0" e o selo "Member").
- **Desmarcar:** "Unmark" no card ou "Remove" na tela. A remoção é recusada se o aluno tiver identidades associadas manualmente; desfaça a associação antes ("Unlink").
- Os outros usuários veem o selo "Member" e os links "GitHub" e "Details", sem as ações de administrador; `/group/members` responde 403 para eles.
- O `db.sql` só precisa trazer os repositórios (grupos). Com um banco novo, os grupos começam **sem membros**, e o dashboard e `/grades` mostram "No students registered" até a marcação.
- API usada pelas telas: `POST /group-members` (`groupId`, `login`, `name` opcional), `PUT /group-members?groupId=&login=&newLogin=&name=` (edição) e `DELETE /group-members?groupId=&login=`. Exige sessão de administrador e o header `X-Requested-With: XMLHttpRequest`.

## Identidades dos alunos no dashboard

O GitHub liga cada commit à conta do aluno **pelo e-mail** configurado no git, e não pelo nome. Um commit feito com um e-mail que não está cadastrado na conta aparece no dashboard com o selo **"No GitHub account"**. A dica do selo mostra o nome e o e-mail usados.

O que o sistema já faz sozinho:
- um commit sem conta com o **mesmo e-mail** de um commit vinculado no mesmo repositório é somado ao aluno certo;
- commits sem conta com o mesmo e-mail viram uma única linha.

Ele **não** junta por semelhança de nome.

### Associação manual (somente administradores)

Quando não há pista automática (e-mail inválido como `=`, nome diferente do login, e-mail institucional nunca cadastrado), o **administrador** corrige pela tela (spec: `openspec/specs/identity-aliases`):

1. no dashboard do grupo (`/detail`), a linha "No GitHub account" tem o botão **Link to student**;
2. escolha o aluno na janela e confirme em **Link**: os commits dessa identidade passam a contar para ele em todos os períodos, inclusive nas notas;
3. a linha do aluno mostra "Linked manually: …" com a opção **Unlink**, que desfaz a associação.

- A associação vale **por grupo** e pela identidade inteira: o e-mail (quando válido) ou o nome do git.
- Commits já vinculados a uma conta nunca mudam de dono.
- O histórico de commits do aluno (`/contributions-detail`) usa a mesma atribuição do dashboard: lista os commits somados a ele (associações manuais e regras automáticas), com a coluna **Identity** e a linha **Also counted**. O **Total of commits** é igual à coluna Commits do dashboard no mesmo período (merges excluídos).
- Acima da tabela do histórico, um **gráfico de colunas** mostra a frequência de commits do aluno (por dia em períodos de até 62 dias, por semana nos maiores), colorido pelo marco, com o resumo "N commits · D active days". Usa os mesmos commits da tabela e o fuso dos marcos (change `grafico-frequencia-commits`).

**Administradores:** os logins do GitHub listados em `GRADE_CHECKER_ADMINS`, separados por vírgula. Sem a variável, ninguém vê os botões e a API responde 403. Os mesmos administradores gerenciam os [membros dos grupos](#membros-do-grupo-somente-administradores) e acessam as [notas da turma](#notas-da-turma-somente-administradores).

```properties
GRADE_CHECKER_ADMINS=professor-enoqueleal
```

O login do usuário é obtido do GitHub (`GET /user`) no momento do login, por GitHub ou por token; um token rejeitado pelo GitHub já é recusado na tela de login. Sessões abertas antes desta versão precisam de um novo login para serem reconhecidas como administrador.

```bash
# API usada pela tela (exige sessão de administrador e o header X-Requested-With)
curl -b cookies.txt -H "X-Requested-With: XMLHttpRequest" -X POST "$BASE_URL/identity-aliases" \
  -d groupId=6 -d identityKey="email:aluno@senacsp.edu.br" -d login=aluno-github
curl -b cookies.txt -H "X-Requested-With: XMLHttpRequest" -X DELETE \
  "$BASE_URL/identity-aliases?groupId=6&identityKey=email:aluno@senacsp.edu.br"
```

**Orientação para repassar aos alunos:**

1. **Cadastrar o e-mail institucional na conta do GitHub:** GitHub → *Settings* → *Emails* → *Add email address*, e confirmar pelo link recebido. Depois disso, **o GitHub liga automaticamente os commits antigos** feitos com esse e-mail, inclusive os dos computadores do laboratório.
2. **Conferir a configuração do git** em cada computador usado:
   ```bash
   git config --global user.name  "Nome Sobrenome"
   git config --global user.email "email-cadastrado-no-github@exemplo.com"
   git config --global user.email   # confere o valor atual
   ```
   Valores como `=` ou um e-mail digitado errado geram commits que **nunca** são atribuídos ao aluno.

---

## Períodos de avaliação (marcos)

As telas de commits (`/contributions-detail`) e o dashboard do grupo (`/detail`) têm os botões **Milestone 1**, **Milestone 2**, **Milestone 3** e **All**, além de um período **Custom** (spec: `openspec/specs/milestone-period-filter`). **All** cobre o histórico inteiro do repositório e é o período usado ao abrir as telas.

As datas ficam em `src/main/resources/milestones.properties` (dias inteiros e inclusivos, no fuso configurado):

```properties
milestone.timezone=America/Sao_Paulo
milestone.1.label=Milestone 1
milestone.1.start=2026-08-13
milestone.1.end=2026-09-17
```

- **Trocar de semestre:** edite as datas e reinicie a aplicação.
- Um marco com data inválida, ou com início depois do fim, fica desabilitado como "Not configured", e um aviso vai para o log.
- Um marco que ainda não começou também aparece desabilitado.
- As rotas aceitam `period=m1|m2|m3|all|custom`. Com `custom`, `date-since` e `date-until` sem fuso são interpretados no `milestone.timezone`.

```bash
curl -b cookies.txt "$BASE_URL/contributors-stats?owner=OWNER&repo=REPO&period=m2"
```

---

## Docker (aplicação para testes)

O `Dockerfile` segue os mesmos passos do template CloudFormation:
- Amazon Corretto 17;
- build com `sh ./mvnw -DskipTests` (testes pulados para agilizar);
- Jetty 11.0.26 baixado do Maven Central;
- WAR publicado como `ROOT.war`;
- usuário sem privilégios de root.

A imagem é montada a partir do **código local** (não faz `git clone`), então serve para testar o que está em desenvolvimento. O container usa a **porta 8080**, a mesma do IntelliJ: rode um ou outro, não os dois ao mesmo tempo. A URL de callback do GitHub App (`http://localhost:8080/callback`) serve para os dois.

### 1. Arquivo `.env.local` (na raiz do projeto)

Guarda as credenciais do GitHub App. **Não vai para o git** (está no `.gitignore`):

```properties
GITHUB_CLIENT_ID=Iv1.xxxxxxxxxxxxxxxx
GITHUB_CLIENT_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
# administradores da plataforma (membros dos grupos, associação manual de identidades e notas da turma)
GRADE_CHECKER_ADMINS=professor-enoqueleal
# opcional (padrão https://github.com)
# GITHUB_BASE_URL=https://github.com
# opcional (padrão https://api.github.com), usado no GET /user do login
# GITHUB_API_URL=https://api.github.com
```

### 2. Build da imagem

```bash
docker build -t grade-checker .
```

### 3. Run do container usando o `.env.local`

```bash
docker rm -f grade-checker 2>/dev/null   # remove um container anterior, se existir
docker run -d --name grade-checker -p 8080:8080 --env-file .env.local \
  -v grade-checker-data:/var/lib/grade-checker/h2db grade-checker
```

O volume `grade-checker-data` guarda o banco H2: grupos, membros e associações manuais sobrevivem a `docker rm` e a um novo build. Para começar do zero: `docker volume rm grade-checker-data`.

### Comandos úteis

```bash
docker ps --filter name=grade-checker   # STATUS deve ficar "(healthy)"
docker logs -f grade-checker            # logs do Jetty
docker rm -f grade-checker              # parar e remover
```

- Aplicação em `http://localhost:8080/`, com healthcheck em `/login`.
- O banco H2 é preservado entre reinícios (o `db.sql` é idempotente: cria só o que falta e nunca apaga dados).
- **Login com GitHub no container:** a primeira URL de callback do GitHub App precisa ser `http://localhost:8080/callback`.
- **Testes E2E contra o container:** `cd e2e && npm run test:docker`. O script lê o `GITHUB_CLIENT_ID` do mesmo `.env.local` para conferir o redirecionamento de `/login/github`.

---

## AWS (CloudFormation)

O template `infra/cloudformation/grade-checker-ec2.yaml` cria uma EC2 (Amazon Linux 2023) com Elastic IP, gera o WAR na instância e roda o Jetty como serviço systemd (spec: `openspec/specs/ec2-deployment`). Os parâmetros viram variáveis do arquivo `/etc/grade-checker/grade-checker.env`:

| Parâmetro | Variável | Obrigatório |
|---|---|---|
| `GitHubBaseUrl` | `GITHUB_BASE_URL` | sim (padrão `https://github.com`) |
| `GitHubClientId` | `GITHUB_CLIENT_ID` | sim |
| `GitHubClientSecret` | `GITHUB_CLIENT_SECRET` (via Secrets Manager) | sim |
| `GradeCheckerAdmins` | `GRADE_CHECKER_ADMINS` | não (vazio = ninguém é administrador: sem membros, associações e notas da turma) |
| `CacheAdminSecret` | `GCACHE_ADMIN_SECRET` (via Secrets Manager) | não |
| `GitHubApiUrl` | `GITHUB_API_URL` | não (padrão `https://api.github.com`) |
| `CacheEnabled` | `GCACHE_ENABLED` | não (padrão `true`) |
| `CacheTtlSeconds` | `GCACHE_TTL_SECONDS` | não (padrão `60`) |
| `CacheMaxSize` | `GCACHE_MAX_SIZE` | não (padrão `10000`) |
| `DashboardMaxCommits` | `GH_DASHBOARD_MAX_COMMITS` | não (padrão `1000`) |

Parâmetros opcionais vazios não são escritos no arquivo, e a aplicação usa o padrão. O banco H2 fica na instância (`/var/lib/grade-checker/h2db`), então `DB_URL`, `DB_USER` e `DB_PASS` não são parâmetros.

**Mudar uma configuração depois de criar a stack** (por exemplo, os administradores): o arquivo é escrito só no primeiro boot, e atualizar os parâmetros da stack não o reescreve. Edite o arquivo na instância (Session Manager) e reinicie o serviço, como mostra o output `ChangeSettings`:

```bash
sudo vi /etc/grade-checker/grade-checker.env
sudo systemctl restart grade-checker
```

Para publicar uma nova versão do código: `sudo grade-checker-redeploy` (o banco é mantido).

---

## Testes E2E

Há uma suíte Playwright em `e2e/` (veja `e2e/README.md`):

```bash
cd e2e && npm install && npm run test:local
```

---

## Admin Cache API — exemplos curl

A API administrativa fica em `/admin/cache` (POST, ação no parâmetro `action`) e `/admin/cache/stats` (GET, somente leitura). Ela tem autenticação própria: o header `X-ADMIN-TOKEN` precisa ter o mesmo valor da variável de ambiente `GCACHE_ADMIN_SECRET`. **Não é preciso sessão de login.** Essas duas rotas ficam fora do filtro de autenticação (spec: `openspec/specs/route-protection`).

Respostas de erro:
- `403 {"error":"forbidden"}`: header ausente ou incorreto, ou `GCACHE_ADMIN_SECRET` não definida no servidor.
- `400`: `action` desconhecida ou `invalidate` sem `key`.

Os exemplos usam `http://localhost:8080`, a base padrão do `mvn jetty:run` (context path `/`). Ajuste `BASE_URL` para o seu deploy:

```bash
export BASE_URL="http://localhost:8080"
export GCACHE_ADMIN_SECRET="defina-um-segredo"   # o mesmo valor configurado no servidor
```

- Obter estatísticas do cache (hits/misses/estado) via GET:

```bash
curl -H "X-ADMIN-TOKEN: $GCACHE_ADMIN_SECRET" \
  "$BASE_URL/admin/cache/stats"
```

- Obter estatísticas via POST (`stats` é a ação padrão quando `action` é omitida):

```bash
curl -X POST \
  -H "X-ADMIN-TOKEN: $GCACHE_ADMIN_SECRET" \
  -d "action=stats" \
  "$BASE_URL/admin/cache"
```

- Invalidar uma chave específica:

```bash
curl -X POST \
  -H "X-ADMIN-TOKEN: $GCACHE_ADMIN_SECRET" \
  -d "action=invalidate" \
  --data-urlencode "key=github.contributors::octocat:hello-world:ABCDEFHASH" \
  "$BASE_URL/admin/cache"
```

- Invalidar todo o cache:

```bash
curl -X POST \
  -H "X-ADMIN-TOKEN: $GCACHE_ADMIN_SECRET" \
  -d "action=invalidateAll" \
  "$BASE_URL/admin/cache"
```

- Rebuild do cache (habilitar/desabilitar e ajustar TTL e tamanho máximo em runtime):

```bash
curl -X POST \
  -H "X-ADMIN-TOKEN: $GCACHE_ADMIN_SECRET" \
  -d "action=rebuild" \
  -d "enabled=true" \
  -d "ttl=120" \
  -d "max=5000" \
  "$BASE_URL/admin/cache"
```

Notas de segurança rápidas:
- Defina `GCACHE_ADMIN_SECRET` no ambiente do servidor (não deixe valor em branco).
- Exponha o endpoint apenas em redes confiáveis (localhost, VPN) ou proteja via proxy autenticação adicional.
