# OpenSpec

## Exemplo 1
```text
/opsx:propose "Preciso gerar um documento da estrutura detalhada e arquitetura do projeto. Esse documento deve ter também um diagrama de arquitetura. O documento deve ser claro e fácil de entender, mesmo para pessoas que não estão familiarizadas com o projeto."
```

## Exemplo 2
```text
/opsx:propose "Usar DAO com try-with-resources para fechar conexões com segurança. Externalizar strings SQL para constantes ou usar um ORM leve (JDBI) para reduzir boilerplate. Adicionar testes unitários e integração para GitHubProxy (mock Unirest) e repositórios (H2 in-memory). Remover credenciais sensíveis do código e definir um fluxo seguro de tokens."
```

## Exemplo 3
```text
/opsx:propose "adicionar cache em memory para resultados de consultas frequentes, usando uma biblioteca leve como Caffeine. O cache deve ser configurável para expirar após um tempo definido e deve ser integrado de forma transparente com os DAOs, sem alterar a interface pública dos repositórios. Uma variável de ambiente deve ser usada para habilitar/desabilitar o cache em diferentes ambientes (desenvolvimento, produção). Uma vaiável de ambiente para TTL do cache também deve ser criada, permitindo ajustes sem necessidade de recompilar o código."
```

---

# OpenSpec — Comandos essenciais para o fluxo de mudanças

# 🧠 Visão geral (o fluxo completo)

```text
explore → propose → (refinar/spec/tasks) → apply → archive
```

👉 Cada comando tem um papel bem específico nesse fluxo.

---

# 🔍 `/opsx:explore` → ENTENDER

👉 Serve para **analisar o sistema antes de propor mudanças**

Você usa quando:

* não conhece bem o código
* quer mapear arquitetura
* quer entender impacto de mudanças

💡 Exemplo:

```text
/opsx:explore "How authentication is currently implemented"
```

👉 Resultado esperado:

* análise do código
* explicação de módulos
* nenhum arquivo criado

---

# 💡 `/opsx:propose` → CRIAR UMA MUDANÇA

👉 Aqui você inicia uma **change**

Ele:

* cria uma pasta em `openspec/changes/`
* gera arquivos como:

    * proposal.md
    * spec.md
    * tasks.md

💡 Exemplo:

```text
/opsx:propose "Add JWT authentication"
```

👉 Resultado:

* estrutura de mudança criada
* conteúdo inicial gerado pela IA

---

# ⚙️ `/opsx:apply` → APLICAR A MUDANÇA

👉 Esse é o momento onde a mudança vira “real”

Ele:

* move a spec para `openspec/specs/`
* pode gerar/alterar código (dependendo da integração)
* atualiza o estado do sistema

💡 Em outras palavras:

> “Essa mudança agora faz parte do sistema”

---

# 📦 `/opsx:archive` → FINALIZAR E GUARDAR HISTÓRICO

👉 Depois que a mudança já foi aplicada:

Ele:

* move a change para `openspec/archive/`
* mantém histórico da decisão

💡 Serve para:

* auditoria
* rastreabilidade
* entendimento futuro

---

# 🔥 Diferença resumida (bem clara)

| Comando         | Função                   | Cria arquivos? | Quando usar               |
| --------------- | ------------------------ | -------------- | ------------------------- |
| `/opsx:explore` | Entender o sistema       | ❌              | Antes de qualquer mudança |
| `/opsx:propose` | Criar uma mudança        | ✅              | Quando você tem uma ideia |
| `/opsx:apply`   | Tornar a mudança oficial | ✅              | Após validar a spec       |
| `/opsx:archive` | Guardar histórico        | ✅              | Depois de finalizado      |

---

# 🧠 Mentalidade correta

Pensa assim:

* **explore** → investigação
* **propose** → intenção
* **apply** → decisão
* **archive** → memória

---

# ⚠️ Erro comum (importante)

Muita gente tenta pular direto para:

```text
propose → apply
```

👉 Sem explorar e refinar

Resultado:

* specs ruins
* decisões mal pensadas

---

# 🚀 Fluxo ideal (nível profissional)

```text
/opsx:explore "entender contexto"
/opsx:propose "definir mudança"
(refinar spec com IA)
/opsx:apply
/opsx:archive
```

---

# ✅ Resumo final

* `/opsx:explore` → entender (não altera nada)
* `/opsx:propose` → criar mudança
* `/opsx:apply` → aplicar no sistema
* `/opsx:archive` → guardar histórico
