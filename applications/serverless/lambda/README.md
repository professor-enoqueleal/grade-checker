# Grade Checker — backend (Lambda)

Backend da versão serverless: **uma função Lambda** em Java 21 (SnapStart) atrás de uma **HTTP API** do API Gateway, que atende `/api/*` para a SPA (`applications/serverless/spa`), com **DynamoDB**. Ele é descrito no template AWS SAM `template.yaml`.

- **Specs:** `openspec/specs` da raiz do repositório (`serverless-backend-project`, `serverless-api-routing`, `serverless-session-auth`, `serverless-dynamodb-data`, `serverless-github-cache`, `serverless-groups-api`, `serverless-members-api`, `serverless-identity-aliases-api`, `serverless-contributions-api`, `serverless-gradebook-api`).
- **Contrato da API:** `applications/serverless/spa/README.md`.

## Pré-requisitos

| Ferramenta | Para quê | Como conferir |
|---|---|---|
| Java 21 e Maven 3.8+ | compilar e testar | `java -version`, `mvn -v` |
| **AWS SAM CLI 1.103 ou mais novo** | `sam build`, `sam local` (as versões antigas não conhecem o `java21`) | `sam --version` |
| Docker rodando | `sam local` e DynamoDB Local | `docker info` |
| AWS CLI | o script que cria as tabelas no DynamoDB Local | `aws --version` |

Se o `sam validate --lint` quebrar com `cannot import name 'REGISTRY_SCHEMAS'` depois de atualizar o SAM CLI por cima de uma versão antiga, apague `/usr/local/aws-sam-cli` e instale de novo.

Na primeira vez:

```sh
mvn -f ../../library-core/pom.xml install          # a library grade-checker-core (repita depois de mudar a library)
cp env.local.example.json env.local.json   # variáveis da Lambda no sam local (fora do Git)
```

O domínio e o cliente do GitHub vêm da library **`grade-checker-core`** (`applications/library-core`), compartilhada com a versão Jetty. O `mvn test` e o `sam build` usam a versão instalada no `~/.m2`. Sem a versão no `~/.m2`, ela vem do GitHub Packages, com um `~/.m2/settings.xml` com token `read:packages` (veja o README de `applications/library-core`); ou instale a do repositório com `mvn -f ../../library-core/pom.xml install`.

No `env.local.json`, coloque o seu login em `GRADE_CHECKER_ADMINS`. O GitHub App (`GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`) é opcional.

## Rodar o backend isolado (sem a SPA)

Todos os comandos são nesta pasta. Primeiro, o banco local:

```sh
sh scripts/local-dynamodb.sh     # DynamoDB Local na porta 8000 e as duas tabelas
sh scripts/seed.sh               # grupos do semestre (pode rodar de novo: não duplica)
```

Depois, escolha um dos jeitos:

| Jeito | Para quê |
|---|---|
| **A. API completa** (`sam local start-api`) | chamar as rotas pela HTTP API emulada, com `curl` ou outro cliente |
| **B. Uma invocação** (`sam local invoke`) | rodar a Lambda uma vez com um evento salvo |
| **C. Imagem Docker da Lambda** | rodar a Lambda na imagem oficial da AWS, sem SAM |

### A. API completa com o SAM

O `template.yaml` e o `samconfig.toml` ficam em `applications/serverless` (o template descreve a stack inteira: API, tabelas, bucket da SPA e CloudFront), então os comandos do SAM rodam lá:

```sh
cd ..                            # applications/serverless
sam build
sam local start-api              # http://localhost:3000 (fica rodando)
```

Em outro terminal, teste com `curl`. O `-c` e o `-b` guardam e reenviam o cookie de sessão. Use `localhost`, porque o cookie é `Secure`.

```sh
curl http://localhost:3000/api/me                         # 401: sem sessão

curl -c cookies.txt -X POST http://localhost:3000/api/auth/token \
     -H 'X-Requested-With: XMLHttpRequest' -H 'Content-Type: application/json' \
     -d "{\"token\":\"$(gh auth token)\"}"                  # {"login":"…","isAdmin":…}

curl -b cookies.txt http://localhost:3000/api/groups      # grupos do seed
curl -b cookies.txt "http://localhost:3000/api/groups/3/contributions?period=all"

curl -b cookies.txt -X POST -H 'X-Requested-With: XMLHttpRequest' \
     http://localhost:3000/api/auth/logout                # sai
```

Toda alteração (`POST`, `PUT`, `DELETE`) precisa do header `X-Requested-With: XMLHttpRequest`. As rotas estão no contrato em `applications/serverless/spa/README.md`.

Depois de mudar o Java: `sam build` e reinicie o `sam local start-api`.

### B. Uma invocação com um evento

```sh
cd ..                                       # applications/serverless
sam build
sam local invoke -e lambda/events/me.json   # {"statusCode": 401, ...}
```

`events/me.json` é um evento HTTP API 2.0 de `GET /api/me`. Copie e mude o `rawPath`, o `requestContext.http.method`, os `headers`, os `cookies` e o `body` para testar outras rotas.

