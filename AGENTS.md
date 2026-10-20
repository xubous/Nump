# AGENTS.md — Contexto do projeto Nump

> Este arquivo contextualiza o projeto para assistentes de IA (opencode, Cursor, Claude, etc.).
> Leia antes de propor ou alterar código. Mantenha-o atualizado quando o projeto mudar.

## 1. Visão geral

**Nump** é uma plataforma de **compartilhamento de arquivos com compactação automática** e
**encurtamento de links**, escrita em **Spring Boot (Java 21)**. Proposta: alternativa ao
MediaFire — o arquivo enviado vira `.zip`, recebe um token curto e um link de download curto.

O objetivo de negócio do autor é evoluir o Nump para uma ferramenta de **atendimento técnico a
clientes**, integrando **n8n** para orquestrar automações que reduzam o tempo de atendimento
(ex.: liberação de acesso remoto, envio de arquivos/ferramentas de suporte, etc.).
Já existem duas funcionalidades nessa linha: **F1 (transferência de arquivos)** e
**F2 (conexão/consentimento RustDesk com senha temporária)** — ver seção 5.

## 2. Stack

| Camada        | Tecnologia |
|---------------|-----------|
| Linguagem     | Java 21 |
| Framework     | Spring Boot **4.0.5** (webmvc, data-jpa, security, validation, actuator) |
| Auth          | JWT (jjwt 0.12.5), BCrypt |
| Banco (prod)  | PostgreSQL — **Neon** (via env `DATABASE_URL`) |
| Banco (local) | H2 em memória (padrão quando `DATABASE_URL` não é definida) |
| Build         | Maven (`./mvnw`) |
| Container     | Docker multi-stage (`Dockerfile`) + `compose.yaml` |
| Frontend      | HTML/CSS/JS estáticos em `src/main/resources/static` — servidos pelo **próprio app** (arquitetura Docker única, mesma origem; **Vercel aposentado**) |
| Libs extras   | Lombok |

## 3. Comandos

```bash
# Rodar localmente (H2 em memória; JWT_SECRET é obrigatório; sobe na porta 8081)
JWT_SECRET=dev-secret-com-no-minimo-32-bytes ./mvnw spring-boot:run

# Build (o teste de contexto exige JWT_SECRET — ou use -DskipTests)
JWT_SECRET=dev-secret-com-no-minimo-32-bytes ./mvnw clean package

# Testes (JWT_SECRET é obrigatório, senão o contexto não sobe)
JWT_SECRET=dev-secret-com-no-minimo-32-bytes ./mvnw test

# Subir stack completa (app + postgres) — requer .env
docker compose up --build
```

Variáveis de ambiente (ver `application.properties` e `compose.yaml`):
`DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASS`, `DATABASE_DRIVER`,
`SPRING_JPA_DATABASE_PLATFORM`, `JWT_SECRET` (**obrigatória, sem fallback**), `APP_BASE_URL`,
`H2_CONSOLE_ENABLED`, `N8N_WEBHOOK_URL` (opcional — webhook n8n, best-effort),
`RUSTDESK_DOWNLOAD_URL` (opcional — instalador do RustDesk que o `.bat` do F2 baixa).

> **Porta:** a app sobe na **8081** por padrão (`server.port=${SERVER_PORT:8081}`; 8080 está
> bloqueado na rede local do autor). No Render, apontar a porta do serviço para **8081**.

`.env` está no `.gitignore` — nunca commitar segredos.

## 4. Estrutura

```
src/main/java/project/xubous/Nump/
├── NumpApplication.java
├── controller/      FileController, UserController, AcessoRemotoController
├── dto/             UserDTO (records de request/response)
├── global/          GlobalExceptionHandler, LinkIndisponivelException
├── model/           User, File, UploadLink, AcessoRemoto, DownloadLog, OrigemArquivo, StatusAcessoRemoto
├── repository/      UserRepository, FileRepository, UploadLinkRepository, AcessoRemotoRepository, DownloadLogRepository
├── security/        SecurityConfig, JwtService, JwtAuthFilter, UserDetailsServiceImpl
└── service/         FileService, UserService, Compactor, Shortener, AcessoRemotoService, WebhookPublisher
src/main/resources/
├── application.properties
└── static/          index.html, login.html, register.html, coleta.html, consentimento.html, favicon.ico, *.css
```

## 5. Domínio e endpoints

### Entidades
- **User**: id, name, email (único), password (BCrypt), role (USER | ADMIN).
- **File** (`table_file`): id, path, size, token, downloadUrl, `ownerEmail`,
  + F1: `description`, `expiresAt`, `maxDownloads`, `downloadCount`, `revoked`, `origem` (enum `OrigemArquivo`).
