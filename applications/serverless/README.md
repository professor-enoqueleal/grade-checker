# Grade Checker — versão serverless

| Pasta | Conteúdo |
|---|---|
| `spa/` | SPA (Vue 3 + Vite), servida pelo S3 + CloudFront na AWS |
| `lambda/` | API: uma Lambda Java 21 atrás do API Gateway (HTTP API), com DynamoDB, descrita no `template.yaml` desta pasta (AWS SAM), que também cria o bucket da SPA e o CloudFront na AWS. O domínio vem da library `grade-checker-core` (`applications/library-core`) |
| `local/gateway/` | emulador local da HTTP API, usado pelo Docker Compose |
| `e2e/` | testes de ponta a ponta (Playwright) da versão serverless, num ambiente próprio do Compose |
| `openspec/` | specs da versão serverless (`openspec validate --all --strict`, rodado a partir desta pasta) |
| `migration-plan.md` | plano de migração e andamento |

Há dois jeitos de rodar tudo em localhost:

| | Com Docker Compose | Sem Docker Compose (AWS SAM) |
|---|---|---|
| Comandos | 1 | 4, em 2 terminais |
| Pré-requisitos | Docker | Docker, Java 21, Maven, AWS SAM CLI 1.103 ou mais novo, AWS CLI, Node 22 |
| API Gateway | emulador próprio (`local/gateway`) | emulador do SAM, que lê as rotas do `template.yaml` |
| SPA | build de produção no nginx | Vite com recarga automática |
| Dados do DynamoDB | mantidos num volume | só em memória: somem quando o container para |
| Bom para | testar a aplicação inteira, como vai ficar na AWS | desenvolver o backend e validar o template SAM |

Nos dois casos a aplicação abre em **http://localhost:5173**. Use `localhost`, e não `127.0.0.1`: o cookie de sessão é `Secure`, e o navegador só o aceita sem HTTPS em `localhost`. Os dois jeitos usam as portas 3000, 5173 e 8000, então rode um de cada vez.

---

## 1. Com Docker Compose

**Pré-requisito:** Docker rodando.

### Subir

Na pasta `serverless`:

```sh
cp .env.example .env          # opcional, veja "Configuração" abaixo
docker compose up --build
```

A primeira vez leva alguns minutos, porque compila o backend e a SPA. Quando aparecer `HTTP API gateway on :3000`, abra **http://localhost:5173** e entre com um personal access token do GitHub (`gh auth token` serve).

### O que sobe

| Serviço | O que é | Porta no host |
|---|---|---|
| `spa` | build de produção da SPA no nginx, no papel do CloudFront: `index.html` nas rotas da SPA e `/api` repassado ao gateway | 5173 |
| `gateway` | emulador da HTTP API: requisição → evento 2.0 → Lambda → resposta (cookies, 500, 503 acima de 30 s) | 3000 |
| `api` | a Lambda, na imagem oficial `public.ecr.aws/lambda/java:21`, com o mesmo handler do template | — |
| `setup` | cria as tabelas que faltam e roda o seed dos grupos; termina antes de a `api` subir | — |
| `dynamodb` | DynamoDB Local, com os dados no volume `dynamodb-data` | 8000 |

### Configuração (`.env`)

O arquivo `serverless/.env` fica fora do Git. Todos os valores são opcionais:

| Variável | Para quê |
|---|---|
| `GRADE_CHECKER_ADMINS` | seu login do GitHub (separe vários por vírgula): mostra "Manage groups", "Class grades" e "Manage members" |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | botão "Continue with GitHub": GitHub App de desenvolvimento com a callback `http://localhost:5173/api/auth/callback` |

Sem `.env`, tudo funciona com o login por token, mas ninguém é administrador. Depois de mudar o `.env`, rode `docker compose up -d` de novo.

### Comandos do dia a dia

| Comando | O que faz |
|---|---|
| `docker compose up --build -d` | sobe em segundo plano |
| `docker compose logs -f gateway api` | acompanha as requisições e os logs da Lambda |
| `docker compose up --build -d api` | recompila e reinicia só a Lambda, depois de mudar o Java |
| `docker compose up --build -d spa` | refaz o build da SPA, depois de mudar o frontend |
| `docker compose down` | para tudo e **mantém** os dados |
| `docker compose down -v` | para tudo e **apaga** os dados (o próximo `up` recria as tabelas e o seed) |
| `SPA_PORT=… GATEWAY_PORT=… DYNAMODB_PORT=… docker compose -p <nome> up -d` | outro ambiente ao mesmo tempo, em outras portas (é o que a suíte E2E faz) |

