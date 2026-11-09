# core-boundaries Specification

## Purpose
TBD - created by archiving change core-library. Update Purpose after archive.
## Requirements
### Requirement: Conteúdo da library
A library SHALL conter, em pacotes `br.com.gradechecker.core.*`, o núcleo comum às duas versões:
- `grading`, `period` e `attribution`: nota, períodos e atribuição de autores;
- `history`: histórico e relatório;
- `group`: grupo e membro;
- `github`: o que o GitHub devolve (contribuidor, repositório, erro);
- `view`: dados prontos para a tela, iguais nas duas versões;
- `application`: casos de uso, entre eles o relatório e as notas de All;
- `port`: as portas, entre elas o `ResultCache`;
- `adapter.github`: o cliente HTTP do GitHub.

Nos arquivos que divergiram entre as versões, SHALL valer a versão serverless, inclusive a leitura única do histórico para as notas de All. O resultado dela é igual ao das três leituras, e o cálculo volta às três quando o histórico vem truncado. HTTP, sessão, persistência, implementações de cache e leitura de variáveis de ambiente MUST NOT estar na library.

#### Scenario: Mesmo cálculo
- **WHEN** os testes do núcleo e o `MilestoneGradesTest` (uma leitura do histórico × uma leitura por marco) rodam na library
- **THEN** todos passam, com as mesmas notas que as duas versões calculavam antes

### Requirement: Dependências permitidas
Nenhum arquivo da library SHALL importar `jakarta.*`, `javax.servlet.*`, `java.sql.*`, `software.amazon.*`, `com.github.benmanes.*` ou `org.apache.logging.*`. `kong.unirest.*` e `com.google.gson.*` SHALL aparecer só no pacote `core.adapter`. Os logs SHALL usar `java.lang.System.Logger`. Um teste de arquitetura da library SHALL falhar quando uma dessas regras for quebrada.

#### Scenario: Import proibido
- **WHEN** alguém adiciona `import kong.unirest.Unirest;` a uma classe de `core.grading`
- **THEN** o teste de arquitetura falha e aponta o arquivo

### Requirement: Infraestrutura injetada
O cache das respostas do GitHub SHALL ser a porta `ResultCache`, com get por chave e tipo e put, recebida pelo `GitHubClient` (padrão: sem cache), com as chaves de hoje (a dos contribuidores com `v2`, desde a change `core-naming`). As configurações do OAuth SHALL ser recebidas por `GitHubOAuthSettings` (client id, client secret por `Supplier`, URL base e URL da API), montado pela aplicação. `GradingConfig` e `Milestones` SHALL ler `/grading.properties` e `/milestones.properties` do classpath da aplicação (carregador de contexto da thread), com os arquivos mantidos em cada aplicação.

#### Scenario: Cache de cada aplicação
- **WHEN** a versão serverless entrega ao `GitHubClient` um `ResultCache` com Caffeine e DynamoDB, e a versão Jetty entrega um com o Caffeine
- **THEN** a library usa o cache recebido sem conhecer nenhum dos dois