### C. Imagem Docker da Lambda

```sh
docker build --build-context core=../../library-core -t grade-checker-api .   # compila a library de applications/library-core na imagem
docker run --rm -p 9000:8080 --network grade-checker-local \
  -e DYNAMODB_ENDPOINT=http://dynamodb-local:8000 \
  -e DATA_TABLE=grade-checker-data -e EPHEMERAL_TABLE=grade-checker-ephemeral \
  -e AWS_REGION=us-east-1 -e AWS_ACCESS_KEY_ID=local -e AWS_SECRET_ACCESS_KEY=local \
  grade-checker-api

# em outro terminal: invoca a Lambda pelo Runtime Interface Emulator da imagem
curl -d @events/me.json http://localhost:9000/2015-03-31/functions/function/invocations
```

É a mesma imagem que o Docker Compose da pasta `serverless` usa.

### Parar

- Ctrl+C no `sam local start-api` ou no `docker run`.
- `sh scripts/local-dynamodb.sh stop` para o DynamoDB Local. **Os dados são perdidos:** na próxima vez, rode o script e o seed de novo.

## Rodar com a SPA

- **Docker Compose:** `docker compose up --build`, na pasta `serverless`. É o jeito mais simples: tudo num comando, em http://localhost:5173 (veja `serverless/README.md`).
- **SAM:** faça o jeito A acima e, em outro terminal, `cd ../spa && npm run dev`. Abra http://localhost:5173 e entre com um personal access token.

O login com o GitHub precisa de um GitHub App com a callback `http://localhost:5173/api/auth/callback` (de preferência um app só de desenvolvimento) e de `GITHUB_CLIENT_ID` e `GITHUB_CLIENT_SECRET` no `env.local.json`.

## Comandos

| Comando | O que faz |
|---|---|
| `mvn test` | testes unitários, sem Docker e sem AWS |
| `mvn test -Pintegration` | inclui os testes `*IT`, contra o DynamoDB Local (tabelas próprias de cada execução) e o GitHub simulado no WireMock |
| `sh scripts/local-dynamodb.sh` / `... stop` | sobe ou para o DynamoDB Local |
| `sh scripts/seed.sh` | seed dos grupos (`src/main/resources/seed-groups.json`). Para a AWS: `DYNAMODB_ENDPOINT= DATA_TABLE=<tabela> sh scripts/seed.sh` |
| `sam validate --lint` (em `applications/serverless`) | valida o template da stack inteira |
| `sam build` (em `applications/serverless`) | compila e empacota a função em `applications/serverless/.aws-sam/` |
| `sam local start-api` (em `applications/serverless`) | API em `http://localhost:3000`, com `lambda/env.local.json`, a rede do DynamoDB Local e o container aquecido (`samconfig.toml`) |
| `sam local invoke -e lambda/events/me.json` (em `applications/serverless`) | uma invocação da Lambda com um evento salvo |
| `docker build --build-context core=../../library-core -t grade-checker-api .` | imagem da Lambda (a mesma do Docker Compose), com a library compilada de `applications/library-core` |

## Estrutura

```
../template.yaml         a stack inteira (em applications/serverless): HTTP API, ApiFunction (CodeUri: lambda/),
                         tabelas, log de 14 dias, bucket da SPA, CloudFront e tags
../samconfig.toml        opções do sam build, do sam local e do sam deploy
env.local.example.json   variáveis da função para o sam local (copie para env.local.json, que fica fora do Git)
scripts/                 DynamoDB Local e seed
events/                  eventos HTTP API 2.0 de exemplo para o sam local invoke
Dockerfile               imagem da Lambda (imagem oficial java:21) para o Docker Compose
src/main/java/br/com/gradechecker/
  lambda/                      ApplicationHandler (entry point do template.yaml) e AppContext (composition root)
  adapter/inbound/http/        Router, Request, Response, Call, ApiException, Json, Maps
    routes/                    AuthRoutes, GroupRoutes, ReportRoutes, GradebookRoutes (só HTTP → casos de uso)
  adapter/outbound/dynamodb/   DynamoClient; DynamoGroupRepository, DynamoMemberRepository,
                               DynamoIdentityAliasRepository (portas da library); DynamoSessionStore; DynamoResultCache
  adapter/outbound/cache/      TwoLevelResultCache: o ResultCache da library (Caffeine + DynamoResultCache)
  session/                     Session (login e token do GitHub da sessão)
  config/                      AdminConfig, GitHubOAuthConfig (monta o GitHubOAuthSettings da library, com o SSM)
  tools/                       Seed (scripts/seed.sh) e LocalSetup (serviço setup do Compose)
src/main/resources/      milestones.properties, grading.properties, seed-groups.json, log4j2.xml
```

## Como funciona

- **Uma função, um roteador:** a HTTP API manda `ANY /api/{proxy+}` para a `ApiFunction`.
  - O `Router` casa método e caminho e aplica, nesta ordem: o `X-Requested-With` nas alterações (403), a sessão (401) e o administrador (403).
  - Os erros saem como `{ "error", "message" }`, com as mesmas mensagens da versão Jetty.
