# Plano de migração para serverless

> Estudo de **2026-11-08**, feito sobre o código do commit `a1812d1` mais as alterações locais da navbar. Nenhum código foi alterado para este estudo. A visão geral da arquitetura atual está em `openspec/architecture.md`.

## Objetivo

Migrar o Grade Checker para uma arquitetura serverless, sem custo operacional parado:

- **backend:** funções AWS Lambda atrás do **API Gateway**;
- **frontend:** SPA (Vue ou React) hospedada no **S3**;
- **banco:** **DynamoDB**.

**Requisito:** a solução serverless deve poder ser **executada e testada em localhost com o AWS SAM** (`sam local start-api`). Veja a seção 7.

**Premissa:** a versão atual **não está em produção**. Não há dados reais a preservar, então o plano **não inclui migração de dados** (exportar o H2, importar no DynamoDB, manter IDs e links antigos). O banco novo começa vazio, só com os grupos dos seeds.

**Conclusão:** a migração é viável e o custo mensal cai para perto de zero. Quatro pontos exigem decisão de projeto: **sessão**, **cache**, **IDs dos grupos** e o **limite de 30 s do API Gateway**. Nenhum deles impede a migração.

## 1. Como o sistema funciona hoje (o que impacta a migração)

| Peça atual | Depende de servidor sempre ligado? | No serverless |
|---|---|---|
| `HttpSession`: `accessToken`, `userLogin`, `isAdmin`, `state` do OAuth | **Sim**, fica na memória do Jetty | Tabela de sessões no DynamoDB com TTL e cookie HttpOnly |
| H2 em arquivo com DBCP2 | **Sim**, arquivo em disco | DynamoDB |
| Cache Caffeine (`CacheManager`), com chave por hash do token | **Sim**, memória do processo | DynamoDB com TTL; o Caffeine pode continuar como cache local de cada instância |
| `db.sql` executado a cada startup, com seeds | Sim | Script de seed executado no deploy (idempotente) |
| JSPs, tag `navbar.tag`, JSTL | Sim, renderizadas no servidor | SPA no S3 |
| `/grades.csv`, que calcula todos os grupos em sequência | Requisição longa | Gerado no navegador a partir de `/grades/group?id=N`, que a tela já chama |
| Console H2 (`/console`) e `/admin/cache` | Sim | **Somem**, e com eles a limitação conhecida do console H2 |

**Podem ser reaproveitados quase sem mudança**, se o backend continuar em Java:

- **cálculo de notas e atribuição de autores:** `GradeCalculator`, `GroupGradesService`, `AuthorAggregator`, `PeriodResolver`, `Milestones`, `GradingConfig`;
- **integração com o GitHub:** a lógica GraphQL e REST do `GitHubProxy`, `GroupRepositoryUrl` e os modelos.

Essa é a parte mais delicada do sistema (atribuição de autores e notas), e os testes unitários dela vêm junto.

## 2. Arquitetura proposta

```
Navegador ──HTTPS──> CloudFront ──/*──────> S3 privado (SPA: index.html, js, css)
                         │
                         └──/api/*──> API Gateway (HTTP API) ──> Lambda (Java 21 + SnapStart)
                                                                    ├──> DynamoDB: grade-checker (dados)
                                                                    ├──> DynamoDB: grade-checker-ephemeral (sessão, state, cache; TTL)
                                                                    ├──> SSM Parameter Store (client secret, admins)
                                                                    └──> GitHub REST/GraphQL (internet; Lambda fora de VPC)
```

### 2.1 Por que colocar o CloudFront na frente do S3

O pedido original era S3 com static website hosting. A recomendação é o CloudFront, por três motivos:

1. **HTTPS:** o endpoint de website do S3 só serve HTTP. Sem HTTPS, o cookie de sessão (que dá acesso ao token do GitHub) trafega em claro.
2. **Mesma origem:** com `/` e `/api/*` no mesmo domínio, não há CORS, e o cookie funciona com `SameSite=Lax`. O cabeçalho `X-Requested-With` continua servindo de proteção contra CSRF.
3. **Custo zero:** o free tier permanente do CloudFront (1 TB e 10 milhões de requisições por mês) cobre com folga. O bucket fica privado, acessado por OAC (Origin Access Control).

### 2.2 Rotas da SPA

