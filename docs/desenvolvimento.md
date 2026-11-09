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

## Executar localmente

O uso padrão é **na sua máquina, em `http://localhost:8080`**:

```shell
sh ./mvnw -f ../library-core/pom.xml install   # a library (uma vez, e de novo depois de mudar applications/library-core)
sh ./mvnw jetty:run                            # ou rode pelo IntelliJ (Jetty na porta 8080)
```

O CI, o Docker e o workflow de release compilam a library sozinhos. Só o desenvolvimento local precisa desse `install`.

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

Há uma suíte Playwright em `applications/monolith/e2e` (veja o `README.md` dela). Por padrão, ela sobe a própria instância com `mvn jetty:run` e um banco H2 isolado, então a library precisa estar instalada (veja [Executar localmente](#executar-localmente)):

```bash
cd e2e && npm install && npm run test:local
```

## Navbar

Todas as páginas autenticadas têm a mesma navbar: a marca **"Grade Checker"** (leva à home), os links de administrador **"Manage groups"** e **"Class grades"** (só para quem está em `GRADE_CHECKER_ADMINS`), o login do usuário e **"Sign out"**. No celular, os itens recolhem num botão de menu. O breadcrumb continua em todas as páginas, abaixo da navbar.

- **Um arquivo só:** `src/main/webapp/WEB-INF/tags/navbar.tag`. Cada página usa `<%@ taglib prefix="gc" tagdir="/WEB-INF/tags" %>` e `<gc:navbar/>` (ou `<gc:navbar active="grades"/>` / `active="groups"` para destacar o item atual). Um item novo no arquivo aparece em todas as páginas.
- **Dados da sessão:** no login (GitHub ou token), `AdminConfig.signIn` grava `userLogin` e `isAdmin` na sessão, e a navbar lê os dois. A flag só mostra ou esconde links: telas e APIs continuam verificando o administrador pelo login.
- As páginas com navbar precisam do JavaScript do Bootstrap (`bootstrap.min.js`) para o menu do celular, **uma única vez**: com duas cópias (por exemplo, `bootstrap.min.js` e `bootstrap.bundle.js`), o menu abre e não fecha. O `JspStructureTest` confere isso.
