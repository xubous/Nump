# 🧪 Roteiro de testes manuais — Nump (F1 + F2)

> Siga **de cima pra baixo**. Cada passo diz **o que digitar** (copie e cole) e
> **o que você deve ver**. Ao final, você terá visto as 3 funcionalidades
> funcionando na ponta do dedo: **baixar arquivo**, **cliente enviar arquivo de volta**
> e **autorizar acesso remoto (RustDesk)**.
>
> Requisitos: `curl` e `jq` instalados, e o JDK 21 no PATH.

---

## ▶ Passo 0 — Subir o sistema

Abra **um terminal** e cole:

```bash
cd ~/git/xub/projects/Nump
export JAVA_HOME=~/.local/jdk/jdk-21.0.12.1+1
export PATH="$JAVA_HOME/bin:$PATH"
JWT_SECRET=dev-secret-com-no-minimo-32-bytes ./mvnw spring-boot:run
```

**Você deve ver** (depois de ~5s):
```
Tomcat started on port 8081 (http)
Started NumpApplication in ...
```

> ⚠️ Deixe esse terminal **aberto e rodando**. Pra parar depois: `Ctrl + C`.

> 💡 **Dica:** pra não esperar 5 minutos nos testes de expiração, pode subir com tempos curtos:
> ```bash
> JWT_SECRET=... APP_LINK_EXPIRACAO_MINUTOS=1 APP_ACESSO_EXPIRACAO_MINUTOS=1 ./mvnw spring-boot:run
> ```

---

## ▶ Passo 1 — Criar conta e ver o painel

1. Abra o navegador em **http://localhost:8081**
2. Vai pra **`/register.html`** e crie uma conta (nome, email, senha).
3. Faça **login**.

**Você deve ver:** o painel do técnico (`index.html`) com a área de **arrastar arquivos**.
Sem erro nenhum no console (aperte `F12` → aba *Console* e veja se está limpo).

> 📌 **Anota o email e a senha que você criou** — vamos usar no terminal no Passo 3.

---

## ▶ Passo 2 — 💾 Enviar arquivo e baixar (F1)

1. **Arraste um arquivo** (qualquer um) pra área de upload no painel.
2. Clique em **"Enviar todos"**.
   - **Você deve ver:** barra de progresso subindo e depois um **card** com o arquivo.
3. No card, clique em **"Copiar"**.
   - **Você deve ver:** o botão vira **"Copiar!"** (verde) e o link vai pro seu `Ctrl+V`.
4. Cole o link numa **aba anônima** (`Ctrl+Shift+N`) e abra.
   - **Você deve ver:** o download começa e baixa um **`.zip`**. Abra o zip — seu arquivo está lá. ✅
5. De volta ao painel, clique **"Baixar"** num card.
   - **Você deve ver:** baixa o `.zip` direto.

> 🎉 **Acabou de ver a F1 funcionando:** upload → link curto temporário → download.

---

## ▶ Passo 3 — 📥 Cliente devolve arquivo (coleta / upload reverso)

Aqui o **cliente manda log/screenshot de volta** pra você, **sem ter login**.

### 3.1 Faça login no terminal e pegue o TOKEN

Cole (troque `SEU_EMAIL` e `SUA_SENHA` pelos do Passo 1):

```bash
export TOKEN=$(curl -s -X POST http://localhost:8081/users/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"SEU_EMAIL","password":"SUA_SENHA"}' | jq -r .token)
echo "$TOKEN"
```

**Você deve ver:** uma sequência longa de letras (o JWT). Se vier `null`, confira email/senha.

### 3.2 Crie o link de coleta e copie a URL

```bash
curl -s -X POST http://localhost:8081/files/coletas \
  -H "Authorization: Bearer $TOKEN" | jq -r .coletaUrl
```

**Você deve ver:** uma URL tipo `http://localhost:8081/files/c/abc12345`.
**Copie essa URL.**

### 3.3 Abra como se fosse o cliente (aba anônima)

Cole a URL numa **aba anônima**.

**Você deve ver:**
- A página **`Enviar arquivo`** carrega **sem pedir login** ✅
- Um aviso e a área de **arrastar arquivo**

### 3.4 Envie um arquivo e confira no seu painel

1. Arraste um arquivo (ex.: um `.txt` ou print) e clique **Enviar**.
   - **Você deve ver:** barra de progresso → **"Arquivo enviado com sucesso!"**
