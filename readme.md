# Grade Checker

![Build Status](https://github.com/professor-enoqueleal/grade-checker/actions/workflows/maven.yml/badge.svg)

[Log4J Pattern Layout](https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/PatternLayout.html)

## Setting environment variables for Linux

```shell
export GITHUB_CLIENT_ID=your-client-id
export GITHUB_CLIENT_SECRET=your-client-secret
export GITHUB_BASE_URL=https://github.com
```

## Setting environment variables for Windows

```shell
set GITHUB_CLIENT_ID=your-client-id
set GITHUB_CLIENT_SECRET=your-client-secret
set GITHUB_BASE_URL=https://github.com
```

## Set execution permissions

To execute commands *maven wrapper*, permissions are necessary. To set permissions for execution, using this command:

```shell
$ chmod +x mvnw
```

---

## Configuração da JVM

Para garantir que todos os módulos não-nomeados possam acessar o pacote `java.util`, adicione o 
seguinte comando à configuração da JVM:

```
--add-opens java.base/java.util=ALL-UNNAMED
```

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

## Dashboard de contribuições (GitHub GraphQL)

O botão **Load Dashboard** da tela de detalhe do grupo chama `GET /contributors-stats?owner=&repo=`. Esse endpoint consulta a **API GraphQL do GitHub** (`POST https://api.github.com/graphql`) e devolve, por autor, commits, adições, remoções e porcentagem das linhas alteradas no período. Esses números preenchem a tabela e o gráfico (spec: `openspec/changes/corrigir-dashboard-contribuicoes`).

- **Período:** `date-since` / `date-until` (ISO-8601). Padrão: de `2024-01-01T00:01:00Z` até agora.
- **Escopo:** commits da branch padrão; merges não contam.
- **Custo:** ~1 chamada GraphQL para cada 100 commits do período (~1 ponto do limite de 5.000/h por token). Os resultados vão para o cache.
- **Limite:** `GH_DASHBOARD_MAX_COMMITS` (padrão `1000`). Acima disso, a resposta vem com `truncated: true` e a tela avisa.
- **Token:** o GraphQL sempre exige token. Para repositórios privados, o PAT clássico precisa do escopo `repo`, e o fine-grained precisa de *Contents: read*.

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

## Admin Cache API — exemplos curl

A API administrativa fica em `/admin/cache` (POST, ação no parâmetro `action`) e `/admin/cache/stats` (GET, somente leitura). Ela tem autenticação própria: o header `X-ADMIN-TOKEN` precisa ter o mesmo valor da variável de ambiente `GCACHE_ADMIN_SECRET`. **Não é preciso sessão de login.** Essas duas rotas ficam fora do filtro de autenticação (ver `openspec/changes/proteger-rotas`).

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

---

mvn clean package
java-javaagent:/Users/robot/.m2/repository/com/fnix/fnix-agent/1.0-SNAPSHOT/fnix-agent-1.0-SNAPSHOT.jar -jar target/do/jetty-runner.jar app.war