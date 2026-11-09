# core-errors Specification

## Purpose
Erros do domínio com tipo, código estável e a mensagem da API, traduzidos pelos adaptadores (HTTP: 400, 404, 409).
## Requirements
### Requirement: Erros do domínio com código estável
Os casos de uso SHALL lançar `br.com.gradechecker.core.error.DomainException`, com um tipo (`INVALID`, `NOT_FOUND` ou `CONFLICT`), um código estável e a mensagem em inglês já usada pela API. O adaptador HTTP SHALL traduzir o tipo para 400, 404 ou 409. Erros do GitHub SHALL continuar como `GitHubApiException`, com o status e a mensagem do GitHub.

#### Scenario: Repositório já cadastrado num grupo arquivado
- **WHEN** um administrador cadastra um repositório que pertence a um grupo arquivado
- **THEN** o caso de uso lança `CONFLICT`, com o código `already_registered` e a mensagem "This repository is already registered in an archived group. Restore it instead."
- **AND** a API responde 409 com esse código e essa mensagem