2. Volte no **seu painel** (aba normal, recarregue).
   - **Você deve ver:** o arquivo enviado pelo "cliente" aparece na **sua lista** ✅

> 🎉 **Acabou de ver a F1 (parte reversa) funcionando:** cliente sem login manda arquivo
> de volta e cai pro técnico.

---

## ▶ Passo 4 — 🖥️ Autorizar acesso remoto / RustDesk (F2)

Aqui o **cliente autoriza o acesso**, recebe a senha temporária e **baixa a ferramenta RustDesk**
com 1 clique — o script instala com a senha já aplicada, então o técnico conecta **sem o cliente
aceitar a conexão** (acesso não atendido).

### 4.1 Crie a sessão e copie a URL

```bash
curl -s -X POST http://localhost:8081/acessos \
  -H "Authorization: Bearer $TOKEN" | jq -r .consentUrl
```

**Você deve ver:** uma URL tipo `http://localhost:8081/acesso/xyz9876`.
**Copie essa URL.**

### 4.2 Abra como se fosse o cliente (aba anônima)

Cole numa **aba anônima**.

**Você deve ver:**
- A página **`Autorizar acesso remoto`** sem pedir login ✅
- Caixa de aviso com **ícone de escudo** dizendo que fica **registrado (data, hora e IP)**
- Um campo de email (opcional) e o botão **"Autorizo o acesso remoto"**

### 4.3 Dê o consentimento

1. (Opcional) Preencha o email e clique **"Autorizo o acesso remoto"**.
   - **Você deve ver:** vira **"Registrando autorização…"**
2. O resultado aparece:
   - **Sem n8n:** painel verde **"Acesso autorizado"** com a **senha RustDesk gerada pelo Nump**
   - **Com n8n (Passo 5):** mostra **ID/senha do RustDesk** + botão **"Copiar dados"** ✅
3. Sempre abaixo aparece o botão verde **"Baixar ferramenta de acesso (Windows)"**
   + as instruções (executar → clicar em "Sim" no UAC → compartilhar o ID) ✅

### 4.4 Teste o uso único

Recarregue a **mesma URL** e tente consentir de novo.

**Você deve ver:** recusa com **"Este link já foi utilizado"** ✅

### 4.5 Confira no painel do técnico

Volte no seu painel / lista de sessões (`GET /acessos`).

**Você deve ver:** a sessão como **CONSENTIDO**, com **data/hora e IP** gravados ✅

### 4.6 Baixe a ferramenta RustDesk (`.bat`)

Clique no botão **"Baixar ferramenta de acesso (Windows)"** (ou abra
`http://localhost:8081/acessos/ferramenta/<TOKEN>.bat`).

**Você deve ver:** baixa o arquivo **`Suporte Nump.bat`**; ao executar (num Windows), ele **busca a
senha de atendimento no servidor** (token de uso único — a senha **não** vai em texto claro no
`.bat`), baixa o RustDesk **rápido** (barra de progresso do `curl`; se o `curl` faltar, usa BITS),
instala em silêncio já com a **senha temporária** (clique **Sim** no UAC uma vez), abre o RustDesk e
mostra o **ID**. O técnico conecta com esse ID e a senha — **sem o cliente aceitar nada** ✅

**No fim do atendimento**, o `.bat` mostra um menu e espera a escolha do cliente:

| Tecla | O que acontece |
|---|---|
| **`1`** | **Remove o acesso**: apaga a senha, para o serviço e desinstala o RustDesk — o técnico **não conecta mais** (pode pedir UAC) |
| **`2`** | **Mantém o acesso**: RustDesk fica instalado com a **senha de atendimento** (acesso permanente — a senha não expira) |

> Teste também **antes do consentimento** (token ainda PENDENTE): deve recusar com
> **"Autorize o acesso antes de baixar a ferramenta"** ✅
>
> Segurança (endurecido em 2026-10-20): o **link de acesso remoto tem 24 chars** (antes 8) e o
> endpoint `GET /acessos/ferramenta/{fToken}/senha` é de **uso único** — a 2ª chamada devolve
> **410 "Esta ferramenta já foi usada"** ✅

### 4.7 Encerre o acesso remoto depois (opcional, `.bat` de encerramento)

Se o cliente escolheu **2** (ou fechou o script) e mais tarde quiser remover o acesso, na mesma
página o botão **"Encerrar atendimento (remover acesso remoto)"** (ou
`http://localhost:8081/acessos/<TOKEN>/encerrar.bat`) faz o mesmo do `1`: limpa a senha, para o
serviço e mata o RustDesk ✅