Links como `/detail?id=3` precisam devolver o `index.html`. Isso se faz com uma **CloudFront Function** no comportamento do S3, que reescreve para `/index.html` todo caminho que não seja um arquivo.

Não use "custom error response": essa configuração vale para a distribuição inteira e transformaria os 404 da API em `index.html`.

### 2.3 API Gateway HTTP API ou REST API

A recomendação é a **HTTP API**:

- custa US$ 1 por milhão de requisições, contra US$ 3,50 da REST API;
- é mais simples de configurar.

O que a REST API oferece a mais (usage plans, cache pago) não se aplica aqui.

### 2.4 Lambda: uma função ou várias

A recomendação é **uma única função Java** (ou duas: autenticação e API), com um roteador interno simples, atrás de `ANY /api/{proxy+}`:

- com 14 servlets, uma função por rota multiplicaria os cold starts e o empacotamento;
- uma função só mantém a instância quente para todas as telas.

**Requisito obrigatório:** a Lambda **fica fora de VPC**. Dentro de uma VPC, ela precisaria de um NAT Gateway (cerca de US$ 32 por mês) para alcançar o GitHub.

### 2.5 Java ou Node/TypeScript

A recomendação é **Java 21 com SnapStart**:

- **custo:** o SnapStart é gratuito para Java e reduz o cold start de alguns segundos para algo abaixo de 1 s;
- **reaproveitamento:** a lógica de notas e atribuição é reaproveitada, com os ~270 testes unitários que validam essa lógica.

Reescrever em TypeScript daria uma linguagem só no projeto inteiro, mas obrigaria a reescrever e revalidar a parte mais sensível do sistema.

**Cuidados com o SnapStart:**

- gerar os IDs de sessão com `SecureRandom` (que é compatível com o SnapStart);
- não guardar conexões abertas no snapshot.

### 2.6 Injeção de dependências: sem container de CDI

**Decisão:** não usar container de CDI. As dependências são montadas à mão, num único ponto de montagem.

**Por quê:**

- Na Lambda, a eficiência vem de **criar os objetos caros uma vez só**, na inicialização da instância, e reaproveitá-los em todas as invocações. Os objetos caros aqui são o cliente do DynamoDB, o cliente HTTP do GitHub, o `Gson` e a configuração de marcos e notas. Com o SnapStart, eles já entram prontos no snapshot. Isso não exige container.
- Um CDI tradicional, como o Weld, organiza o código mas não acelera nada. Ele varre o classpath, monta o grafo por reflexão e cria proxies, e esse custo cai no cold start.
- O código atual já usa injeção por construtor (por exemplo, `GroupsApiServlet(GroupRepository, GitHubProxy)`), e os testes passam mocks pelo construtor. O `beans.xml` e o Weld comentado no `web.xml` são sobras de uma tentativa anterior.

**Como fica:** uma classe `AppContext` cria todas as dependências uma vez, no init estático do handler:

```java
// criado uma vez por instância da Lambda (init estático, entra no snapshot do SnapStart)
final class AppContext {
    final DynamoDbClient dynamo = DynamoDbClient.create();
    final GitHubProxy gitHub = new GitHubProxy(...);
    final GroupStore groups = new GroupStore(dynamo, TABLE);
    final MemberStore members = new MemberStore(dynamo, TABLE);
    final GroupGradesService grades = new GroupGradesService(gitHub, ...);
    // ...
}

public class ApiHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private static final AppContext CTX = new AppContext();
    private static final Router ROUTER = new Router(CTX);
    ...
}
```

As classes recebem as dependências pelo construtor. Os testes continuam passando mocks, e o grafo inteiro cabe numa classe que se lê de uma vez.

**Se o grafo crescer muito:**

| Opção | Avaliação |
|---|---|
| **Dagger 2** | Injeção resolvida na compilação, sem reflexão e sem custo de startup. É a primeira escolha se a montagem manual ficar trabalhosa |
| **Quarkus** | CDI resolvido na compilação, com extensão para Lambda e HTTP API e suporte a SnapStart. Muda bastante a estrutura do projeto, então seria uma decisão à parte |
| Spring Boot / Spring Cloud Function | Não recomendado: o cold start é o mais pesado, e o tamanho do projeto não justifica |
| Weld (CDI tradicional) | Não recomendado: paga reflexão na inicialização sem trazer ganho |