**Para mexer na SPA com recarga automática,** suba tudo menos a SPA e rode o Vite, que usa a API do Compose pela porta 3000:

```sh
docker compose up --build -d dynamodb setup api gateway
cd spa && npm install && npm run dev
```

### Limitações em relação à AWS

- A Lambda atende uma requisição por vez. As notas da turma carregam os grupos em sequência: o resultado é o mesmo, só mais lento.
- Não há throttling, IAM nem SnapStart.

---

## 2. Sem Docker Compose (AWS SAM, direto no localhost)

Aqui cada parte roda por um comando próprio. O AWS SAM emula a HTTP API a partir do `template.yaml` (o `sam local` ignora o S3 e o CloudFront) e roda a Lambda num container que ele mesmo cria. Por isso o Docker continua necessário, mas sem o Compose.

### Pré-requisitos

| Ferramenta | Para quê | Como conferir |
|---|---|---|
| Docker rodando | o `sam local` e o DynamoDB Local | `docker info` |
| Java 21 e Maven 3.8+ | compilar o backend | `java -version`, `mvn -v` |
| AWS SAM CLI **1.103 ou mais novo** | `sam build` e `sam local start-api` (as versões antigas não conhecem o `java21`) | `sam --version` |
| AWS CLI | o script que cria as tabelas no DynamoDB Local | `aws --version` |
| Node 22 | a SPA | `node -v` |

Se o Compose estiver no ar, pare antes com `docker compose down`, porque ele ocupa as mesmas portas.

### Primeira vez

```sh
cd applications/serverless
mvn -f ../library-core/pom.xml install                     # a library grade-checker-core (ou a publicada: docs/desenvolvimento.md)
cp lambda/env.local.example.json lambda/env.local.json     # coloque seu login em GRADE_CHECKER_ADMINS
(cd spa && npm install)
```

Depois de mudar algo em `applications/library-core`, rode o `mvn -f ../library-core/pom.xml install` de novo antes do `sam build`. O Docker Compose não precisa disso: ele compila a library de `applications/library-core` dentro da imagem.

No `lambda/env.local.json` (fora do Git) ficam as variáveis da Lambda. O GitHub App é opcional, como no `.env` do Compose.

### Subir

**Terminal 1 (backend):**

```sh
cd applications/serverless              # onde ficam o template.yaml e o samconfig.toml
sh lambda/scripts/local-dynamodb.sh     # DynamoDB Local na porta 8000 e as duas tabelas
sh lambda/scripts/seed.sh               # grupos do semestre (pode rodar de novo: não duplica)
sam build                               # compila e empacota a Lambda
sam local start-api                     # API em http://127.0.0.1:3000 (fica rodando)
```

**Terminal 2 (SPA):**

```sh
cd applications/serverless/spa
npm run dev                      # http://localhost:5173, com /api repassado para a porta 3000
```

Abra **http://localhost:5173** e entre com um personal access token.

### Depois de mudar o código

| Mudança | O que fazer |
|---|---|
| Java (backend) | `sam build` e reinicie o `sam local start-api` (Ctrl+C e rode de novo) |
| SPA (frontend) | nada: o Vite recarrega sozinho |
| `template.yaml` | `sam validate --lint`, `sam build` e reinicie o `sam local start-api` |

### Parar

- Ctrl+C no `sam local start-api` e no `npm run dev`.
- `sh lambda/scripts/local-dynamodb.sh stop` para o DynamoDB Local. **Os dados são perdidos:** na próxima vez, rode o script e o seed de novo.

---

## 3. Publicar na AWS

O `template.yaml` descreve a stack inteira (`grade-checker-serverless`, em `us-east-1`): HTTP API, Lambda, tabelas DynamoDB, bucket privado da SPA e CloudFront. A SPA e a API ficam no mesmo domínio HTTPS (`https://<id>.cloudfront.net`, com `/api/*` indo para a Lambda). Os comandos rodam em `applications/serverless`, com a AWS CLI e o SAM CLI configurados.

### Uma única vez

1. **GitHub App de produção** (separado do app de desenvolvimento, que fica com o `localhost`): GitHub → *Settings* → *Developer settings* → *GitHub Apps* → *New GitHub App*.
   - *Callback URL:* qualquer URL provisória (por exemplo, `https://example.com/callback`); ela é trocada depois do primeiro deploy.
   - *Permissions:* *Contents: read* e *Metadata: read*. Desmarque *Webhook → Active*.
   - Depois de criar: *Generate a new client secret* e, em *Advanced*, **Make public** (um app privado só autoriza a conta dona).