- **UploadLink** (F1, coleta/upload reverso): token de coleta, `expiresAt`, `maxUploads`, `uploadCount`, `descricao`, dono.
- **AcessoRemoto** (F2, consentimento de acesso remoto — **RustDesk com senha temporária**): token
  (24 chars, `SecureRandom`), `status` (enum `StatusAcessoRemoto`), `expiresAt`, consentimento
  (timestamp/IP/user-agent), senha temporária gerada pelo Nump (`rustdeskSenha`) + dados do n8n
  (`rustdeskId`, `rustdeskServer` — nullable), `ferramentaToken` (uso único do `.bat`) +
  `ferramentaUsadaEm`, dono.
  A senha dá **acesso não atendido**: o script busca a senha no servidor na execução, aplica na
  máquina do cliente e o técnico conecta direto, sem o cliente aceitar de novo.
- **DownloadLog** (F1): arquivo, ip, userAgent, timestamp.

### Rotas de usuário (`/users`)
| Método | Rota | Acesso |
|--------|------|--------|
| POST | `/users/register` | público → retorna `{ token, user }` |
| POST | `/users/login` | público → retorna `{ token, user }` |
| GET | `/users` | ADMIN |
| GET | `/users/{id}` | autenticado |
| PUT | `/users/{id}` | autenticado |
| DELETE | `/users/{id}` | ADMIN |

### Rotas de arquivo (`/files`) — F1 (transferência)
| Método | Rota | Acesso |
|--------|------|--------|
| GET | `/files` | autenticado (filtra por `ownerEmail` do JWT) |
| GET | `/files/{id}` | autenticado (só o dono) |
| POST | `/files/upload` | autenticado — `multipart`, campo `file` + opcional `expiraMinutos`, `maxDownloads`, `description` |
| GET | `/files/{id}/downloads` | autenticado (dono) → histórico de downloads |
| POST | `/files/{id}/revogar` | autenticado (dono) → desativa o link |
| POST | `/files/{id}/reativar` | autenticado (dono) → reativa o link |
| GET | `/files/r/{token}` | **público** → baixa o `.zip` (valida expiração/revogação/limite) |
| DELETE | `/files/{id}` | autenticado (só o dono) |

### Rotas de coleta / upload reverso (`/files`)
| Método | Rota | Acesso |
|--------|------|--------|
| POST | `/files/coletas` | autenticado (dono) → cria link de coleta (`expiraMinutos`, `maxUploads`, `descricao`) |
| GET | `/files/coletas` | autenticado (dono) → lista coletas |
| DELETE | `/files/coletas/{id}` | autenticado (dono) |
| GET | `/files/c/{token}` | **público** → redirect `/coleta.html` (página onde o cliente envia) |
| POST | `/files/c/{token}` | **público** → recebe o arquivo sem login (`multipart`, campo `file`) |

### Rotas de acesso remoto (`/acessos`) — F2 (consentimento RustDesk)
| Método | Rota | Acesso |
|--------|------|--------|
| POST | `/acessos` | autenticado → cria sessão (`clienteEmail`, `expiraMinutos`) → devolve `consentUrl` |
| GET | `/acessos` | autenticado (dono) → lista sessões |
| DELETE | `/acessos/{id}` | autenticado (dono) → cancela se pendente |
| GET | `/acessos/{token}` | **público** → redirect `/consentimento.html` |
| POST | `/acessos/{token}/consentir` | **público** → registra consentimento (IP/user-agent), gera a **senha temporária** e o `ferramentaToken` (uso único), lê o ID do RustDesk do n8n (opcional) |
| GET | `/acessos/ferramenta/{token}.bat` | **público** → gera e baixa o `.bat` "Suporte Nump": baixa o **RustDesk RÁPIDO** (curl com progresso), **busca a senha no servidor** (não a carrega em texto claro) e instala em silêncio com ela |
| GET | `/acessos/ferramenta/{fToken}/senha` | **público / uso único** → troca o `ferramentaToken` pela senha em texto puro (o `.bat` chama isso na execução); 2ª chamada → 410 |
| GET | `/acessos/{token}/encerrar.bat` | **público** → `.bat` "Encerrar atendimento": o cliente roda ao fim para **remover o acesso remoto** (limpa a senha, para o serviço e mata o RustDesk) |

