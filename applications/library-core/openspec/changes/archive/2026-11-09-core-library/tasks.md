## 1. Library

- [x] 1.1 `core/pom.xml` (`br.com.gradechecker:grade-checker-core`, `--release 11`, Unirest e Gson, testes, jar de fontes, `distributionManagement` do GitHub Packages), `.gitignore`, `CHANGELOG.md`, README
- [x] 1.2 Código em `br.com.gradechecker.core.*` (pacotes do design), a partir da versão serverless nos 6 arquivos que divergiram
- [x] 1.3 Pureza: `System.Logger`; `ResultCache` no `GitHubProxy`; `GitHubOAuthSettings` nos proxies; sem `@SerializedName` no domínio; configuração pelo carregador de contexto
- [x] 1.4 Testes do núcleo (uma vez só), `GroupGradesSingleHistoryTest`, `ResultCache` em memória nos testes de cache, teste de arquitetura
- [x] 1.5 `mvn -f core/pom.xml install` (1.0.0-SNAPSHOT)

## 2. Versão serverless adota

- [x] 2.1 `backend/pom.xml`: dependência `grade-checker-core`, repositório `github`, `log4j-jpl`
- [x] 2.2 Apagar as cópias, trocar os imports; `ResultCache` com Caffeine + `DynamoCache`; `GitHubOAuthSettings` a partir das variáveis e do SSM
- [x] 2.3 `backend/Dockerfile` e `docker-compose.yml`: library compilada de `core/` (contexto adicional), secret `maven_settings` opcional; READMEs
- [x] 2.4 `mvn test`, `mvn test -Pintegration`, `sam build`, `docker compose up --build`, E2E (administrador e aluno)
- [x] 2.5 OpenSpec de `serverless/`: `backend-project` deixa de dizer que o core é copiado (change própria)

## 3. Versão Jetty adota

- [x] 3.1 `pom.xml`: dependência, repositório `github`, `log4j-jpl`
- [x] 3.2 Apagar as cópias, trocar os imports (servlets, JSPs que referenciam classes, testes); `ResultCache` com o `CacheManager`; `GitHubOAuthSettings` a partir do `GitHubOAuthConfig`; `ContributorsService` com as associações do `IdentityAliasRepository`
- [x] 3.3 `Dockerfile` (library compilada de `core/`, secret opcional); `.github/workflows/maven.yml` (instala `core/`, `server-id: github`, `packages: read`); `readme.md`
- [x] 3.4 EC2: o script compilava `core/` antes do WAR (sem token); substituído pela change `ec2-release-war` da raiz, em que a instância usa o WAR da Release sem build
- [x] 3.5 `mvn test`, E2E da versão Jetty (administrador e aluno), build Docker

## 4. Publicação e aceite

- [x] 4.1 `.github/workflows/core-release.yml`: disparo manual com `version` = versão do pom, só no `main`, tag inexistente, `verify`, `deploy` com `GITHUB_TOKEN`, tag `core-vX.Y.Z` e Release com os jars (sem *Latest*)
- [x] 4.2 Versão `1.0.0` no `core/pom.xml` e nas duas aplicações; workflow "Release core library" disparado pelo professor com `version=1.0.0` (2026-11-09): 145 testes, jar, jar de fontes e pom no GitHub Packages, tag `core-v1.0.0` e Release sem *Latest*
- [x] 4.3 Comparação de notas Jetty × serverless sem diferenças
- [x] 4.4 `core/plan.md` (andamento) e `serverless/migration-plan.md`; validar e arquivar
