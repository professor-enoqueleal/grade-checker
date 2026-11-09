# API

As rotas abaixo são usadas pelas telas. As rotas de administrador exigem uma sessão de administrador (veja [Administradores](configuracao.md#administradores)) e o header `X-Requested-With: XMLHttpRequest`; para os outros usuários, respondem 403.

## Dashboard de contribuições

`GET /contributors-stats?groupId=&period=` devolve, para o repositório do grupo cadastrado, por autor, commits, adições, remoções e porcentagem das linhas alteradas no período.

- **`period`:** `all` (padrão, histórico inteiro), `m1`, `m2`, `m3` ou `custom`. `date-since` e `date-until` só valem com `period=custom` e, sem fuso, são interpretados no `milestone.timezone`.
- Acima de `GH_DASHBOARD_MAX_COMMITS`, a resposta vem com `truncated: true`.

```bash
curl -b cookies.txt "$BASE_URL/contributors-stats?groupId=6&period=m2"
```

O histórico de commits de um aluno (`/contributions-detail`) aceita os mesmos valores de `period`.

Erros do GitHub voltam como JSON `{"error": "...", "message": "..."}` e são exibidos na tela:

| Status | `error` | Quando |
|---|---|---|
| 400 | `bad_request` | `groupId` ausente ou inválido |
| 401 | `github_unauthorized` | token inválido ou expirado |
| 403 | `github_forbidden` | token sem permissão no repositório |
| 404 | `github_not_found` | repositório inexistente ou sem acesso |
| 429 | `github_rate_limited` | limite de requisições do GitHub esgotado |
| 502 | `github_error` | outro erro ou falha de rede |

## Grupos (administrador)

- `POST /groups/api` (`name`, `repositoryUrl`): cria um grupo. O repositório é validado no GitHub e gravado com a grafia do GitHub. URL inválida, repositório inexistente e repositório já cadastrado (inclusive num grupo arquivado) são recusados.
- `PUT /groups/api?id=&name=&repositoryUrl=`: edita o nome e a URL, mantendo o id (membros, associações e notas continuam).
- `POST /groups/api/archive?id=` e `POST /groups/api/restore?id=`: arquivam e restauram. Um grupo arquivado sai da home, de `/grades` e do CSV, e `/detail` responde 404.

## Membros (administrador)

- `POST /group-members` (`groupId`, `login`, `name` opcional): adiciona um membro. O login é validado no GitHub.
- `PUT /group-members?groupId=&login=&newLogin=&name=`: edita o membro. As identidades associadas ao login antigo passam para o novo.
- `DELETE /group-members?groupId=&login=`: remove o membro. É recusado se o aluno tiver identidades associadas manualmente; desfaça a associação antes.

## Associação manual de identidades (administrador)

```bash
curl -b cookies.txt -H "X-Requested-With: XMLHttpRequest" -X POST "$BASE_URL/identity-aliases" \
  -d groupId=6 -d identityKey="email:aluno@senacsp.edu.br" -d login=aluno-github
curl -b cookies.txt -H "X-Requested-With: XMLHttpRequest" -X DELETE \
  "$BASE_URL/identity-aliases?groupId=6&identityKey=email:aluno@senacsp.edu.br"
```

## Notas da turma (administrador)

- `/grades`: página com todos os alunos de todos os grupos (M1, M2, M3 e Total, no período All, incluindo as associações manuais).
- `/grades/group`: notas de um grupo, carregadas pela página.
- `/grades.csv`: export em formato pt-BR (`;`, vírgula decimal, UTF-8 com BOM). Colunas: `group;student;m1;m2;m3;total;provisional;error`. Grupos sem alunos não geram linhas, e nomes que começam com `=`, `+`, `-` ou `@` recebem `'` (proteção contra fórmulas).

## API administrativa do cache

A API fica em `/admin/cache` (POST, ação no parâmetro `action`) e `/admin/cache/stats` (GET, somente leitura). Ela tem autenticação própria: o header `X-ADMIN-TOKEN` precisa ter o mesmo valor da variável de ambiente `GCACHE_ADMIN_SECRET`. **Não é preciso sessão de login**: essas duas rotas ficam fora do filtro de autenticação.

Respostas de erro:

- `403 {"error":"forbidden"}`: header ausente ou incorreto, ou `GCACHE_ADMIN_SECRET` não definida no servidor.
- `400`: `action` desconhecida ou `invalidate` sem `key`.

Os exemplos usam `http://localhost:8080`, a base padrão do `mvn jetty:run`. Ajuste `BASE_URL` para o seu deploy:

```bash
export BASE_URL="http://localhost:8080"
export GCACHE_ADMIN_SECRET="defina-um-segredo"   # o mesmo valor configurado no servidor
```

Estatísticas (hits, misses e estado):

```bash
curl -H "X-ADMIN-TOKEN: $GCACHE_ADMIN_SECRET" "$BASE_URL/admin/cache/stats"

# também via POST: stats é a ação padrão quando action é omitida
curl -X POST -H "X-ADMIN-TOKEN: $GCACHE_ADMIN_SECRET" -d "action=stats" "$BASE_URL/admin/cache"
```

Invalidar uma chave específica:

```bash
curl -X POST -H "X-ADMIN-TOKEN: $GCACHE_ADMIN_SECRET" -d "action=invalidate" \
  --data-urlencode "key=github.contributors::octocat:hello-world:ABCDEFHASH" \
  "$BASE_URL/admin/cache"
```

Invalidar todo o cache:

```bash
curl -X POST -H "X-ADMIN-TOKEN: $GCACHE_ADMIN_SECRET" -d "action=invalidateAll" "$BASE_URL/admin/cache"
```

Rebuild (habilitar ou desabilitar e ajustar TTL e tamanho máximo em runtime):

```bash
curl -X POST -H "X-ADMIN-TOKEN: $GCACHE_ADMIN_SECRET" \
  -d "action=rebuild" -d "enabled=true" -d "ttl=120" -d "max=5000" \
  "$BASE_URL/admin/cache"
```

Segurança:

- Defina `GCACHE_ADMIN_SECRET` no ambiente do servidor, sem deixar em branco.
- Exponha o endpoint apenas em redes confiáveis (localhost, VPN) ou proteja com autenticação adicional num proxy.