> **Fluxo RustDesk (F2) — acesso NÃO ATENDIDO:** o `.bat` baixa o instalador oficial
> (`app.rustdesk.download-url`, default RustDesk **1.5.0**; env `RUSTDESK_DOWNLOAD_URL`) usando
> **`curl --progress-bar`** (rápido + dá feedback); se o `curl` não existir, cai para **BITS**
> (`Start-BitsTransfer`). A **senha NÃO vai embutida no `.bat`**: o script a busca em
> `GET /acessos/ferramenta/{fToken}/senha` (token de uso único, consumo único → 2ª vez 410);
> depois `--silent-install --password <senha>` (cliente clica **1x** em "Sim" no UAC), abre o
> RustDesk, espera o ID (`--get-id`, com retry) e mostra.
> Resultado: o técnico conecta **direto com ID + senha**, sem o cliente aceitar a conexão.
> A senha é **sempre gerada pelo Nump** (SecureRandom 12 chars, sem 0/O/1/I) no consentimento —
> a senha do n8n é ignorada de propósito. O n8n pode informar `rustdeskId`/`rustdeskServer`
> (aceita `anydeskId` como alias) e é opcional: **sem n8n o script descobre o ID localmente**.

### Fluxo de upload
1. `Shortener.generateToken()` → token de 8 chars (UUID truncado).
2. `Compactor.compactAndSave()` → grava `uploads/arquivo_<token>.zip` em `System.getProperty("user.dir")/uploads`.
3. `Shortener.generateDownloadUrl()` → `${app.base-url}/files/r/<token>`.
4. Persiste `File` com `ownerEmail = email do JWT`.

## 6. Segurança

- `SecurityConfig`: stateless, CSRF off, JWT via header `Authorization: Bearer <token>`.
- **Páginas estáticas são públicas** (GET): `/`, `/index.html`, `/login.html`, `/register.html`,
  `/style.css`, `/login.css`, `/favicon.ico` — a API continua protegida por JWT.
- Rotas públicas: `OPTIONS /**`, `POST /users/register`, `POST /users/login`, `GET /files/r/**`,
  `GET|POST /files/c/**` (coleta), `GET /acessos/{token}`, `POST /acessos/{token}/consentir`,
  `GET /acessos/ferramenta/**` (ferramenta RustDesk + senha de uso único),
  `GET /acessos/*/*.bat` (encerrar atendimento), `GET /actuator/health`.
- ⚠️ **Armadilha do Spring Security 6:** `"/acessos/**"` **também casa com `/acessos`** (PathPattern),
  o que liberaria a **listagem de sessões** anonimamente. Por isso os padrões de `/acessos` são
  **explícitos** (`/acessos/*`, `/acessos/*/*.bat`, `/acessos/ferramenta/*`, `/acessos/ferramenta/*/*`)
  — nunca use `/acessos/**` ali.
- Admin: `GET /users`, `DELETE /users/**`.
- **Arquitetura C (Docker único):** frontend e API são **mesma origem** → CORS não é usado no
  fluxo real. A config de CORS ainda lista origens (legado do Vercel, já aposentado) — pode ser simplificada.
- `FileService` sempre deriva o dono de `SecurityContextHolder`, não de parâmetro recebido.

## 7. Convenções de código (IMPORTANTE)

O autor usa uma formatação própria e consistente — **mantenha o estilo ao editar**:

- Espaço antes de parênteses de método/chamada: `public void foo ( )`, `fileService.getAllFile ( )`.
- Genéricos com espaço: `List < File >`, `Optional < File >`.
- Chaves de bloco em linha própria (Allman).
- Lombok nas entidades (`@Getter/@Setter/@NoArgsConstructor/@AllArgsConstructor`).
- DTOs como `record` aninhados em uma classe `UserDTO`.
- Mensagens de erro e comentários em **português**.
- Controllers finos: lógica em `service/`.

## 8. Estado atual / limitações conhecidas

- **Implementado:** F1 (links temporários com expiração/revogação/limite de downloads, upload
  reverso por coleta, logs de download, download em **streaming** com nome original no zip) e
  F2 (consentimento de acesso remoto **RustDesk** com senha temporária + webhook n8n opcional +
  ferramenta `.bat` que o cliente baixa com 1 clique e instala com a senha já aplicada →
  **acesso não atendido**, sem o cliente aceitar a conexão).
- **Deploy: arquitetura C (Docker único, 1 origem, sem CORS)** — porta **8081**, frontend servido
  pelo próprio app, banco Neon, `.dockerignore` + healthcheck no compose.
