# Plano de testes MANUAIS — Nump (F1 + F2)

> Para você rodar **no navegador / com o n8n real**. O que é coberto por script
> automatizado está em `docs/plano-de-testes.md` (executado: 60 casos, 0 falhas).
> Aqui ficam os testes que exigem inspeção visual, interação humana ou o n8n de verdade.

---

## A. Subir o ambiente (uma vez)

```bash
# terminal 1 — app (H2 local)
JWT_SECRET=dev-secret-com-no-minimo-32-bytes ./mvnw spring-boot:run
```

Abra `http://localhost:8080` e **crie uma conta** em `/register.html` (ou use uma já existente).

> Para testar expiração sem esperar 5 min, suba com tempos curtos:
> ```bash
> JWT_SECRET=... APP_LINK_EXPIRACAO_MINUTOS=1 APP_ACESSO_EXPIRACAO_MINUTOS=1 ./mvnw spring-boot:run
> ```

---

## B. Interface do técnico (index.html) — navegador

| # | Ação | Esperado |
|---|------|----------|
| B1 | Logar com email/senha | Entra no painel sem erro |
| B2 | **Arrastar um arquivo** pra área de upload | Fila mostra o arquivo; botão "Enviar" aparece |
| B3 | Clicar "Enviar todos" | Barra de progresso sobe; card do arquivo aparece com link |
| B4 | Clicar **"Copiar"** no card | Texto vira "Copiar!" (verde) e link vai pra área de transferência |
| B5 | Clicar **"Baixar"** | Baixa o `.zip` e abre normalmente |
| B6 | Clicar **"Excluir"** | Card some da lista |
| B7 | Recarregar a página | Lista recarrega do servidor (persiste) |
| B8 | Conferir no card | Mostra tamanho e o link de download |

---

## C. Página de coleta (coleta.html) — navegador, **sem login**

1. Técnico cria a coleta (via `POST /files/coletas` ou botão no painel, se existir).
2. **Copie o `coletaUrl`** e abra em **aba anônima** (ou outro navegador/celular).

| # | Ação | Esperado |
|---|------|----------|
| C1 | Abrir a URL numa aba anônima | Página carrega **sem pedir login** |
| C2 | Conferir visual | Título "Enviar arquivo", aviso, área de arrastar |
| C3 | Arrastar/selecionar um arquivo | Nome + tamanho aparecem; botão "Enviar" habilita |
| C4 | Enviar | Barra de progresso → "Arquivo enviado com sucesso!" |
| C5 | Somar no painel do técnico | O arquivo aparece na lista do técnico (dono = técnico) |
| C6 | Recarregar a coleta e enviar de novo | Depois do limite (`maxUploads`), recusa com mensagem de erro legível |
| C7 | Testar no **celular** (mesma rede) | Upload funciona pelo celular |

---

## D. Página de consentimento (consentimento.html) — navegador, **sem login**

1. Técnico cria a sessão (`POST /acessos`).
2. **Copie o `consentUrl`** e abra em **aba anônima**.

| # | Ação | Esperado |
|---|------|----------|
| D1 | Abrir a URL anônima | Carrega **sem login** |
| D2 | Conferir visual | Título "Autorizar acesso remoto" + caixa de aviso com ícone de escudo |
| D3 | Ler o aviso | Menciona que fica **registrado (data, hora e IP)** e é temporário |
| D4 | Preencher email (opcional) e clicar **"Autorizo o acesso remoto"** | Vira "Registrando autorização…" |
| D5 | Ver o resultado | Painel verde "Acesso autorizado" + **senha gerada pelo Nump** |
| D6 | Com n8n configurado | Mostra **ID do RustDesk** (e servidor, se houver) + botão **"Copiar dados"** |
| D7 | Clicar "Copiar dados" | Botão vira "Copiado!" |
| D8 | Tentar usar o **mesmo link de novo** | Recusa com "Este link já foi utilizado" (uso único) |
| D9 | Conferir no painel do técnico | Sessão aparece como **CONSENTIDO** com data/hora/IP |

---

## E. n8n real (não o mock) — passo a passo

> Aqui você valida a integração de verdade, com o n8n que já roda em `localhost:5678`.

| # | Passo | Esperado |
|---|-------|----------|
| E1 | No n8n: criar workflow **Webhook** (nó "Webhook"), copiar a URL (ex.: `http://localhost:5678/webhook/abc`) | URL copiada |
| E2 | (Opcional) ligar um nó **Respond to Webhook** devolvendo `{"rustdeskId":"999","rustdeskServer":"meuservidor:21116"}` | Workflow salvo e **ativo** (toggle ON) |
| E3 | Subir a app com `N8N_WEBHOOK_URL=<a URL do webhook>` | App no ar |
| E4 | Fazer **upload** de um arquivo | O nó Webhook dispara com `evento: arquivo_enviado` |
| E5 | **Baixar** o arquivo | Dispara `evento: arquivo_baixado` |
| E6 | Criar coleta e **enviar** pelo link | Dispara `coleta_recebida` |
| E7 | Criar sessão e **consentir** | Dispara `acesso_consentido`; a página mostra o ID/senha do E2 |
| E8 | **Desligar** o workflow do n8n e repetir um upload | Upload **ainda funciona** (n8n fora do ar não quebra o Nump) |
| E9 | Conferir o painel de **Execuções** do n8n | Vê as execuções com o payload correto |

---

## F. Segurança / comportamento (manual rápido)

| # | Ação | Esperado |
|---|------|----------|
| F1 | Abrir `http://localhost:8080/files` no navegador **sem login** | Bloqueado (não lista arquivos) |
| F2 | Abrir `http://localhost:8080/acessos` **sem login** | Bloqueado |
| F3 | Abrir `http://localhost:8080/coleta.html` e `/consentimento.html` sem login | **Passam** (são públicas) |
| F4 | Criar 2 contas; na conta B tentar revogar/excluir arquivo da conta A (via URL do id) | Não dá (404 / some da lista) |
| F5 | Deixar um link de download de **1 min** parado e baixar depois | Recusa com "Este link expirou" |
| F6 | Revogar um link e tentar baixar | Recusa; **reativar** → volta a baixar |
| F7 | Ver o histórico de downloads de um arquivo (painel ou `GET /files/{id}/downloads`) | Mostra IP, data/hora e navegador de cada download |

---

## G. Checagem final

- [ ] Todos os itens B–F ok
- [ ] Nenhum erro no **Console do navegador** (F12) em index/coleta/consentimento
- [ ] N8N_WEBHOOK_URL vazio → tudo funciona normalmente
- [ ] Fluxo real de ponta a ponta: técnico gera link → cliente consente → técnico recebe dados → cliente baixa envio → técnico vê log

---

### Dúvidas / comportamentos pra confirmar com o autor
1. Na página de consentimento, quando o n8n **não** responde, o texto "técnico avisado, aguarde" é suficiente?
2. O técnico precisa de um **botão no painel** pra criar coleta/sessão, ou `curl`/API basta por enquanto?
3. O log de downloads deve aparecer **na UI** do técnico ou o endpoint `GET /files/{id}/downloads` basta?
