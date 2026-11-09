# Grade Checker

![Build Status](https://github.com/professor-enoqueleal/grade-checker/actions/workflows/maven.yml/badge.svg)

O Grade Checker acompanha a contribuição de cada aluno nos projetos em grupo hospedados no GitHub. Ele lê os commits do repositório de cada grupo, mostra quanto cada aluno contribuiu em cada período de avaliação e calcula uma nota de contribuição por marco.

- **Alunos** conferem a própria contribuição e entendem como a nota é calculada.
- **Professores** (administradores) cadastram os grupos e os alunos, corrigem commits que não foram atribuídos a ninguém e exportam as notas da turma.

## Como entrar

Acesse a plataforma e clique em **Continue with GitHub**. O GitHub pede uma única vez a autorização para ler os repositórios. Repositórios públicos funcionam sem nenhuma configuração extra. Para um repositório privado, o app do Grade Checker precisa estar instalado nele.

## Para os alunos

### Dashboard do grupo

A página inicial lista os grupos. Ao abrir um grupo, você vê:

- **Contributors and members:** um card para cada aluno, com o link **Details** para o histórico de commits dele.
- **Tabela de contribuições:** commits, linhas adicionadas e removidas e a porcentagem de cada autor, além da nota.
- **Gráficos:** as notas por marco e o volume de linhas de cada autor.
- **"Como as notas são calculadas":** a explicação da nota, no fim da página.

Os botões **Milestone 1**, **Milestone 2**, **Milestone 3** e **All** mudam o período. **All** mostra o histórico inteiro e as notas dos três marcos somadas. O período **Custom** permite escolher as datas.

### Histórico de commits

O link **Details** de cada aluno abre a lista dos commits que contaram para ele, com um gráfico da frequência de commits (por dia ou por semana) e o total de dias ativos.

### Como a nota é calculada

Cada marco vale **3 pontos**, e o total máximo é **9**. A nota mede o **equilíbrio da contribuição dentro do grupo**, só com o que está no git:

- A contribuição de cada aluno combina três medidas: **linhas efetivas** (50%), **dias com commit** (30%) e **commits relevantes**, com 20 linhas ou mais (20%).
- Commits muito grandes têm um limite de linhas, e reorganizações de arquivos contam pouco.
- Quem fez pelo menos **80% da sua parte justa** tira a nota máxima. Por exemplo, num grupo de 4 alunos, cada um com 25%, todos tiram 3. Abaixo disso, a nota cai na proporção.
- Quem não fez commits no marco tira **0**.
- Um marco em andamento aparece como **"(in progress)"**.

A caixa "Como as notas são calculadas", no fim do dashboard, mostra os números exatos e um exemplo.

### Faça seus commits contarem

O GitHub liga cada commit à sua conta **pelo e-mail configurado no git**, e não pelo nome. Um commit feito com um e-mail que não está na sua conta aparece com o selo **"No GitHub account"** e **não conta para a sua nota**.

1. **Cadastre o e-mail institucional na sua conta do GitHub:** *Settings* → *Emails* → *Add email address*, e confirme pelo link que chegar. Depois disso, o GitHub liga automaticamente os commits antigos feitos com esse e-mail, inclusive os dos computadores do laboratório.
2. **Confira a configuração do git** em cada computador que usar:
   ```bash
   git config --global user.name  "Nome Sobrenome"
   git config --global user.email "email-cadastrado-no-github@exemplo.com"
   git config --global user.email   # mostra o valor atual
   ```
   Um valor como `=` ou um e-mail digitado errado gera commits que nunca são atribuídos a você.

## Para os professores (administradores)

Os administradores veem na barra de navegação os links **Manage groups** e **Class grades**.

- **Grupos (Manage groups):** cadastre cada grupo com o nome e a URL do repositório no GitHub. É possível editar o nome e a URL sem perder os dados, e arquivar um grupo no fim do semestre. Um grupo arquivado sai da página inicial e das notas, e pode ser restaurado.
- **Membros:** os membros são os alunos que recebem nota. No início do semestre, marque cada aluno com o link **Mark** no card dele. Para um aluno que ainda não fez commits, use **Manage members** → **Add a new member**. Nessa mesma tela você edita o login de um aluno (as correções feitas para ele são mantidas) ou o remove.
- **Commits sem conta:** quando um commit aparece como "No GitHub account" e o sistema não consegue descobrir de quem ele é, use **Link to student** na linha correspondente do dashboard. Os commits dessa identidade passam a contar para o aluno escolhido em todos os períodos. **Unlink** desfaz a associação.
- **Notas da turma (Class grades):** lista todos os alunos de todos os grupos com as notas M1, M2, M3 e o total. A tabela pode ser ordenada e filtrada, e o botão **Export CSV** gera um arquivo que abre direto no Excel e no LibreOffice.

## Documentação técnica

- [Desenvolvimento](docs/desenvolvimento.md): rodar localmente, variáveis de ambiente, banco de dados e testes.
- [Configuração](docs/configuracao.md): GitHub App, administradores, marcos, regras da nota, dashboard e cache.
- [Deploy com Docker](docs/deploy.md): imagem, container e controle do cache em produção.
- [API](docs/api.md): rotas usadas pelas telas, erros e API administrativa do cache.
