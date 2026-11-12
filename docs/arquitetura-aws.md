# Arquitetura AWS (versão serverless)

A versão serverless (`applications/serverless`) roda numa única stack do CloudFormation, `grade-checker-serverless`, em `us-east-1`, descrita em [`template.yaml`](../applications/serverless/template.yaml) (AWS SAM). A aplicação responde em `https://gc.enoque.dev`; o domínio `*.cloudfront.net` da distribuição também funciona.

Um único domínio serve a SPA e a API: o CloudFront manda `/api/*` para o API Gateway e todo o resto para o bucket S3. Por isso não há CORS, e o cookie de sessão é do mesmo site.

## Visão geral

```mermaid
flowchart LR
  user(["Navegador<br/>aluno ou professor"])
  dns["Cloudflare DNS<br/>CNAME gc → *.cloudfront.net<br/>(DNS only)"]
  github["GitHub<br/>OAuth do GitHub App e API REST"]

  user -. "resolve gc.enoque.dev" .-> dns
  user -- "HTTPS" --> cf

  subgraph aws["AWS: stack grade-checker-serverless (us-east-1)"]
    acm["ACM<br/>certificado gc.enoque.dev"]
    cf["CloudFront<br/>HTTP/2 e 3, TLS 1.2+"]
    cff["CloudFront Function<br/>rotas da SPA → /index.html"]
    s3[("S3<br/>SPA Vue (privado, OAC)")]
    apigw["API Gateway HTTP API<br/>/api/{proxy+}, 20 req/s"]
    lambda["Lambda Java 21 arm64<br/>alias live + SnapStart"]
    data[("DynamoDB DataTable<br/>grupos, membros, repositórios")]
    eph[("DynamoDB EphemeralTable<br/>sessões, state do OAuth, cache<br/>TTL em expiresAt")]
    ssm[["SSM Parameter Store<br/>client secret (SecureString)"]]
    logs["CloudWatch Logs<br/>14 dias"]
    budget["AWS Budgets<br/>alerta mensal (opcional)"]

    acm -. "certificado" .-> cf
    cf -- "/* (cache longo nos assets)" --> cff --> s3
    cf -- "/api/* (sem cache)" --> apigw --> lambda
    lambda --> data
    lambda --> eph
    lambda -. "lê uma vez" .-> ssm
    lambda -.-> logs
  end

  lambda -- "token do usuário" --> github
```

## Recursos

| Recurso | Tipo | Papel |
|---|---|---|
| `Distribution` | CloudFront | Porta de entrada. `/*` vai para o S3, com o cache gerenciado *CachingOptimized*. `/api/*` vai para o API Gateway sem cache (*CachingDisabled*), repassando cookies, query e headers, menos o `Host`. Redireciona HTTP para HTTPS, usa `PriceClass_100` e aceita IPv6. |
| `SpaRewriteFunction` | CloudFront Function | No *viewer request*, troca caminhos sem extensão (`/home`, `/detail`…) e os antigos `.jsp` por `/index.html`, para o roteador da SPA resolver. |
| `SpaBucket` + `SpaOriginAccessControl` + `SpaBucketPolicy` | S3 + OAC | Arquivos da SPA. O bucket é privado e criptografado: só esta distribuição lê (assinatura SigV4 pelo OAC). |
| `HttpApi` | API Gateway (HTTP API) | Rota `ANY /api/{proxy+}` para a Lambda, com limite de 20 requisições por segundo (rajada de 40). |
| `ApiFunction` | Lambda | API inteira em Java 21 (arm64, 1024 MB, 29 s). O API Gateway chama o alias `live`; cada versão publicada ganha um snapshot do SnapStart, que encurta a partida a frio. |
| `ApiFunctionLogGroup` | CloudWatch Logs | Logs da função, guardados por 14 dias. |
| `DataTable` | DynamoDB (sob demanda) | Dados permanentes, numa tabela única (`PK`/`SK`): grupos (`GROUP#`), membros (`MEMBER#`), repositórios (`REPO#`) e apelidos de autor (`ALIAS#`). Tem recuperação para um ponto no tempo (PITR). |
| `EphemeralTable` | DynamoDB (sob demanda) | Dados de vida curta, apagados pelo TTL do DynamoDB em `expiresAt`: sessões (`SESSION#`), `state` do login OAuth (`STATE#`) e o segundo nível do cache dos relatórios (`CACHE#`). |
| Parâmetro do SSM | SSM Parameter Store | O client secret do GitHub App (`/grade-checker/github-client-secret`), criado à mão uma vez e fora da stack. A função só tem permissão de ler esse parâmetro. |
| Certificado | ACM | Certificado de `gc.enoque.dev`, pedido uma vez, fora da stack, com validação por DNS no Cloudflare. |
| `Budget` | AWS Budgets | Orçamento mensal com alerta por e-mail. Só é criado quando `BUDGET_EMAIL` é informado. |

Todos os recursos recebem as tags `Application=grade-checker`, `Component=serverless`, `Environment`, `ManagedBy=cloudformation` e `Repository`, usadas para separar o custo no Cost Explorer.

