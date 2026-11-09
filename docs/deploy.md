# Deploy

Duas formas: [Docker](#docker) e [AWS (CloudFormation)](#aws-cloudformation). Os comandos rodam em `applications/monolith`.

## Docker

O `Dockerfile` monta a aplicação assim:

- Amazon Corretto 17;
- build com `sh ./mvnw -DskipTests` (testes pulados para agilizar);
- Jetty 11.0.26 baixado do Maven Central;
- WAR publicado como `ROOT.war`;
- usuário sem privilégios de root.

A imagem é montada a partir do **código local**, com a library `grade-checker-core` compilada de `applications/library-core` (não faz `git clone`), então também serve para testar o que está em desenvolvimento. O container usa a **porta 8080**, a mesma do IntelliJ: rode um ou outro, não os dois ao mesmo tempo. A URL de callback do GitHub App (`http://localhost:8080/callback`) serve para os dois.

### 1. Arquivo `.env.local` (em `applications/monolith`)

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
# opcional: habilita a API administrativa do cache (16 caracteres ou mais)
# GCACHE_ADMIN_SECRET=
```

As demais variáveis estão em [Configuração](configuracao.md#variáveis-de-ambiente).

### 2. Build da imagem

```bash
docker build --build-context core=../library-core -t grade-checker .
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

## AWS (CloudFormation)

O template `grade-checker-ec2.yaml` (em `applications/monolith`) cria uma EC2 (Amazon Linux 2023) com Elastic IP e roda o Jetty como serviço systemd. A instância **não compila nada**: ela baixa o `grade-checker.war` de uma Release do GitHub (publicada pelo workflow "Release vanilla version"), confere o SHA-256 publicado pelo GitHub e o instala como `ROOT.war`.

| Parâmetro | Variável | Obrigatório |
|---|---|---|
| `GitHubBaseUrl` | `GITHUB_BASE_URL` | sim (padrão `https://github.com`) |
| `GitHubClientId` | `GITHUB_CLIENT_ID` | sim |
| `GitHubClientSecret` | `GITHUB_CLIENT_SECRET` (via Secrets Manager) | sim |
| `GradeCheckerAdmins` | `GRADE_CHECKER_ADMINS` | não (vazio = ninguém é administrador) |
| `CacheAdminSecret` | `GCACHE_ADMIN_SECRET` (via Secrets Manager) | não |
| `GitHubApiUrl` | `GITHUB_API_URL` | não (padrão `https://api.github.com`) |
| `CacheEnabled` | `GCACHE_ENABLED` | não (padrão `true`) |
| `CacheTtlSeconds` | `GCACHE_TTL_SECONDS` | não (padrão `60`) |
| `CacheMaxSize` | `GCACHE_MAX_SIZE` | não (padrão `10000`) |
| `DashboardMaxCommits` | `GH_DASHBOARD_MAX_COMMITS` | não (padrão `1000`) |
| `ReleaseRepository` | — | não (padrão `professor-enoqueleal/grade-checker`) |
| `ReleaseVersion` | — | não (`latest` ou `vX.Y.Z`; padrão `latest`) |

Parâmetros opcionais vazios não são escritos no arquivo `/etc/grade-checker/grade-checker.env`, e a aplicação usa o padrão. O banco H2 fica na instância (`/var/lib/grade-checker/h2db`).

```bash
aws cloudformation create-stack --stack-name grade-checker \
  --template-body file://grade-checker-ec2.yaml --capabilities CAPABILITY_IAM \
  --parameters ParameterKey=GitHubClientId,ParameterValue=Iv1.xxx ParameterKey=GitHubClientSecret,ParameterValue=xxx \
    ParameterKey=VpcId,ParameterValue=vpc-xxx ParameterKey=SubnetId,ParameterValue=subnet-xxx
```

- **Trocar de versão** na instância (o banco é mantido): `sudo grade-checker-deploy v1.0.1` (ou `latest`). A versão em uso fica em `/etc/grade-checker/version`.
- **Mudar uma configuração depois de criar a stack** (por exemplo, os administradores): o arquivo de ambiente é escrito só no primeiro boot. Edite-o na instância (Session Manager) e reinicie o serviço, como mostra o output `ChangeSettings`:
  ```bash
  sudo vi /etc/grade-checker/grade-checker.env
  sudo systemctl restart grade-checker
  ```
- **Login com GitHub na EC2:** a primeira callback URL do GitHub App precisa ser a do output `OAuthCallbackUrl` da stack. Depois, volte a de localhost para o topo.

## Cache em produção: desligar e acompanhar

- **Desligar sem reiniciar:** `POST /admin/cache` com `action=rebuild&enabled=false` (exige `GCACHE_ADMIN_SECRET` configurado no servidor).
- **Desligar de forma persistente:** no Docker, acrescente `GCACHE_ENABLED=false` ao `.env.local` e recrie o container; na EC2, acrescente a linha em `/etc/grade-checker/grade-checker.env` e rode `sudo systemctl restart grade-checker`.
- **Acompanhar:** `GET /admin/cache/stats` (hits e misses).
- Sem `GCACHE_ADMIN_SECRET`, a API administrativa responde 403 a tudo. Para habilitá-la, defina a variável no `.env.local` (Docker) ou informe o parâmetro `CacheAdminSecret` ao criar a stack (AWS).

Os exemplos estão em [API administrativa do cache](api.md#api-administrativa-do-cache).