- **Hospedagem: guia `docs/deploy-oracle.md`** — VM Oracle Cloud **Always Free** (app + Postgres
  via `docker compose`, nunca dorme, uploads em volume `uploads_data`, `restart: unless-stopped`,
  Postgres só em `127.0.0.1`). O Render free (transição) dorme após 15 min; o Neon free suspende
  após 5 min mas acorda em ~0,5 s. O Dockerfile separa `pom.xml` de `src/` pra cachear o Maven.
- **Neon (produção): `ddl-auto=update` não adiciona coluna `NOT NULL` em tabela já com dados**
  (o ALTER falha e o Hibernate engole o erro) — mudança de entidade pode exigir ALTER manual
  (caso `download_count`/`revoked` em `table_file`, corrigido manualmente em 2026-10-16).
  Colunas **nullable** são adicionadas normalmente (caso `rustdesk_id`/`rustdesk_senha`/
  `rustdesk_server` em `table_acesso_remoto`, criadas automaticamente em 2026-10-19).
- Sem paginação em listagens.
- ⚠️ **`Compactor` grava em disco local (`user.dir/uploads`)** — no **Render** o filesystem é
  **efêmero**: arquivos enviados **somem a cada deploy/restart**. Pra produção séria, migrar pra
  storage externo (S3/MinIO).
- Download público (`/files/r/{token}`) envia o zip em **streaming** (`FileSystemResource` +
  `Content-Length`) e preserva o nome original do arquivo no nome do `.zip` baixado.
- **Inconsistência de config JWT**: `JwtService` lê `${jwt.expiration-ms:86400000}` (default 24h),
  mas `application.properties` define `jwt.expiration` → a propriedade configurada **não tem efeito**.
- `data/*.mv.db` (H2) está versionado por engano — não é usado quando `DATABASE_URL` está setada.
- `JWT_SECRET` de produção deve ser aleatório (o atual é válido, 32 bytes, mas previsível).
  Gerar com: `openssl rand -base64 48` e definir no `.env` do servidor (nunca commitar).
- **F2 endurecido (2026-10-20):** token do link de acesso remoto agora tem **24 chars**
  (`SecureRandom`, ~2^120) em vez de 8; a **senha não vai mais em texto claro dentro do `.bat`** —
  o script a busca em `GET /acessos/ferramenta/{fToken}/senha` (token de uso único; 2ª chamada 410).
  Tokens de **arquivo/coleta continuam de 8 chars** (não mudou o F1).
- **Encerramento do acesso remoto (2026-10-20):** o `.bat` "Encerrar atendimento"
  (`GET /acessos/{token}/encerrar.bat`) remove o acesso ao fim do atendimento — limpa a senha
  (`--password ""`), para o serviço (`--service-uninstall`) e mata o processo. A ferramenta e a
  página do cliente orientam o cliente a rodá-lo. **A senha do RustDesk não expira sozinha** — sem
  esse `.bat`, o acesso continua válido enquanto o RustDesk estiver instalado.
- Testes automatizados: apenas `NumpApplicationTests` (context load) — **exige `JWT_SECRET`
  definido** (`JWT_SECRET=... ./mvnw test`), senão o contexto não sobe. O restante é coberto pelos
  planos manuais em `docs/` (`roteiro-de-testes.md`, `plano-de-testes*.md`).
- O `.bat` do F2 foi validado no nível do servidor (conteúdo/HTTP; fluxo completo com n8n
  fora do ar) — a **execução em Windows real** ainda depende do roteiro manual em `docs/`.

## 9. Integração com n8n — como plugar (técnico)

O n8n pode conversar com o Nump de duas formas:

1. **n8n → Nump (saída)**: nós HTTP Request chamam a API REST do Nump.
   - Autenticar: `POST /users/login` → guardar o `token` → usar `Authorization: Bearer`.
   - Upload de arquivo: `POST /files/upload` (multipart, campo `file`).
   - Criar link de coleta/sessão de acesso, listar arquivos, deletar.
2. **Nump → n8n (entrada)**: o Nump **já emite webhooks** via `WebhookPublisher` (env
   `N8N_WEBHOOK_URL`, **best-effort** — nunca quebra o fluxo principal). Dispara em download e no
   consentimento de acesso remoto (F2). Sem `N8N_WEBHOOK_URL`, cai em silêncio/fallback.

**Implementado (F1/F2):** upload com metadados (`description`, `expiraMinutos`, `maxDownloads`),
link com expiração/revogação, `WebhookPublisher` (`N8N_WEBHOOK_URL`), endpoint público de
consentimento de acesso remoto. **Pendente:** storage externo (S3/MinIO) no lugar do disco local.