2. **Client secret no SSM** (SecureString, fora da stack: apagar e recriar a stack não exige digitá-lo de novo). O `read -rs` lê o valor sem mostrá-lo na tela nem guardá-lo no histórico:
   ```sh
   read -rs SECRET && aws ssm put-parameter --region us-east-1 --name /grade-checker/github-client-secret \
     --type SecureString --value "$SECRET" && unset SECRET
   ```
   Para trocar o valor depois, repita o comando com `--overwrite`.
3. **Tags nos relatórios de custo** (opcional, vale para a conta inteira e para o monolith também). As tags passam a contar daí em diante e aparecem no Cost Explorer em até 24 horas:
   ```sh
   aws ce update-cost-allocation-tags-status --cost-allocation-tags-status \
     TagKey=Application,Status=Active TagKey=Component,Status=Active TagKey=Environment,Status=Active
   ```

### Deploy

```sh
GITHUB_CLIENT_ID=Iv1.xxxxxxxxxxxx GRADE_CHECKER_ADMINS=seu-login sh scripts/deploy.sh
```

O script confere que o parâmetro do SSM existe (sem ler o valor), roda `sam build` e `sam deploy`, gera a SPA e a envia ao bucket (o `index.html` sem cache no navegador e os `assets/` com cache longo), invalida o cache do CloudFront e roda o seed dos grupos. No fim, mostra a URL da aplicação e a callback URL. O primeiro deploy leva de 5 a 15 minutos, por causa do CloudFront.

Variáveis opcionais: `ENVIRONMENT` (padrão `test`, também a tag `Environment`), `APP_DOMAIN` e `APP_CERTIFICATE_ARN` (domínio próprio, sempre juntos; veja abaixo), `BUDGET_EMAIL` e `BUDGET_AMOUNT` (orçamento mensal com alerta em 80%, padrão 5 USD), `MAX_CONCURRENCY` (concorrência reservada; contas novas costumam não permitir), `DATA_TABLE_DELETION_POLICY` (`Delete`, o padrão, ou `Retain`, que mantém a tabela de dados quando a stack é apagada), `SESSION_IDLE_MINUTES` e `SESSION_MAX_HOURS` (a sessão cai depois de 30 minutos sem uso e, mesmo com uso, 8 horas depois do login; no GitHub Actions, variáveis do repositório de mesmo nome).

**Depois do primeiro deploy:** troque a callback URL do GitHub App de produção pela URL mostrada no fim (`https://<id>.cloudfront.net/api/auth/callback`, ou a do domínio próprio). Ela precisa ser a **primeira** da lista: o backend não envia `redirect_uri`, então o GitHub usa a primeira. Ela muda só se a stack for apagada e criada de novo ou se o domínio próprio mudar.

### Domínio próprio (opcional)

A aplicação pode responder num subdomínio de um domínio registrado fora da AWS, por exemplo `gc.enoque.dev`, com o DNS no Cloudflare. O domínio `*.cloudfront.net` continua funcionando. Não há custo: o certificado público do ACM é gratuito, e o CloudFront usa SNI.

1. **Certificado, uma única vez e fora da stack.** Precisa ser em `us-east-1`, a única região que o CloudFront lê:
   ```sh
   aws acm request-certificate --region us-east-1 --domain-name gc.enoque.dev --validation-method DNS \
     --tags Key=Application,Value=grade-checker Key=Component,Value=serverless Key=ManagedBy,Value=cli \
     --query CertificateArn --output text
   aws acm describe-certificate --region us-east-1 --certificate-arn <ARN> \
     --query 'Certificate.DomainValidationOptions[0].ResourceRecord'
   ```
   Crie no Cloudflare o CNAME de validação mostrado (`Name` → `Value`, nuvem cinza) e espere o status `ISSUED` (`aws acm wait certificate-validated --region us-east-1 --certificate-arn <ARN>`). **Mantenha esse registro:** é com ele que o ACM renova o certificado.
2. **Deploy** com `APP_DOMAIN=gc.enoque.dev APP_CERTIFICATE_ARN=<ARN>` (ou as variáveis do repositório de mesmo nome, no GitHub Actions). No fim, o script mostra o alvo do CNAME.
3. **DNS no Cloudflare:** CNAME `gc` → o `DistributionDomainName` da stack (`<id>.cloudfront.net`), em **DNS only** (nuvem cinza). Com o proxy do Cloudflare ligado, seriam duas CDNs em série.
4. **GitHub App de produção:** `https://gc.enoque.dev/api/auth/callback` como **primeira** callback URL. O desenvolvimento local usa outro GitHub App, com a callback `http://localhost:5173/api/auth/callback`, então o app de produção não precisa ser alterado para testar em localhost.