> Teste também em token PENDENTE (não consentido): deve recusar com
> **"Nenhum atendimento em andamento para este link"** ✅

> 🎉 **Acabou de ver a F2 funcionando:** cliente autoriza → auditoria registrada →
> RustDesk instalado com senha temporária → técnico conecta direto (acesso não atendido) →
> cliente escolhe no fim (remover ou manter).

---

## ▶ Passo 5 — 🔌 Ligar no n8n real (opcional, mas recomendado)

Aqui você valida a integração **de verdade** (o n8n já roda em `localhost:5678`).

| # | Passo | Esperado |
|---|-------|----------|
| 5.1 | No n8n, crie um workflow com nó **Webhook** e copie a URL (ex.: `http://localhost:5678/webhook/abc`) | URL copiada |
| 5.2 | Ligue um nó **Respond to Webhook** devolvendo `{"rustdeskId":"999","rustdeskServer":"meuservidor:21116"}` e **ative** o workflow (toggle ON) | Workflow salvo e ativo |
| 5.3 | Pare a app (`Ctrl+C`) e suba com a URL do webhook:<br>`JWT_SECRET=... N8N_WEBHOOK_URL=<URL_DO_WEBHOOK> ./mvnw spring-boot:run` | App no ar |
| 5.4 | **Envie** um arquivo (Passo 2) | O Webhook dispara com `evento: arquivo_enviado` |
| 5.5 | **Baixe** um arquivo | Dispara `arquivo_baixado` |
| 5.6 | Crie coleta e **envie** pelo link | Dispara `coleta_recebida` |
| 5.7 | Crie sessão e **consentir** (Passo 4) | Dispara `acesso_consentido` **e** a página mostra o `anydeskId: 999` / `anydeskSenha: xyz` do 5.2 ✅ |
| 5.8 | **Desligue** o workflow e repita um upload | Upload **ainda funciona** — n8n fora do ar não quebra o Nump ✅ |
| 5.9 | Olhe o painel de **Execuções** do n8n | Vê as execuções com o payload certo |

---

## ▶ Passo 6 — 🛡️ Checagens rápidas de segurança

| # | Faça | Esperado |
|---|------|----------|
| 6.1 | Abra `http://localhost:8081/files` numa aba anônima | **Bloqueado** (não lista arquivos) |
| 6.2 | Abra `http://localhost:8081/acessos` anônimo | **Bloqueado** |
| 6.3 | Abra `/coleta.html` e `/consentimento.html` anônimo | **Passam** (são públicas) |
| 6.4 | Crie uma 2ª conta; por ela tente revogar/excluir arquivo da 1ª conta (pelo id na URL) | Não dá (404 / não aparece na lista) |
| 6.5 | Suba com `APP_LINK_EXPIRACAO_MINUTOS=1`, espere 1 min e baixe o link | Recusa: **"Este link expirou"** |
| 6.6 | Revogue um link (botão/painel) e tente baixar → depois **reative** | Bloqueia → volta a baixar |
| 6.7 | Veja o histórico: `GET /files/{id}/downloads` (com `$TOKEN`) | Mostra **IP, data/hora e navegador** de cada download |

Ver histórico de downloads (copie e troque `{ID}`):

```bash
curl -s "http://localhost:8081/files/{ID}/downloads" \
  -H "Authorization: Bearer $TOKEN" | jq
```

---

## ✅ Checklist final

- [ ] Passo 2: enviei → copiei link → baixei o `.zip`
- [ ] Passo 3: cliente (anônimo) me devolveu arquivo → apareceu no meu painel
- [ ] Passo 4: cliente autorizou → auditoria registrada → uso único bloqueou 2ª tentativa
- [ ] Passo 5 (se fez): n8n disparou os 4 eventos e a página mostrou o ID do RustDesk
- [ ] Passo 6: rotas privadas bloqueadas; expiração/revogação funcionando
- [ ] Console do navegador (`F12`) **limpo** em index / coleta / consentimento

---

### 🤔 Pra confirmar com você
1. Sem n8n, o texto **"técnico avisado, aguarde"** na página de consentimento está bom?
2. Quer **botão no painel** pra criar coleta/sessão, ou `curl`/API basta por enquanto?
3. O histórico de downloads deve aparecer **na UI** do técnico, ou o endpoint `GET /files/{id}/downloads` basta?