### 2.7 Por que não "subir o Jetty dentro da Lambda" (Lambda Web Adapter)

Não funciona sem as mesmas mudanças, por dois motivos:

- a sessão em memória se perde entre instâncias;
- o H2 em arquivo não sobrevive ao disco efêmero da Lambda.

## 3. Mapa das rotas atuais para a nova API

| Hoje | Nova API | Acesso |
|---|---|---|
| `/login/github`, `/callback` | `GET /api/auth/github`, `GET /api/auth/callback` | público |
| `POST /login` (token) | `POST /api/auth/token` | público |
| `/logout` | `POST /api/auth/logout`, que apaga o item da sessão | público |
| `sessionScope` usado na navbar | `GET /api/me`, que devolve `{login, isAdmin}` | autenticado |
| marcos e explicação da nota (antes na JSP) | `GET /api/config` | autenticado |
| `/home` | `GET /api/groups` (ativos, com a contagem de membros) | autenticado |
| `/detail?id=` | `GET /api/groups/{id}` (grupo, membros e contribuidores) | autenticado |
| `/contributors-stats` | `GET /api/groups/{id}/contributions?period=` | autenticado |
| `/contributions-detail` | `GET /api/groups/{id}/commits?author=&period=` | autenticado |
| `/groups` (grupos arquivados) | `GET /api/groups/archived` | admin |
| `/groups/api` (criar, editar, arquivar, restaurar) | `POST /api/groups`, `PUT /api/groups/{id}`, `POST /api/groups/{id}/archive` e `/restore` | admin |
| `/group/members?id=` e `/group-members` | `GET` e `POST /api/groups/{id}/members`, `PUT` e `DELETE /api/groups/{id}/members/{login}` | admin |
| `/identity-aliases` | `POST /api/groups/{id}/aliases`, `DELETE /api/groups/{id}/aliases?identityKey=` | admin |
| `/grades`, `/grades/group` | `GET /api/grades`, `GET /api/grades/{groupId}` | admin |
| `/grades.csv` | gerado no navegador | admin |
| `/admin/cache`, `/console` | removidos | — |

O contrato detalhado (corpos, respostas e erros) está em `serverless/frontend/README.md`, e o mock da SPA (`serverless/frontend/mock/`) o implementa.

**Controle de acesso:** fica num middleware dentro da própria função (sessão, admin e `X-Requested-With`). Um Lambda authorizer separado acrescentaria uma invocação por chamada sem ganho real aqui.

## 4. Modelo no DynamoDB

O volume é pequeno (cerca de 13 grupos e dezenas de membros), então o modelo segue os padrões de acesso, numa tabela única.

### 4.1 Tabela `grade-checker` (on-demand, com PITR ligado)

| PK | SK | Conteúdo / uso |
|---|---|---|
| `GROUPS` | `GROUP#0003` | nome, URL, owner, repo, private, `archivedAt`, `memberCount`. Listar ativos e arquivados = 1 Query |
| `REPO#<url normalizada>` | `REPO` | `groupId`. Garante a URL única entre grupos ativos e arquivados, com uma condition expression |
| `GROUP#0003` | `MEMBER#<login em minúsculas>` | login como está no GitHub, nome, `createdManually`. Membros do grupo = 1 Query |
| `GROUP#0003` | `ALIAS#email:…` / `ALIAS#name:…` | login, `createdBy`, `createdAt` |
| `COUNTER` | `GROUP` | contador atômico (`ADD 1`) que mantém IDs numéricos curtos, usados na tela ("Group 3") e nas URLs (`/detail?id=3`) |

**Como as regras de hoje ficam no DynamoDB:**

| Hoje (SQL) | No DynamoDB |
|---|---|
| `UNIQUE (GITHUB_USERNAME, GROUP_ID)`, sem diferenciar maiúsculas | O login em minúsculas na SK, mais `attribute_not_exists` |
| URL de repositório única (`group.findRepositories`) | O item `REPO#…` dentro de uma `TransactWriteItems` junto com o grupo |
| Editar a URL do grupo | Transação: apaga o `REPO#` antigo, cria o novo e atualiza o grupo |
| Renomear um membro, com as associações seguindo o login (`alias.updateLogin`) | Transação: apaga o membro antigo, cria o novo e atualiza os `ALIAS#` do mesmo login (mesma partição; limite de 100 itens, sem problema) |
| `member.countAllGroups` na home | Atributo `memberCount` no grupo, atualizado na mesma transação |
| Arquivar ou restaurar | `UpdateItem` com condição em `archivedAt` |
| Seeds do `db.sql` (`WHERE NOT EXISTS`) | Script de seed com `attribute_not_exists` no item `REPO#…` |