- **Montagem das dependências:** é manual, no `AppContext` (composition root), criado no init estático do `ApplicationHandler`, para entrar no snapshot do SnapStart. Não há container de CDI.
- **Sessão:**
  - fica no item `SESSION#<id>` da tabela efêmera, com o login, o token do GitHub e dois prazos: `expiresAt` (inatividade, 30 minutos sem uso por padrão, estendido a cada chamada, gravado no máximo uma vez por minuto) e `maxExpiresAt` (8 horas depois do login, por padrão; o token do GitHub App vence em 8 horas);
  - o navegador só recebe o cookie `__Host-gc_session` (HttpOnly, Secure, SameSite=Lax), com `Max-Age` igual ao limite absoluto; a inatividade é conferida no servidor;
  - no login com o GitHub, o `state` fica no DynamoDB e num cookie de 10 minutos, e só pode ser usado uma vez.
- **Dados na tabela de dados:**
  - **grupos:** `GROUPS / GROUP#…`;
  - **URL única do repositório:** `REPO#…`, gravada na mesma transação do grupo;
  - **membros:** `GROUP#… / MEMBER#…`, com o contador `memberCount` na mesma transação;
  - **associações:** `GROUP#… / ALIAS#…`;
  - **contador de IDs:** `COUNTER / GROUP`.
- **Cache em duas camadas:** Caffeine na instância e `CACHE#<sha256>` na tabela efêmera (JSON com gzip, TTL de 600 s). Uma falha do cache só gera aviso no log.
- **Notas:** as de All, no dashboard e na turma, leem o histórico do GitHub **uma vez** por grupo e o fatiam por marco, ficando longe do limite de 30 s do API Gateway. Se o histórico vier truncado pelo teto de commits, o cálculo volta a uma leitura por marco, para dar o mesmo resultado.

### O que vem da library `grade-checker-core`

O domínio, as regras e o cliente do GitHub ficam na library (`applications/library-core`), em `br.com.gradechecker.core.*`: notas, atribuição de autores, períodos, grupos e membros, relatório, histórico e cliente do GitHub. Antes, eles estavam copiados aqui e na versão Jetty. A arquitetura é hexagonal, na medida certa:

- **Casos de uso** (`core.application`): `GroupManagement`, `Membership`, `IdentityLinking`, `ContributionsQuery`, `CommitHistoryQuery` e `ClassGradesQuery`.
  - Cada rota lê a requisição, chama um caso de uso e escreve o JSON.
  - As regras e as mensagens de erro (`DomainException`, que o `Router` traduz para 400, 404 ou 409) ficam todas na library.
- **Portas** (`core.port`): `GroupRepository`, `MemberRepository` e `IdentityAliasRepository`, implementadas aqui pelos `Dynamo*Repository`, e `ResultCache`, implementada pelo `TwoLevelResultCache`. A porta do GitHub (`GitHubGateway`) é implementada pelo `GitHubClient` da própria library.
- **Repository × Store:**
  - *Repository* é a persistência de agregados do domínio (grupo, membro, associação), atrás de uma porta;
  - *Store* (`DynamoSessionStore`) guarda um dado técnico e temporário (sessão e state do OAuth), sem porta.

A Lambda entrega à library:
- os adaptadores do DynamoDB para as portas de persistência;
- o cache (`TwoLevelResultCache`, com a segunda camada no DynamoDB);
- as configurações do GitHub App (`GitHubOAuthConfig.settings()`, com o secret do SSM);
- os arquivos `grading.properties` e `milestones.properties`, que continuam em `src/main/resources`.

## Variáveis de ambiente

| Variável | Uso |
|---|---|
| `DATA_TABLE`, `EPHEMERAL_TABLE` | tabelas do DynamoDB (dados; sessão, `state` e cache) |
| `DYNAMODB_ENDPOINT` | só local: `http://dynamodb-local:8000` |
| `GITHUB_CLIENT_ID` | GitHub App (login com GitHub) |
| `GITHUB_CLIENT_SECRET` | só local: client secret do GitHub App |
| `GITHUB_CLIENT_SECRET_PARAMETER` | na AWS: nome do parâmetro SecureString do SSM com o client secret |
| `GITHUB_BASE_URL`, `GITHUB_API_URL` | OAuth e API do GitHub |
| `GRADE_CHECKER_ADMINS` | logins de administradores, separados por vírgula |
| `GCACHE_ENABLED`, `GCACHE_TTL_SECONDS`, `GCACHE_MAX_SIZE` | cache (padrão do TTL: 600 s) |
| `GH_DASHBOARD_MAX_COMMITS` | teto de commits por relatório |
| `SESSION_IDLE_MINUTES`, `SESSION_MAX_HOURS` | a sessão cai depois de N minutos sem uso (padrão 30, de 5 a 480) e, mesmo com uso, N horas depois do login (padrão 8, no máximo 8) |

A publicação na AWS (CloudFront, bucket da SPA, GitHub App de produção, parâmetro do SSM) está em "3. Publicar na AWS", no README da serverless.