## 10. Visão n8n / Roadmap — PARCIALMENTE IMPLEMENTADO

> **Já saíram do papel (F1/F2):** consentimento de acesso remoto (item **A** — hoje **RustDesk**
> com senha temporária e ferramenta `.bat` de 1 clique, acesso não atendido), upload reverso/coleta
> e links temporários (item **B**), revogação/expiração/auditoria (item **E**).
> O que segue ainda é **ideia/plano**.

> **Plano de execução detalhado em `docs/plano-n8n.md`** (fases 0–4, entregáveis e critérios de pronto).
> Para estudar n8n com a IA como instrutora, use o comando `/aprender-n8n` (`.opencode/commands/aprender-n8n.md`).

Objetivo: **reduzir o tempo de atendimento técnico ao cliente**. Ideias priorizadas:

### A. Link de liberação de acesso remoto (RustDesk) — ideia central do autor ✅ (F2)
1. Técnico abre um atendimento (form n8n / bot / ticket) → n8n gera um **link único e expirável**
   do Nump.
2. Cliente recebe o link (WhatsApp/Telegram/e-mail) e em **1 clique** vê as instruções e
   **autoriza o acesso remoto** (endpoint Nump registra consentimento: timestamp, IP, user-agent).
3. Após o aceite, a página do cliente mostra a senha temporária e o botão **"Baixar ferramenta de
   acesso (Windows)"** → `.bat` gerado pelo Nump (`/acessos/ferramenta/{token}.bat`) que baixa o
   RustDesk rápido, instala em silêncio **já com a senha de atendimento** e exibe o ID. O técnico
   conecta **direto com ID + senha**, sem o cliente aceitar a conexão (acesso não atendido).
4. Link expira automaticamente (ex.: 15 min) e/ou após uso único.

### B. Envio/roteamento de arquivos ✅ parcial (F1)
- Técnico faz upload no Nump → n8n pega o `downloadUrl` e envia ao cliente automaticamente.
- Cliente envia logs/screenshots de volta por um link de upload → n8n anexa ao ticket.
- Distribuição de **ferramentas de suporte** (instaladores, scripts, runtimes) compactadas.

### C. Atendimento e triagem (plano)
- Bot de WhatsApp/Telegram: cliente pede suporte → bot coleta nome/problema → cria ticket →
  gera link de acesso/apresentação → notifica técnico.
- Triagem por IA: classificar a mensagem do cliente e rotear para o técnico certo.
- Integração com ticketing/CRM (GLPI, Freshdesk, Google Sheets).

### D. Agendamento e follow-up (plano)
- Agendamento de atendimento (Google Calendar) → lembrete com link de acesso antes do horário.
- Notificação ao técnico quando o arquivo é **baixado** ✅ (F1: webhook de download).
- Pesquisa de satisfação pós-atendimento.

### E. Segurança e auditoria ✅ parcial (F1/F2)
- Revogação/expiração de links ✅, uso único ✅ (consentimento), senha opcional no download (plano).
- Registro de quem liberou, quando e de qual IP ✅ (trilha de auditoria do consentimento).

### Próximos passos técnicos sugeridos
1. ~~Endpoint `POST /files/upload` aceitando metadados~~ ✅ (F1: `description`, `expiraMinutos`, `maxDownloads`).
2. ~~Colunas `expiresAt`, `maxDownloads`, `downloadCount` e `revoked` em `table_file`~~ ✅ (F1).
3. ~~`WebhookPublisher` configurável por env (`N8N_WEBHOOK_URL`)~~ ✅ (best-effort).
4. Storage plugável (S3/MinIO) para uploads — **pendente** (disco local não sobrevive ao Render).
5. ~~Endpoint público de consentimento de acesso remoto~~ ✅ (F2: `/acessos/{token}/consentir`).
6. ~~Ferramenta de acesso remoto para o cliente baixar com 1 clique~~ ✅ (F2: `/acessos/ferramenta/{token}.bat`, RustDesk + senha temporária).
7. Triagem/roteamento por IA e bot de WhatsApp/Telegram — ainda plano.

## 11. Regras para a IA

- Não commitar segredos nem `.env`.
- Antes de mexer em auth/upload, ler `SecurityConfig`, `JwtService` e `FileService`.
- Respeitar a formatação de código da seção 7.
- Marcar claramente o que é **plano** vs **implementado** (F1/F2 já existem; o resto da seção 10 é plano).
- Rodar `./mvnw test` após mudanças e confirmar que o build passa.