### 4.2 Tabela `grade-checker-ephemeral` (on-demand, com TTL e sem backup)

- **`SESSION#<id aleatório de 256 bits>`:** token do GitHub, login, `isAdmin` e expiração.
  - O token **nunca vai para o navegador**, como hoje.
  - O cookie se chama `__Host-session` e é HttpOnly, Secure e SameSite=Lax. SameSite precisa ser Lax, e não Strict, porque o callback chega do github.com.
  - O logout apaga o item.
- **`STATE#…`:** o `state` do OAuth, com TTL de 10 minutos e uso único (apagado com condição na leitura).
- **`CACHE#<hash da chave atual>`:** relatório e histórico, com TTL igual ao `GCACHE_TTL_SECONDS`. A chave continua incluindo o hash do token e a impressão digital das associações, para não vazar repositórios privados entre usuários.

**Cuidado com o cache:** um item do DynamoDB tem limite de 400 KB. Foram medidos cerca de 200 a 260 bytes por commit no JSON do GitHub. Um histórico de 1000 commits (`GH_DASHBOARD_MAX_COMMITS`) pode passar de 250 KB, então grave o cache **comprimido com gzip** (cai para cerca de 60 a 80 KB).

## 5. Riscos e limites

| Risco | Detalhe | Mitigação |
|---|---|---|
| **Limite de 30 s do API Gateway** (HTTP API, valor fixo) | A consulta GraphQL do dashboard levou de **0,85 a 1,97 s por página de 100 commits**, medida em 2026-11-08 em 5 repositórios dos seeds. Esses repositórios têm hoje dezenas de commits, então ficam longe do limite. No pior caso, com o teto de 1000 commits, são 10 páginas sequenciais (~10–20 s). Uma nota de grupo pode exigir até 3 relatórios, um por marco | Cache no DynamoDB; buscar o histórico **uma vez** e fatiar por marco em memória (melhoria a fazer antes ou durante a migração); se necessário, baixar `GH_DASHBOARD_MAX_COMMITS` |
| `/grades.csv` calcula todos os grupos numa só requisição | Passaria dos 30 s com a turma inteira | Gerar o CSV no navegador (BOM, `;`, vírgula decimal e proteção contra fórmulas, em JS) |
| Cold start Java | 3 a 6 s sem SnapStart | SnapStart (gratuito para Java), 1024 MB de memória e preparação das classes do SDK e do Gson no init |
| Abuso gerando custo | Endpoint público | Throttling no stage do API Gateway (por exemplo, 20 rps), concorrência máxima da Lambda limitada e alerta no AWS Budgets. O WAF custa a partir de US$ 5 por mês e não compensa aqui |
| Callback do GitHub App | O GitHub usa a **primeira** URL de callback cadastrada | Cadastrar o domínio do CloudFront como primeira URL; o desenvolvimento local exige trocar a ordem ou usar um segundo GitHub App |
| Desenvolvimento local | Hoje basta `mvn jetty:run` | Requisito do projeto: `sam local start-api`, `vite dev` com proxy de `/api` e DynamoDB Local (seção 7) |

## 6. Frontend: Vue ou React

A recomendação é **Vue 3, Vite, Vue Router e Bootstrap 5**, com Chart.js direto:

- **proximidade com o código atual:** os templates do Vue ficam próximos das JSPs atuais (HTML, Bootstrap e IDs), a migração tela a tela é quase mecânica, e o visual não muda;
- **testes E2E:** mantendo os mesmos IDs (`#main-navbar`, `#groups-table`, etc.), a maior parte dos testes Playwright continua valendo.

O React é a opção certa se a intenção for que o projeto também sirva de vitrine do mercado para os alunos.

**Estrutura da SPA:**

- **telas:** 7 views (login, home, detalhe do grupo, commits, notas da turma, grupos, membros);
- **navbar:** vira um componente alimentado por `/api/me`;
- **caixa "Como as notas são calculadas":** passa a receber os parâmetros de `grading.properties` por `GET /api/config`, que também devolve os marcos de `milestones.properties`.

