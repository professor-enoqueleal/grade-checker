# Desenvolvimento

O monolith (versão Jetty) fica em `applications/monolith`, e os comandos abaixo rodam nessa pasta. O domínio, as regras da nota e o cliente do GitHub vêm da library `grade-checker-core`, em `applications/library-core`.

## Variáveis de ambiente

Linux e macOS:

```shell
export GITHUB_CLIENT_ID=your-client-id
export GITHUB_CLIENT_SECRET=your-client-secret
export GITHUB_BASE_URL=https://github.com
export GRADE_CHECKER_ADMINS=your-github-login
```

Windows:

```shell
set GITHUB_CLIENT_ID=your-client-id
set GITHUB_CLIENT_SECRET=your-client-secret
set GITHUB_BASE_URL=https://github.com
set GRADE_CHECKER_ADMINS=your-github-login
```

Sem `GRADE_CHECKER_ADMINS`, ninguém vê membros, associações nem as notas da turma. As demais variáveis estão em [Configuração](configuracao.md).

## Pré-requisitos

- **Java 17 ou 21**, no terminal e no IntelliJ (*Settings → Build Tools → Maven → Runner → JRE* e *Project Structure → SDK*). No Java 25, os testes falham com "Mockito cannot mock this class": a versão do Mockito do projeto não suporta o Java 25.
- **A library `grade-checker-core`**, de um destes dois jeitos:
  - **Versão publicada (GitHub Packages):** o Maven baixa sozinho a versão do `pom.xml`, mas o GitHub Packages exige um token com `read:packages` **mesmo com o repositório público**. Crie o `~/.m2/settings.xml` (o IntelliJ usa o mesmo arquivo, em *Settings → Build Tools → Maven → User settings file*):

    ```xml
    <settings>
      <servers>
        <server>
          <id>github</id>
          <username>SEU_LOGIN_DO_GITHUB</username>
          <password>${env.GITHUB_TOKEN}</password>
        </server>
      </servers>
    </settings>
    ```

    e deixe o token na variável `GITHUB_TOKEN` (por exemplo, `export GITHUB_TOKEN=$(gh auth token)` no `~/.zshrc`; o `gh` precisa do escopo `read:packages`: `gh auth refresh -s read:packages`).
    - **IntelliJ aberto pelo Dock ou pelo Spotlight não lê o `~/.zshrc`:** defina `GITHUB_TOKEN` em *Maven → Runner → Environment variables*, abra o IntelliJ pelo terminal, ou ponha o token direto no `<password>` (um token só com `read:packages`, e `chmod 600 ~/.m2/settings.xml`).
    - Sem a credencial, o download falha com **401**. Depois do primeiro download, a versão fica no `~/.m2/repository` e os builds seguintes não precisam mais dela.
  - **Compilada do repositório:** `sh ./mvnw -f ../library-core/pom.xml install`. Use quando estiver mudando a library; repita depois de cada mudança em `applications/library-core`.

O CI, o Docker e o workflow de release compilam a library sozinhos, sem token.

## Executar localmente

O uso padrão é **na sua máquina, em `http://localhost:8080`**:

```shell
sh ./mvnw jetty:run        # ou rode pelo IntelliJ (Jetty na porta 8080)
```

Se o *maven wrapper* não tiver permissão de execução:

```shell
chmod +x mvnw
```

- **Banco:** H2 em arquivo, em `~/h2db/grade-checker`. Para usar outro, defina `DB_URL`, `DB_USER` e `DB_PASS`.
- **`db.sql`:** roda a cada início e cria só o que falta. Os `DROP TABLE` do começo do arquivo **precisam ficar comentados**: ativos, cada início apaga o banco inteiro (grupos, membros e associações), e dois testes unitários falham para avisar.
- **Console do H2:** `http://localhost:8080/console`, acessível só a partir da própria máquina.
- **Gráficos:** carregam o Chart.js de `cdn.jsdelivr.net`, então a página precisa de internet para desenhá-los.
- **Logs:** configuração em `src/main/resources/log4j2.xml` ([Log4j 2 Pattern Layout](https://logging.apache.org/log4j/2.x/manual/pattern-layout.html)).

### Dados iniciais (seeds do `db.sql`)

- O `db.sql` só precisa trazer os repositórios (grupos). Com um banco novo, os grupos começam **sem membros**, e o dashboard e `/grades` mostram "No students registered" até o administrador marcar os alunos.
- Um grupo arquivado continua no banco com a mesma URL, então o seed não o recria. **Mas**, se você trocar a URL de um grupo que veio de um seed, no próximo início o seed não acha mais a URL antiga e recria o grupo original: atualize o `db.sql` junto.

## Testes

```shell
sh ./mvnw test
```

Os testes precisam que os módulos não nomeados acessem o pacote `java.util`. No Maven isso já está no `pom.xml` (`maven-surefire-plugin`). Para rodar os testes pela IDE, adicione à configuração da JVM do teste:

```
--add-opens java.base/java.util=ALL-UNNAMED
```

A aplicação em si (IntelliJ e Docker) não precisa dessa opção.

### Testes E2E

Há uma suíte Playwright em `applications/monolith/e2e` (veja o `README.md` dela). Por padrão, ela sobe a própria instância com `mvn jetty:run` e um banco H2 isolado, então a library precisa estar disponível (veja [Pré-requisitos](#pré-requisitos)):

```bash
cd e2e && npm install && npm run test:local
```

## Navbar

Todas as páginas autenticadas têm a mesma navbar: a marca **"Grade Checker"** (leva à home), os links de administrador **"Manage groups"** e **"Class grades"** (só para quem está em `GRADE_CHECKER_ADMINS`), o login do usuário e **"Sign out"**. No celular, os itens recolhem num botão de menu. O breadcrumb continua em todas as páginas, abaixo da navbar.

- **Um arquivo só:** `src/main/webapp/WEB-INF/tags/navbar.tag`. Cada página usa `<%@ taglib prefix="gc" tagdir="/WEB-INF/tags" %>` e `<gc:navbar/>` (ou `<gc:navbar active="grades"/>` / `active="groups"` para destacar o item atual). Um item novo no arquivo aparece em todas as páginas.
- **Dados da sessão:** no login (GitHub ou token), `AdminConfig.signIn` grava `userLogin` e `isAdmin` na sessão, e a navbar lê os dois. A flag só mostra ou esconde links: telas e APIs continuam verificando o administrador pelo login.
- As páginas com navbar precisam do JavaScript do Bootstrap (`bootstrap.min.js`) para o menu do celular, **uma única vez**: com duas cópias (por exemplo, `bootstrap.min.js` e `bootstrap.bundle.js`), o menu abre e não fecha. O `JspStructureTest` confere isso.
