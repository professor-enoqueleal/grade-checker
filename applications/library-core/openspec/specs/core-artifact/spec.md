# core-artifact Specification

## Purpose
TBD - created by archiving change core-library. Update Purpose after archive.
## Requirements
### Requirement: Projeto próprio da library
A library SHALL ser um projeto Maven independente em `core/`, com o próprio `pom.xml`, sem herdar dos poms das aplicações, com a identidade `br.com.gradechecker:grade-checker-core`, empacotamento jar e bytecode Java 11 (`maven.compiler.release=11`), para servir à versão Jetty (Java 11) e à versão serverless (Java 21). O build SHALL gerar também o jar de fontes. Só o Unirest e o Gson SHALL ser dependências de compilação; as de teste MUST NOT chegar às aplicações.

#### Scenario: Build local
- **WHEN** o desenvolvedor roda `mvn -f core/pom.xml install`
- **THEN** os testes passam e o jar, o jar de fontes e o pom ficam no `~/.m2`, prontos para as duas aplicações

### Requirement: Versionamento semântico
A library SHALL seguir o versionamento semântico, com cada versão descrita no `CHANGELOG.md`: patch para correções sem mudança de regra, minor para regra nova compatível e major para mudança incompatível. Uma mudança que altere o valor de alguma nota SHALL subir pelo menos a minor. Uma versão publicada MUST NOT ser republicada com outro conteúdo.

#### Scenario: Correção de nota
- **WHEN** uma correção muda o arredondamento das notas
- **THEN** a versão sobe pelo menos a minor e o `CHANGELOG.md` explica a mudança

### Requirement: Publicação no GitHub Packages
A library SHALL ser publicada no GitHub Packages do repositório (`https://maven.pkg.github.com/professor-enoqueleal/grade-checker`) pelo workflow "Release core library" (`.github/workflows/core-release.yml`), disparado só manualmente com a entrada `version` (`X.Y.Z`), a partir do `main`. O workflow SHALL conferir que a versão é igual à do `core/pom.xml` e que a tag `core-vX.Y.Z` ainda não existe, rodar os testes e só então publicar, com o `GITHUB_TOKEN`. Depois da publicação, SHALL criar a tag `core-vX.Y.Z` no commit publicado e a Release, com o jar e o jar de fontes, sem marcá-la como a Release mais recente do repositório. Versões `-SNAPSHOT` MUST NOT ser publicadas.

#### Scenario: Publicação de uma versão
- **WHEN** o professor dispara o workflow no `main` com `version=1.0.0` e `1.0.0` no `core/pom.xml`
- **THEN** o workflow testa e publica `grade-checker-core-1.0.0.jar`, o pom e o jar de fontes, e cria a tag `core-v1.0.0` e a Release

#### Scenario: Tag já existente
- **WHEN** o workflow é disparado com uma versão cuja tag `core-vX.Y.Z` já existe
- **THEN** ele falha antes de compilar e nada é publicado

#### Scenario: Versão diferente do pom
- **WHEN** o workflow é disparado com `version=1.0.1` e o pom diz `1.0.0`
- **THEN** o workflow falha sem publicar

