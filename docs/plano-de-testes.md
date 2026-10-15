# Plano de testes — Nump (F1 Transferência + F2 Acesso remoto)

> Documento de trabalho. Complementa `docs/plano-n8n.md` e a seção 8 do `AGENTS.md`.
> Objetivo: validar as funcionalidades **1 (transferência de arquivos)** e
> **2 (conexão com a máquina / consentimento AnyDesk)** de ponta a ponta.
>
> Marque `[x]` conforme executar. A IA deve atualizar este arquivo a cada avanço.

---

## 0. Pré-requisitos e ambiente

```bash
# subir localmente (H2 em memória; obrigatório o JWT_SECRET)
JWT_SECRET=dev-secret-com-no-minimo-32-bytes ./mvnw spring-boot:run
```

- App em `http://localhost:8080`. Health: `GET /actuator/health`.
- **n8n opcional**: para testar webhooks, aponte `N8N_WEBHOOK_URL` para um receptor
  (ver Bloco 6). Sem a variável, os webhooks ficam desativados e o fluxo normal
  deve continuar funcionando (teste 0.3).
- **Testar expiração sem esperar**: suba a app com valores curtos:
  ```bash
  JWT_SECRET=... APP_LINK_EXPIRACAO_MINUTOS=1 APP_ACESSO_EXPIRACAO_MINUTOS=1 \
    ./mvnw spring-boot:run
  ```
  (ou edite `expiresAt` direto no H2 Console — `spring.h2.console.enabled=true`)

### 0.1 Smoke: app sobe
- [ ] `GET /actuator/health` → `200` com `{"status":"UP"}`.

### 0.2 Criar usuário e logar
```bash
curl -s -X POST localhost:8080/users/register -H 'Content-Type: application/json' \
  -d '{"name":"Tecnico","email":"tec@nump.dev","password":"senha123"}'
# → 200 e retorna { token, user }  — guardar o token
```
- [ ] Register devolve `token`.
- [ ] Login (`POST /users/login`) devolve `token`.
- [ ] Register com email duplicado → erro 400.

> Nos comandos abaixo, `TOKEN` = JWT do técnico. Exporte com:
> `export TOKEN="<cole o token>"`

### 0.3 App sem `N8N_WEBHOOK_URL` não quebra
- [ ] Upload e download funcionam normalmente com a variável ausente/vazia.

---

## 1. F1 — Upload e link temporário

### 1.1 Upload com padrão (5 min)
```bash
echo "teste" > /tmp/a.txt
curl -s -X POST localhost:8080/files/upload -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/a.txt"
```
- [ ] `200`; JSON tem `token`, `downloadUrl`, `expiresAt` (~5 min à frente),
      `downloadCount: 0`, `revoked: false`, `origem: "UPLOAD"`.

### 1.2 Upload com expiração customizada
```bash
curl -s -X POST localhost:8080/files/upload -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/a.txt" -F "expiraMinutos=60"
```
- [ ] `expiresAt` ~60 min à frente (override do padrão).

### 1.3 Upload com `maxDownloads`
```bash
curl -s -X POST localhost:8080/files/upload -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/a.txt" -F "maxDownloads=2"
```
- [ ] `maxDownloads: 2` no retorno.

### 1.4 Upload sem token (não autenticado)
```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8080/files/upload -F "file=@/tmp/a.txt"
```
- [ ] `401` ou `403` (não `200`).

### 1.5 Listagem filtra por dono
- [ ] `GET /files` só retorna os arquivos do próprio usuário.

---

## 2. F1 — Download público, limite e revogação

> Use o `token` e o `id` do arquivo criado no 1.x.

### 2.1 Download ok + log
```bash
TOKEN_ARQ="<token do arquivo>"
curl -s -o /tmp/baixado.zip -w "%{http_code}\n" localhost:8080/files/r/$TOKEN_ARQ
```
- [ ] `200` e o `.zip` baixa (conteúdo íntegro).
- [ ] `GET /files/{id}/downloads` → 1 registro com `ip`, `userAgent`, `downloadedAt`.

### 2.2 Contador incrementa
- [ ] Após cada download, `downloadCount` sobe em `GET /files/{id}`.