## 7. Desenvolvimento e testes em localhost com o SAM (requisito)

A solução deve rodar inteira na máquina do desenvolvedor, sem depender de uma stack na AWS. O `sam local start-api` emula a **HTTP API**:

- lê os eventos `HttpApi` do template e monta as rotas (inclusive `ANY /api/{proxy+}`);
- entrega à Lambda o evento no **formato 2.0**, o mesmo da AWS;
- sobe em `http://127.0.0.1:3000` e executa a função Java num container. **O Docker precisa estar rodando.**

### 7.1 Fluxo local

```
Navegador ──> vite dev (localhost:5173) ──/api/*──> sam local start-api (localhost:3000) ──> Lambda Java no Docker
                                                                                              └──> DynamoDB Local (Docker)
```

- **Frontend:** o `vite dev` repassa `/api` para a porta 3000. Navegador e API ficam na mesma origem, como acontecerá com o CloudFront, então não há CORS e o cookie de sessão funciona. Os navegadores aceitam cookie `Secure` (e o prefixo `__Host-`) em `http://localhost`.
- **Banco:** **DynamoDB Local** num container, na mesma rede do SAM (`sam local start-api --docker-network <rede>`).
  - O endpoint do DynamoDB vem de variável de ambiente (por exemplo, `DYNAMODB_ENDPOINT`), informada por `--env-vars env.local.json`. Na AWS, a variável fica vazia e o SDK usa o endpoint padrão.
  - Um script cria as duas tabelas e roda o seed dos grupos no DynamoDB Local.
  - Alternativa: uma tabela de dev na própria AWS, com as credenciais da CLI (custo de centavos).
- **Segredos locais:** client secret e administradores vêm do `env.local.json`, fora do Git (como o `.env.local` de hoje). Na AWS, vêm do SSM Parameter Store.
- **Login com o GitHub:** a callback local é `http://localhost:5173/api/auth/callback`. O GitHub usa a **primeira** callback cadastrada, então o mais prático é um **segundo GitHub App só para desenvolvimento**. O login por personal access token também funciona localmente.

### 7.2 O que o SAM local não reproduz

| Item | Na AWS | No `sam local` |
|---|---|---|
| CloudFront (e a Function que devolve o `index.html`) | sim | não. O `vite dev` cumpre esse papel |
| Limite de 30 s do API Gateway | sim | não é garantido. Vale o `Timeout` da função no template, que deve ficar **abaixo de 30 s** para o problema aparecer localmente |
| Throttling e limite de concorrência | sim | não |
| SnapStart | sim | não, então o cold start local é pior que o real |
| Permissões IAM | aplicadas | não. Usa as credenciais da máquina, então um erro de permissão só aparece na AWS |

### 7.3 Comandos de apoio

- **Função isolada:** `sam local invoke` com um evento gerado por `sam local generate-event apigateway http-api-proxy`.
- **Recarga sem reiniciar:** `sam local start-api --warm-containers EAGER` reaproveita o container e acelera o ciclo de edição.
- **Teste na AWS de verdade:** `sam sync --watch` publica cada alteração numa stack de dev em segundos, incluindo IAM, timeout de 30 s e SnapStart.

### 7.4 Testes

- **Unitários (Maven):** continuam rodando sem Docker. O core (notas, atribuição, GitHub) não depende da Lambda, e o handler é testado com eventos HTTP API 2.0 montados no teste.
- **E2E (Playwright):** rodam contra o ambiente local (`vite dev` + `sam local` + DynamoDB Local), com banco isolado, como hoje acontece com a porta 8099. Antes de publicar, também rodam contra a stack de dev na AWS.

## 8. Custo estimado (us-east-1, uso de uma turma)

| Serviço | Uso esperado | Custo/mês |
|---|---|---|
| Lambda | Poucos milhares de invocações | US$ 0 (free tier permanente: 1 milhão de requisições e 400 mil GB-s) |
| API Gateway HTTP API | Menos de 100 mil requisições | ≈ US$ 0,10 (12 meses grátis até 1 milhão) |
| DynamoDB on-demand | Menos de 1 MB e poucos milhares de operações | ≈ US$ 0,01 (25 GB de armazenamento grátis) |
| S3 + CloudFront | Poucos MB | ≈ US$ 0 (free tier permanente do CloudFront) |
| SSM Parameter Store (standard) | 2 a 3 parâmetros | US$ 0 (o Secrets Manager custaria US$ 0,40 por segredo) |
| CloudWatch Logs | Retenção de 14 dias | centavos |
| **Total** | | **≈ US$ 0 a 1 por mês, sem gasto parado** |