Para voltar ao domínio padrão, faça o deploy sem as duas variáveis. O certificado pode ficar na conta.

Todos os recursos que aceitam tag recebem `Application=grade-checker`, `Component=serverless`, `Environment`, `ManagedBy=cloudformation` e `Repository=professor-enoqueleal/grade-checker`. Para listá-los:

```sh
aws resourcegroupstaggingapi get-resources --tag-filters Key=Application,Values=grade-checker Key=Component,Values=serverless \
  --query 'ResourceTagMappingList[].ResourceARN'
```

### Pelo GitHub Actions

Três workflows, na raiz do repositório (`.github/workflows/`), trabalham nesta pasta:

| Workflow | Quando roda | O que faz |
|---|---|---|
| `serverless-ci.yml` ("Serverless CI") | push e pull request no `main` que mudem `applications/serverless/**` ou `applications/library-core/**` | testes da Lambda; typecheck, testes e build da SPA |
| `serverless-release.yml` ("Release serverless version") | manual, com `version` | testa, gera o build (`sam build` e a SPA) e cria a tag `serverless-vX.Y.Z` e a Release com `serverless-build.zip`, sem *Latest* |
| `serverless-deploy.yml` ("Deploy serverless version") | manual, com `version` | publica na AWS exatamente o build da Release (`SKIP_BUILD=1 scripts/deploy.sh`), com uma role assumida por OIDC |

**Uma única vez, para o deploy pelo GitHub:**
1. Crie a role (o template também cria o provedor OIDC do GitHub; se a conta já tiver um, acrescente `--parameter-overrides CreateOidcProvider=false`):
   ```sh
   aws cloudformation deploy --stack-name grade-checker-github-deploy --template-file github-oidc.yaml \
     --capabilities CAPABILITY_NAMED_IAM --tags Application=grade-checker Component=deploy Environment=test \
     ManagedBy=cloudformation Repository=professor-enoqueleal/grade-checker
   aws cloudformation describe-stacks --stack-name grade-checker-github-deploy --query 'Stacks[0].Outputs[0].OutputValue' --output text
   ```
   Só workflows do branch `main` deste repositório conseguem assumir a role. Ela só lê certificados do ACM, para associar o do domínio próprio ao CloudFront. As permissões ficam limitadas aos recursos da stack `grade-checker-serverless` sempre que a AWS permite.
2. Crie as variáveis do repositório (*Settings → Secrets and variables → Actions → Variables*; o GitHub reserva o prefixo `GITHUB_`). Nenhuma é segredo:
   ```sh
   gh variable set AWS_DEPLOY_ROLE_ARN --body "<ARN mostrado acima>"
   gh variable set APP_CLIENT_ID --body "Iv1.xxxxxxxxxxxx"
   gh variable set APP_ADMINS --body "seu-login"
   # opcionais: APP_ENVIRONMENT (padrão test), BUDGET_EMAIL, APP_DOMAIN e APP_CERTIFICATE_ARN (domínio próprio),
   # SESSION_IDLE_MINUTES (padrão 30) e SESSION_MAX_HOURS (padrão 8)
   ```
   O client secret continua só no parâmetro do SSM.

**Publicar uma versão:**
```sh
gh workflow run serverless-release.yml --ref main -f version=1.0.0
gh workflow run serverless-deploy.yml --ref main -f version=1.0.0
```

### Remover

```sh
sh scripts/destroy.sh
```

Esvazia o bucket da SPA e apaga a stack (o CloudFront leva de 5 a 15 minutos). Com `DATA_TABLE_DELETION_POLICY=Delete` (padrão), a tabela de dados vai junto. O parâmetro do SSM e o GitHub App continuam.

## Só as telas, sem backend

Para trabalhar no visual sem Docker, sem Java e sem GitHub:

```sh
cd applications/serverless/spa && npm install && npm run dev:mock
```

A API fica simulada em memória:
- "Continue with GitHub" entra como administrador;
- o token `student` entra como aluno;
- o token `bad` é recusado.

## Testes

| Onde | Comando | Precisa de |
|---|---|---|
| `lambda/` | `mvn test` | nada além de Java e Maven |
| `lambda/` | `mvn test -Pintegration` | DynamoDB Local na porta 8000 (pelo Compose ou pelo `scripts/local-dynamodb.sh`) |
| `spa/` | `npm test` e `npm run typecheck` | Node |
| `e2e/` | `npm run test:local` (administrador) e `npm run test:student` | Docker, Chrome e `gh` logado; sobe o próprio ambiente na porta 5183 e o apaga no fim |

Mais detalhes em `lambda/README.md` e `spa/README.md`.
