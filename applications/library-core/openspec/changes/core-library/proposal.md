> **Estrutura de pastas (2026-11-09):** o repositório foi reorganizado depois desta change. `core/` virou `applications/library-core`, a versão Jetty (antes na raiz) virou `applications/monolith` e `serverless/backend` e `serverless/frontend` viraram `applications/serverless/lambda` e `applications/serverless/spa`. Os caminhos antigos abaixo são o registro da época.

## Why

O núcleo do Grade Checker existe **copiado** em duas aplicações: a versão Jetty (raiz) e a versão serverless (`serverless/backend`).
- **Cópias:** 26 arquivos idênticos e 6 com acréscimos só na serverless.
- **Risco:** uma correção na regra da nota precisa ser feita duas vezes, e as cópias podem divergir sem que ninguém perceba.

A análise em `core/plan.md` propõe isolar esse núcleo numa library Maven versionada, usada como dependência pelas duas aplicações. Esta é a primeira change do plano (fases 1 a 3): criar a library com o código que já é comum e fazer as duas aplicações a usarem, sem mudar comportamento.

## What Changes

- **Novo projeto Maven `core/`:** o artefato `br.com.gradechecker:grade-checker-core`, com o próprio `pom.xml`, compilado para Java 11, com jar de fontes e `CHANGELOG.md`.
- **O que vai para a library:**
  - o código comum das duas versões, em pacotes `br.com.gradechecker.core.*`: `grading`, `period`, `attribution`, `history`, `group`, `view`, `application`, `github`, `port`;
  - dos 6 arquivos que divergiram, a versão serverless: leitura única do histórico, com o mesmo resultado provado.
- **Ajustes de pureza:**
  - o Log4j sai e entra `System.Logger`, ligado ao Log4j nas aplicações;
  - o cache estático sai e entra a porta `ResultCache`, implementada por cada aplicação;
  - a configuração OAuth por variáveis estáticas sai e entra `GitHubOAuthSettings`, montado por cada aplicação;
  - as anotações do Gson saem do domínio.
  - Unirest e Gson ficam só no pacote `github`.
- **Publicação no GitHub Packages:** por um workflow manual do GitHub Actions ("Release core library"), que cria a tag `core-vX.Y.Z`, com versionamento semântico.
- **As duas aplicações passam a depender de `grade-checker-core:1.0.0`** e apagam as suas cópias. Ficam só com o que é particular de cada uma (HTTP, sessão, persistência, cache, configuração).
- **Builds:** CI, Dockerfiles e Docker Compose compilam a library a partir de `core/` antes da aplicação. A EC2 não compila: usa o WAR da Release do GitHub (change `ec2-release-war`, no OpenSpec da raiz). O token de leitura do GitHub Packages (`GITHUB_TOKEN` no CI, secret de build opcional no Docker, `~/.m2/settings.xml`) só é usado para uma versão diferente da de `core/`.
- **Fora desta change:** portas de persistência, invariantes no domínio, casos de uso, erros do domínio e a regra de fronteira completa. Isso fica para a change seguinte, a fase 4 do plano.

## Capabilities

### New Capabilities
- `core-artifact`: identidade, build, versionamento e publicação da library.
- `core-boundaries`: conteúdo, pacotes e regras de dependência da library.
- `core-adoption`: como as duas aplicações usam a library, com o mesmo comportamento de antes.

### Modified Capabilities
- (nenhuma neste OpenSpec, que é novo. No OpenSpec da versão serverless, `backend-project` deixa de dizer que o core é "copiado"; no da versão Jetty, `ec2-deployment` ganha o token do GitHub Packages. Os dois deltas entram nas tarefas.)

## Impact

- **Novo:** `core/` (pom, código, testes, `CHANGELOG.md`, README, OpenSpec) e `.github/workflows/core-release.yml`.
- **Versão Jetty:** `pom.xml`, `src/` (sem as classes que foram para a library; imports novos), `Dockerfile`, `.github/workflows/maven.yml`, `infra/cloudformation/grade-checker-ec2.yaml`, `readme.md`.
- **Versão serverless:** `backend/pom.xml`, `backend/src/` (sem as cópias; imports novos), `backend/Dockerfile`, `docker-compose.yml`, READMEs.
- **Comportamento:** nenhuma mudança visível, confirmada pelos testes das duas aplicações, pelas duas suítes E2E e pela comparação de notas Jetty × serverless.