**Comparação com a EC2 atual:**

- **ligada:** a t3.medium custa cerca de US$ 30 por mês;
- **parada:** o Elastic IP e o disco EBS continuam cobrando, cerca de US$ 3 a 4 por mês.

Um domínio próprio é opcional: a hosted zone do Route 53 custa US$ 0,50 por mês, e o certificado do ACM é gratuito.

## Andamento

- **Frontend:** pronto em `serverless/frontend` (Vue 3 + Vite). Spec da change `frontend-spa`, arquivada.
- **Backend:** pronto em `serverless/backend` (Lambda Java 21, HTTP API, DynamoDB). Spec da change `backend-lambda`, arquivada.
  - Roda de ponta a ponta em localhost com `sam local start-api`, DynamoDB Local e `npm run dev`.
- **Domínio numa library:** a versão serverless e a versão Jetty usam a library `grade-checker-core` (`core/`), publicada no GitHub Packages (change `core-library` em `core/openspec`).
- **Portas e adapters (2026-11-08):**
  - o backend está organizado em `lambda/` (ponto de entrada `ApplicationHandler` e `AppContext`), `adapter/inbound/http` e `adapter/outbound/{dynamodb,cache}`;
  - as regras ficam nos casos de uso da library (changes `backend-hexagonal` e `core-ports-use-cases`);
  - o trecho de código da seção 2.6 é o esboço original, com os nomes antigos.
- **Ambiente local com um comando:** `docker compose up --build` na pasta `serverless` (change `docker-compose-local`, arquivada).
- **Próximo passo:** a change de publicação na AWS (CloudFront, bucket da SPA, GitHub App de produção, SSM, alarmes e budget), com a adaptação dos testes Playwright.
- **Specs:** o OpenSpec da versão serverless fica em `serverless/openspec`.

## 9. Plano em fases

1. **Preparação, no código atual:** buscar o histórico uma vez por grupo e fatiar por marco, e mover o CSV para o navegador. Os dois reduzem o risco dos 30 s e já valem para a EC2.
2. **Backend:** separar o "core" (notas, atribuição, GitHub) em um módulo Maven sem servlet. Criar o handler Lambda com roteador, a camada DynamoDB no lugar dos repositórios JDBC, e sessão e cache no DynamoDB.
3. **Infraestrutura:** template **SAM** (é CloudFormation, mesma linguagem do template atual) com HTTP API, Lambda com SnapStart, 2 tabelas, bucket, CloudFront com OAC e Function, parâmetros SSM, throttling e budget. Desde a primeira versão, o template deve subir com `sam local start-api`, junto com o script do DynamoDB Local e o `env.local.json` de exemplo (seção 7).
4. **Frontend:** SPA em Vue, tela a tela, com os mesmos IDs, e adaptação dos testes Playwright para a nova URL.
5. **Publicação:** deploy da stack SAM, seed dos grupos, cadastro da URL do CloudFront como callback do GitHub App, validação com os testes E2E e, depois, exclusão da stack da EC2. Não há dados a migrar: os membros e as associações são cadastrados na versão nova.

## 10. Impacto no OpenSpec

Seguindo o OpenSpec, a migração vira uma change grande (por exemplo, `migracao-serverless`), com `design.md` e deltas em quase todas as capabilities:

- **substituídas:** `ec2-deployment`, `database-bootstrap`, `in-memory-cache` e `sql-queries`;
- **alteradas na forma, mesmo comportamento:** `route-protection`, `github-sign-in`, `token-sign-in`, `sign-out`, `platform-admin` e `navigation-bar` (sessão no DynamoDB, `/api/me`), além das telas (`group-management`, `group-members`, `identity-aliases`, `class-gradebook`, `contributions-dashboard`, `commit-history-page`, `card-grid-layout`);
- **sem mudança de regra:** `milestone-grading` e `milestone-period-filter`.
