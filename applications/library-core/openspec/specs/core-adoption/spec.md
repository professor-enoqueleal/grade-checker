# core-adoption Specification

## Purpose
TBD - created by archiving change core-library. Update Purpose after archive.
## Requirements
### Requirement: As duas aplicações usam a library
A versão Jetty (raiz) e a versão serverless (`serverless/backend`) SHALL depender de `br.com.gradechecker:grade-checker-core` com a versão fixa no pom e o repositório `github`, e MUST NOT manter cópias das classes da library. Cada aplicação SHALL ficar só com o que é particular dela: HTTP, sessão, persistência, implementação de `ResultCache`, montagem de `GitHubOAuthSettings`, `AdminConfig` e os seus `.properties`. As duas SHALL incluir `log4j-jpl`, para os logs da library chegarem ao Log4j.

#### Scenario: Sem cópias
- **WHEN** alguém procura `GradeCalculator.java` no repositório
- **THEN** ele existe só em `core/`

### Requirement: Mesmo comportamento
Adotar a library MUST NOT mudar o comportamento das aplicações: os testes unitários e de integração das duas, as duas suítes E2E (como administrador e como aluno) e uma nova comparação de notas Jetty × serverless com os mesmos grupos e membros SHALL passar sem diferenças.

#### Scenario: Comparação de notas
- **WHEN** as duas versões rodam com a library e os mesmos dados
- **THEN** notas da turma, relatórios por período e históricos de commits são iguais campo a campo

### Requirement: Builds a partir do repositório
Todo build que parte do repositório SHALL compilar e instalar a library a partir de `core/` antes da aplicação:
- CI;
- `Dockerfile` da raiz;
- `serverless/backend/Dockerfile` e Docker Compose.

A EC2 não faz build: ela usa o WAR já compilado da Release do GitHub (change `ec2-release-war`, no OpenSpec da raiz).

Assim o build não precisa de token enquanto a versão pedida pela aplicação for a de `core/pom.xml`. Para outra versão, o build SHALL baixá-la do GitHub Packages com um token `read:packages`, que MUST NOT ser gravado no Git nem numa imagem:
- **CI:** `GITHUB_TOKEN` com `packages: read`;
- **Docker:** secret de build opcional `maven_settings`;
- **Desenvolvimento e `sam build`:** `~/.m2/settings.xml`.

Versões `-SNAPSHOT` MUST NOT ser buscadas no GitHub Packages.

#### Scenario: Docker sem token
- **WHEN** o desenvolvedor roda `docker compose up --build` sem `~/.m2/settings.xml` e o backend pede a versão que está em `core/`
- **THEN** a imagem compila a library de `core/` e o build termina sem token

#### Scenario: CI
- **WHEN** o workflow da versão Jetty roda num push
- **THEN** ele instala a library de `core/` e compila a versão Jetty com ela