### 2.3 Limite de downloads → 410
```bash
# com maxDownloads=2: baixar 3 vezes
for i in 1 2 3; do curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/files/r/$TOKEN_ARQ; done
```
- [ ] 1º e 2º → `200`; 3º → `410` com mensagem de limite.

### 2.4 Revogar → 410; reativar → volta a funcionar
```bash
curl -s -X POST localhost:8080/files/$ID/revogar -H "Authorization: Bearer $TOKEN"
curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/files/r/$TOKEN_ARQ   # 410
curl -s -X POST localhost:8080/files/$ID/reativar -H "Authorization: Bearer $TOKEN"
curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/files/r/$TOKEN_ARQ   # 200
```
- [ ] Revogado → `410`. Reativado → `200`. Arquivo **não** é apagado.

### 2.5 Expiração → 410
- [ ] Com link de 1 min, aguardar e baixar → `410` "expirou".

### 2.6 Token inexistente → 404
```bash
curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/files/r/naoexiste
```
- [ ] `404`.

### 2.7 Revogar arquivo de outro usuário → 404
- [ ] Usuário B revogando arquivo de A → `404` (sem vazar existência).

---

## 3. F1 — Coleta (upload reverso do cliente)

### 3.1 Técnico cria link de coleta
```bash
curl -s -X POST localhost:8080/files/coletas -H "Authorization: Bearer $TOKEN" \
  -d "expiraMinutos=120&maxUploads=1&descricao=log da impressora"
```
- [ ] `200`; tem `token`, `coletaUrl`, `uploadCount: 0`, `active: true`.

### 3.2 Página pública responde
```bash
curl -s -o /dev/null -w "%{http_code} -> %{redirect_url}\n" localhost:8080/files/c/$TOKEN_COLETA
```
- [ ] `302` apontando para `/coleta.html?token=...`.
- [ ] `GET /coleta.html` → `200` (página carrega sem login).

### 3.3 Cliente envia sem login
```bash
echo "log do cliente" > /tmp/log.txt
curl -s -X POST localhost:8080/files/c/$TOKEN_COLETA -F "file=@/tmp/log.txt"
```
- [ ] `200`; arquivo criado com `ownerEmail` = **do técnico**, `origem: "COLETA"`,
      `expiresAt: null` (fica guardado).

### 3.4 Aparece na listagem do técnico
- [ ] `GET /files` do técnico inclui o arquivo recebido.

### 3.5 Limite de envios → 410
- [ ] Com `maxUploads=1`, segundo envio → `410`.

### 3.6 Coleta expirada/inválida
- [ ] Token inexistente → `400`. Coleta expirada → `410`.

### 3.7 Técnico remove coleta
```bash
curl -s -X DELETE localhost:8080/files/coletas/$ID_COLETA -H "Authorization: Bearer $TOKEN"
```
- [ ] `204`; envio seguinte naquele link → `410`.

---

## 4. F2 — Acesso remoto (consentimento AnyDesk)

### 4.1 Técnico cria sessão
```bash
curl -s -X POST "localhost:8080/acessos?clienteEmail=cliente@email.com" \
  -H "Authorization: Bearer $TOKEN"
```
- [ ] `200`; tem `token`, `consentUrl`, `status: "PENDENTE"`, `expiresAt` (~15 min).

### 4.2 Página de consentimento é pública
```bash
curl -s -o /dev/null -w "%{http_code} -> %{redirect_url}\n" localhost:8080/acessos/$TOKEN_ACESSO
```
- [ ] `302` → `/consentimento.html?token=...`; a página abre sem login.

### 4.3 Consentir (fluxo feliz, sem n8n)
```bash
curl -s -X POST localhost:8080/acessos/$TOKEN_ACESSO/consentir \
  -H 'Content-Type: application/json' -d '{"clienteEmail":"cliente@email.com"}'
```
- [ ] `200` com `autorizado: true`, `consentAt`.
- [ ] **Sem** chave `n8n` (webhook desativado).

### 4.4 Trilha de auditoria gravada
- [ ] `GET /acessos` (técnico) mostra `status: "CONSENTIDO"`, `consentAt`,
      `consentIp` e `consentUserAgent` preenchidos.

### 4.5 Uso único → 410
```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8080/acessos/$TOKEN_ACESSO/consentir \
  -H 'Content-Type: application/json' -d '{}'
```
- [ ] Segunda tentativa → `410` "já foi utilizado".

