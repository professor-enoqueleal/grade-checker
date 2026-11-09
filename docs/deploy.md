# Deploy com Docker

O `Dockerfile` monta a aplicação assim:

- Amazon Corretto 17;
- build com `sh ./mvnw -DskipTests` (testes pulados para agilizar);
- Jetty 11.0.26 baixado do Maven Central;
- WAR publicado como `ROOT.war`;
- usuário sem privilégios de root.

A imagem é montada a partir do **código local** (não faz `git clone`), então também serve para testar o que está em desenvolvimento. O container usa a **porta 8080**, a mesma do IntelliJ: rode um ou outro, não os dois ao mesmo tempo. A URL de callback do GitHub App (`http://localhost:8080/callback`) serve para os dois.

## 1. Arquivo `.env.local` (na raiz do projeto)

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

## 2. Build da imagem

```bash
docker build -t grade-checker .
```

## 3. Run do container usando o `.env.local`

```bash
docker rm -f grade-checker 2>/dev/null   # remove um container anterior, se existir
docker run -d --name grade-checker -p 8080:8080 --env-file .env.local \
  -v grade-checker-data:/var/lib/grade-checker/h2db grade-checker
```

O volume `grade-checker-data` guarda o banco H2: grupos, membros e associações manuais sobrevivem a `docker rm` e a um novo build. Para começar do zero: `docker volume rm grade-checker-data`.

## Comandos úteis

```bash
docker ps --filter name=grade-checker   # STATUS deve ficar "(healthy)"
docker logs -f grade-checker            # logs do Jetty
docker rm -f grade-checker              # parar e remover
```

- Aplicação em `http://localhost:8080/`, com healthcheck em `/login`.
- O banco H2 é preservado entre reinícios (o `db.sql` é idempotente: cria só o que falta e nunca apaga dados).
- **Login com GitHub no container:** a primeira URL de callback do GitHub App precisa ser `http://localhost:8080/callback`.

## Cache em produção: desligar e acompanhar

- **Desligar sem reiniciar:** `POST /admin/cache` com `action=rebuild&enabled=false` (exige `GCACHE_ADMIN_SECRET` configurado no servidor).
- **Desligar de forma persistente:** acrescente `GCACHE_ENABLED=false` ao `.env.local` e recrie o container.
- **Acompanhar:** `GET /admin/cache/stats` (hits e misses).
- Sem `GCACHE_ADMIN_SECRET`, a API administrativa responde 403 a tudo. Para habilitá-la, defina a variável no `.env.local`.

Os exemplos estão em [API administrativa do cache](api.md#api-administrativa-do-cache).
