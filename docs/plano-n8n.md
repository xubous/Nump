# Plano de execução — Integração Nump + n8n

> Documento de trabalho. Complementa a seção **10. Visão n8n / Roadmap** do `AGENTS.md`.
> Objetivo de negócio: **reduzir o tempo de atendimento técnico ao cliente** (liberar acesso
> remoto, enviar ferramentas de suporte, coletar logs, etc.).

## Como usar este plano

- A trilha está dividida em **fases** do mais simples (aprender) ao objetivo final (acesso remoto).
- Cada fase tem: **objetivo**, **pré-requisitos**, **tarefas**, **entregável** e **critério de pronto**.
- Marque `[x]` conforme concluir. A IA deve atualizar este arquivo a cada avanço.
- Aprender e construir caminham juntos: a Fase 0–1 ensina n8n; a Fase 2+ mexe no Nump.

## Estado atual (o que NÃO existe ainda)

Do `AGENTS.md` seção 8/10 — nada abaixo está implementado:

- Nump **não emite webhooks** (sem `WebhookPublisher`).
- Upload **sem metadados** (recipient, description, expiresAt, maxDownloads).
- Token **sem expiração, sem uso único, sem contagem de downloads**.
- Storage só em disco local (`user.dir/uploads`) — não serve para ambiente efêmero.
- Sem endpoint de **consentimento** de acesso remoto.

---

## Fase 0 — Subir o n8n localmente (aprender a ferramenta)

**Objetivo:** ter a UI do n8n rodando e entender os conceitos básicos.

**Pré-requisitos:** Docker instalado; Nump rodando local (H2) com `JWT_SECRET`.

**Tarefas:**
- [ ] Subir n8n via Docker (`docker run` ou serviço no `compose.yaml`).
- [ ] Acessar a UI (`http://localhost:5678`).
- [ ] Criar um workflow "hello world": `Manual Trigger` → `Set` (definir uma mensagem).
- [ ] Entender: workflow, node, trigger, execução, painel de execuções.

**Entregável:** print/tela de um workflow executando com sucesso.

**Pronto quando:** você sabe explicar o que é trigger, node e execução.

---

## Fase 1 — n8n → Nump (integração de saída)

**Objetivo:** o n8n conversa com a API do Nump usando o JWT.

**Pré-requisitos:** Fase 0 concluída.

**Tarefas:**
- [ ] `HTTP Request` → `POST /users/login` com `{ email, password }` → capturar `token`.
- [ ] Guardar o token numa variável (nó `Set` ou expressão) e usar `Authorization: Bearer`.
- [ ] `HTTP Request` → `POST /files/upload` (multipart, campo `file`) → obter `downloadUrl`.
- [ ] Montar um fluxo: login → upload → devolver o link.

**Entregável:** workflow que sobe um arquivo no Nump e retorna o link curto.

**Pronto quando:** o link gerado baixa o `.zip` corretamente.

---

## Fase 2 — Nump → n8n (webhooks de saída)

**Objetivo:** o Nump **avisa** o n8n quando algo acontece (upload/download).

**Pré-requisitos:** decisão de onde guardar a URL do n8n (`N8N_WEBHOOK_URL`).

**Tarefas (Nump):**
- [ ] Criar `WebhookPublisher` em `service/` que faz `POST` para `N8N_WEBHOOK_URL`.
- [ ] Config por env em `application.properties` (sem segredo hardcoded).
- [ ] Disparar evento no `FileService.uploadAndCompress` (e, futuramente, no download).
- [ ] Respeitar convenções da seção 7 do `AGENTS.md`.

**Tarefas (n8n):**
- [ ] Criar workflow com `Webhook Trigger` e copiar a URL.
- [ ] Receber o payload e, por exemplo, enviar notificação (Telegram/e-mail).

**Entregável:** upload no Nump dispara um workflow no n8n.

**Pronto quando:** o n8n recebe o payload do upload sem polling.

---

## Fase 3 — Links expiráveis / uso único / revogação

**Objetivo:** controlar validade e segurança dos links.

**Tarefas:**
- [ ] Adicionar em `File`: `expiresAt`, `maxDownloads`, `downloadCount`, `revoked`.
- [ ] Validar no download (`GET /files/r/{token}`): expirado/revogado/limite → 410/403.
- [ ] Incrementar `downloadCount` e bloquear quando atingir `maxDownloads`.
- [ ] Metadados no upload (opcional): recipient, description, expiresAt, maxDownloads.

**Entregável:** link que expira e/ou vale uso único.

**Pronto quando:** testes manuais confirmam expiração, revogação e limite.

---

## Fase 4 — Link de consentimento de acesso remoto (RustDesk)

**Objetivo:** o objetivo central do autor — cliente autoriza acesso em 1 clique.

**Tarefas:**
- [ ] Endpoint público de consentimento que registra timestamp, IP e user-agent.
- [ ] Página pública simples (HTML em `static/`) com botão "Autorizo o acesso remoto".
- [ ] Ao consentir → chamar webhook do n8n → n8n libera a sessão.
- [ ] n8n envia ao técnico o ID do RustDesk (e o servidor, se houver).
- [ ] Link expira automaticamente (ex.: 15 min) e/ou após uso único (usar Fase 3).
- [ ] Trilha de auditoria do consentimento (quem, quando, de qual IP).

**Entregável:** fluxo completo: técnica gera link → cliente aceita → sessão liberada.

**Pronto quando:** o ciclo roda de ponta a ponta num ambiente de teste.

---

## Backlog / ideias futuras (não priorizado)

- Bot de WhatsApp/Telegram para triagem de chamados.
- Triagem por IA (classificar mensagem e rotear para o técnico).
- Storage externo (S3/MinIO) para uploads.
- Integração com ticketing/CRM (GLPI, Freshdesk, Google Sheets).
- Agendamento de atendimento + lembrete com link.
- Notificação de download (confirmação de recebimento).
- Pesquisa de satisfação pós-atendimento.

## Referências

- `AGENTS.md` — contexto do projeto e convenções.
- Endpoints atuais do Nump: `AGENTS.md` seção 5.
- Docs n8n: https://docs.n8n.io