## Fluxo de uma requisição

```mermaid
sequenceDiagram
  autonumber
  participant B as Navegador
  participant CF as CloudFront
  participant S3 as S3 (SPA)
  participant API as API Gateway
  participant L as Lambda (alias live)
  participant E as EphemeralTable
  participant D as DataTable
  participant GH as API do GitHub

  B->>CF: GET /detail?id=6
  CF->>S3: /index.html (rota da SPA, via CloudFront Function)
  S3-->>B: index.html e assets/*
  B->>CF: GET /api/groups/6/contributions (cookie de sessão)
  CF->>API: /api/* sem cache
  API->>L: evento HTTP
  L->>E: lê SESSION#35; e estende expiresAt (30 min, até 8 h do login)
  L->>D: lê o grupo e os membros
  L->>E: procura o relatório no cache (CACHE#35;)
  alt não está no cache
    L->>GH: commits do repositório, com o token do usuário
    L->>E: grava o relatório no cache
  end
  L-->>B: JSON
```

O cache dos relatórios tem dois níveis: primeiro a memória da instância da Lambda e depois a `EphemeralTable`, que é compartilhada entre as instâncias.

## Login com o GitHub

```mermaid
sequenceDiagram
  autonumber
  participant B as Navegador
  participant L as Lambda
  participant E as EphemeralTable
  participant SSM as SSM
  participant GH as GitHub

  B->>L: GET /api/auth/github
  L->>E: grava STATE#35; (uso único, vida curta)
  L-->>B: cookie com o state e redireciona para a autorização do GitHub App
  B->>GH: autoriza o app
  GH-->>B: redireciona para /api/auth/callback?code&state
  B->>L: callback
  L->>E: confere o state do cookie e consome STATE#35; (protege contra login CSRF)
  L->>SSM: client secret (lido uma vez por instância)
  L->>GH: troca o code pelo token do usuário e lê GET /user
  L->>E: grava SESSION#35; (login, token, expiresAt, maxExpiresAt)
  L-->>B: cookie de sessão (HttpOnly, Secure, SameSite=Lax)
```

O token do GitHub fica só no DynamoDB, nunca no navegador. O navegador guarda apenas o identificador da sessão, num cookie que o JavaScript não lê.

## Release e deploy

O deploy não usa chaves da AWS guardadas no GitHub: o workflow assume uma role por OIDC, e essa role só aceita a branch `main` deste repositório. A role é criada uma vez, à parte, pela stack [`github-oidc.yaml`](../applications/serverless/github-oidc.yaml).

```mermaid
flowchart TB
  subgraph gh["GitHub Actions"]
    direction LR
    dev(["push na main"]) --> ci["Serverless CI<br/>testes"]
    rel["Release serverless version<br/>(manual, versão X.Y.Z)"] --> build["testes, build da SPA<br/>e sam build"]
    build --> zip[["GitHub Release serverless-vX.Y.Z<br/>serverless-build.zip"]]
    dep["Deploy serverless version<br/>(manual, versão X.Y.Z)"] --> oidc["role da AWS via OIDC<br/>(só a branch main)"]
  end
  zip --> script
  oidc --> script
  subgraph steps["scripts/deploy.sh da tag"]
    direction LR
    script(["início"]) --> s1["Passo 1<br/>confere o parâmetro do SSM<br/>(sem ler o valor)"]
    s1 --> s2["Passo 2<br/>sam deploy: versão nova da<br/>Lambda e alias live"]
    s2 --> s3["Passo 3<br/>SPA para o S3: assets imutáveis,<br/>index.html sem cache"]
    s3 --> s4["Passo 4<br/>invalidação /* do CloudFront"]
    s4 --> s5["Passo 5<br/>seed dos grupos (idempotente)"]
  end
```

O deploy publica o mesmo build que a Release testou. Ele só publica uma versão nova da Lambda, e move o alias `live`, quando o código ou a configuração mudam. Detalhes e variáveis estão no [README da serverless](../applications/serverless/README.md).

## Segurança

- **Sem servidor exposto:** o bucket é privado e só o CloudFront lê os arquivos da SPA. O endereço `execute-api` do API Gateway também é público, mas exige a mesma sessão que o domínio da aplicação, então não abre outro caminho para os dados.
- **Segredo fora do código e da stack:** o client secret fica numa SecureString do SSM e a Lambda só pode ler esse parâmetro. O `deploy.sh` confere que ele existe sem ler o valor.
- **Permissões mínimas:** a função só lê e grava as duas tabelas da stack e lê o parâmetro do SSM. A role do deploy só alcança recursos com o prefixo da stack.
- **Sessão:** vence depois de 30 minutos sem uso e, mesmo com uso, 8 horas depois do login (a vida do token do GitHub App). Os prazos são configuráveis em `SESSION_IDLE_MINUTES` e `SESSION_MAX_HOURS`.
- **Abuso e custo:** limite de 20 requisições por segundo no API Gateway, concorrência reservada opcional (`MAX_CONCURRENCY`) e orçamento mensal com alerta (`BUDGET_EMAIL`).