### 4.6 Cancelamento
```bash
# criar outra sessão e cancelar
curl -s -X DELETE localhost:8080/acessos/$ID_ACESSO -H "Authorization: Bearer $TOKEN"
```
- [ ] Pendente cancelada → consentir depois → `410` "cancelado".
- [ ] `DELETE` de sessão já consentida → **não** altera o status (permanece CONSENTIDO).

### 4.7 Expiração → 410
- [ ] Com `APP_ACESSO_EXPIRACAO_MINUTOS=1`, aguardar e consentir → `410`.
      O status deve virar `EXPIRADO`.

### 4.8 Token inválido
- [ ] `POST /acessos/xyz/consentir` → `400` "inválido".

### 4.9 Rotas autenticadas protegidas
- [ ] `GET /acessos` sem token → `401/403`.
- [ ] `POST /acessos` sem token → `401/403`.
- [ ] `DELETE /acessos/{id}` sem token → `401/403`.
- [ ] `POST /acessos/{token}/consentir` **com** token de sessão válido → `200`
      (é público de propósito).

---

## 5. Auditoria de IP (proxy reverso)

```bash
curl -s -X POST localhost:8080/acessos/$TOKEN_ACESSO/consentir \
  -H 'Content-Type: application/json' -H 'X-Forwarded-For: 203.0.113.9' -d '{}'
```
- [ ] `consentIp` registrado como `203.0.113.9` (e não o IP local).
- [ ] Mesma lógica vale para o log de download (`GET /files/{id}/downloads`).

---

## 6. Webhooks (n8n)

Suba um receptor rápido para capturar os eventos e simular a resposta do n8n:

```bash
# receptor Python que loga o POST e responde com dados do AnyDesk
python3 - <<'EOF'
from http.server import BaseHTTPRequestHandler, HTTPServer
class H(BaseHTTPRequestHandler):
    def do_POST(self):
        n = int(self.headers.get('Content-Length', 0)); body = self.rfile.read(n)
        print("RECEBIDO:", body.decode(), flush=True)
        resp = b'{"anydeskId":"123456789","anydeskSenha":"abc123"}'
        self.send_response(200)
        self.send_header('Content-Type','application/json')
        self.send_header('Content-Length',str(len(resp))); self.end_headers()
        self.wfile.write(resp)
HTTPServer(('localhost', 9999), H).serve_forever()
EOF
```

Suba a app com `N8N_WEBHOOK_URL=http://localhost:9999`.

- [ ] `arquivo_enviado` dispara no upload.
- [ ] `arquivo_baixado` dispara no download.
- [ ] `coleta_recebida` dispara no upload reverso.
- [ ] `acesso_consentido` dispara no consentimento **e** a página `consentimento.html`
      exibe o ID/senha do AnyDesk com botão "Copiar dados".
- [ ] Payload tem `evento` e `emitidoEm`.
- [ ] **Receptor desligado**: upload/download/consentimento continuam funcionando
      (webhook é best-effort, só loga aviso).

---

## 7. Regressão do frontend

- [ ] `index.html`: login + upload via drag-and-drop ainda funciona.
- [ ] Card de arquivo mostra link e botão baixar.
- [ ] `coleta.html` e `consentimento.html` carregam sem erro de console.

---

## 8. Casos de borda

- [ ] Upload de arquivo 0 bytes.
- [ ] Upload estourando o limite (500 MB) → erro claro.
- [ ] `expiraMinutos=0` no upload → comportamento definido (sem expiração).
- [ ] Token de coleta reutilizado por 2 clientes simultâneos.
- [ ] Dois consentimentos simultâneos no mesmo link → só um vence (uso único).

---

## 9. Critérios de aceite (resumo)

1. Link de download expira, conta downloads, revoga e reativa — sempre respondendo
   `410` (não `404`/`500`) quando indisponível.
2. Upload reverso funciona sem login e o arquivo pertence ao técnico.
3. Consentimento de acesso remoto é **uso único**, registra IP/user-agent/hora e
   bloqueia expirado/cancelado.
4. Nenhum webhook afeta o fluxo principal (tudo best-effort).
5. Rotas privadas exigem JWT; rotas públicas (`/files/r`, `/files/c`,
   `/acessos/{token}/consentir`) funcionam sem.
