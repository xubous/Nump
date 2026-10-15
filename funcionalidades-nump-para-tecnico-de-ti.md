# Funcionalidades do Nump para o técnico de TI

> Documento gerado em 13/10/2026 (refatorado — catálogo de funcionalidades independentes).
> Fontes: `AGENTS.md` (seção 10 — Visão n8n / Roadmap), `docs/plano-n8n.md` (plano de fases) e
> `docs/pitch-vendas-suporte.md`.
>
> **Importante:** nada disso está implementado ainda — é o roadmap/visão do projeto.

---

## Como ler este documento

- Cada funcionalidade **funciona sozinha** — dá pra usar só a 1 sem usar as outras.
- Não existe um fluxo único obrigatório: o técnico combina as que quiser no dia a dia.
- Dentro de cada funcionalidade há **sub-funcionalidades** (recursos internos).
- Cada item traz o **repetitivo que elimina** — a tarefa manual que ele corta.

---

## 1. Transferência de arquivos

Enviar e receber arquivos com o cliente por link curto, sem e-mail e sem WhatsApp pesado.

- **Link curto** — upload → compactação automática (`.zip`) → token de 8 caracteres.
- **Envio rápido** — o link vai por WhatsApp/Telegram/e-mail (disparado pelo n8n).
- **Download controlado** — link com validade (ex.: 24 h), uso único, limite de downloads e
  senha opcional.
- **Log do que foi mexido** — histórico de quem enviou, quem baixou, quando e de qual IP.
- **Upload reverso** — link para o cliente mandar logs/prints/diagnósticos de volta.
- **Confirmação de recebimento** — aviso ao técnico quando o cliente baixa o arquivo.
- **Revogação** — desativar um link a qualquer momento, sem apagar o arquivo.

*Repetitivo que elimina:* "manda por e-mail", "você recebeu?", "manda de novo, não baixou".

## 2. Conexão com a máquina mais fácil (acesso remoto)

Abrir sessão remota com o cliente sem fricção e com registro de autorização.

- **Link de liberação em 1 clique** — cliente abre a página e autoriza o acesso remoto.
- **Consentimento registrado** — timestamp, IP e user-agent de quem autorizou (trilha de
  auditoria).
- **Link temporário** — expira em minutos (ex.: 15 min) e/ou é de uso único.
- **Entrega dos dados da sessão** — envia ao técnico o ID/senha do AnyDesk ou abre direto o
  protocolo `anydesk:<id>`.
- **Aviso ao técnico** — n8n notifica "cliente autorizou" assim que ele consentir.

*Repetitivo que elimina:* "baixa o AnyDesk, me manda o código, espera autorizar, o link caiu".

## 3. Organizar arquivos

Manter instaladores, ferramentas e logs sob controle, sem bagunça no desktop.

- **Pastas/categorias** — separar por cliente, chamado ou tipo (instalador, log, script).
- **Nome padronizado** — renomeação automática (`cliente_data_versão.zip`).
- **Histórico de versões** — manter versões anteriores do mesmo arquivo.
- **Busca** — encontrar por nome, cliente ou data.
- **Biblioteca de ferramentas** — repositório reutilizável de instaladores, scripts e `.exe`
  portáteis.
- **Limpeza automática** — expiração/exclusão de arquivos antigos e lixeira com recuperação.

*Repetitivo que elimina:* procurar o instalador certo em 3 pastas diferentes e não achar.

## 4. Notificações e alertas

Avisos independentes que eliminam o "você recebeu?" e o "o link caiu?".

- Cliente **baixou** o arquivo (confirmação de recebimento).
- **Novo arquivo recebido** do cliente (upload reverso chegou).
- Link está **para expirar** / expirou.
- Consentimento de acesso remoto **autorizado**.

*Repetitivo que elimina:* ficar perguntando se chegou, se baixou, se funcionou.

## 5. Apoio técnico com IA (base de conhecimento)

Consultar solução durante o atendimento — só consulta, quem decide é o técnico.

- **Passo a passo com fonte** — sugestão de diagnóstico/solução citando manuais e tickets
  resolvidos (RAG).
- **Busca em tickets antigos** — "já resolvi isso? como?".
- **Resumo do problema** — a partir do relato do cliente, gera um resumo estruturado para o
  chamado.

*Repetitivo que elimina:* reler o mesmo manual e refazer a mesma pesquisa toda semana.

## 6. Agendamento e follow-up

Rotina em volta do atendimento, sem cobrança manual.

- **Agendamento + lembrete** com link de acesso já pronto antes do horário.
- **Pesquisa de satisfação** pós-atendimento automática.
- **Reabertura fácil** — retomar o link/atendimento anterior sem refazer tudo.

*Repetitivo que elimina:* lembrar de cobrar retorno e reenviar link na mão.

## 7. Diagnóstico remoto automático (coleta em 1 clique)

O cliente roda um scriptinhu gerado pelo técnico e o relatório completo volta pronto.

- **Script gerado na hora** — `.bat`/`.ps1` que coleta SO, RAM, disco, serviços, IPs, erros
  recentes e logs.
- **Perfis de coleta** — rede, desempenho, impressora, drivers (escolhe o que quer coletar).
- **Execução sem admin** — roda com permissão de usuário comum, sem pedir elevação.
- **Relatório formatado** — volta pelo upload reverso já legível (não um dump cru).
- **Comparação** — diff com o diagnóstico anterior do mesmo cliente (o que mudou?).

*Repetitivo que elimina:* "manda print do gerenciador de dispositivos", "me passa o IP",
"abre o evento de erros e me manda".

## 8. Scripts de correção prontos (biblioteca de comandos)

Comandos de correção frequentes prontos para enviar ao cliente executar.

- **Biblioteca por sintoma** — limpar DNS, renovar IP, reiniciar serviço, checar porta,
  desativar serviço problemático.
- **Variáveis** — o script pede "qual serviço?" em vez de o técnico editar na mão.
- **Preview do que vai rodar** — o cliente vê o comando antes de executar (transparência).
- **Log de execução** — o resultado volta pelo upload reverso e entra no histórico do caso.

*Repetitivo que elimina:* digitar o mesmo PowerShell/CMD pela centésima vez e explicar onde
colar.

## 9. Mensagens e tutoriais prontos

Textos e explicações que o técnico já escreveu N vezes, guardados para reuso.

- **Templates de resposta** — "reinicie o roteador", "atualize o driver", instruções de
  instalação — com campos para personalizar (nome, modelo, versão).
- **Tutoriais com prints** — passo a passo visual que o técnico envia pronto ao cliente.
- **Envio em 1 clique** — escolher o template → personaliza → envia (WhatsApp/e-mail).
- **Evolução** — se o técnico escreveu uma resposta nova 2 vezes, vira template
  automaticamente (sugestão).

*Repetitivo que elimina:* reexplicar pela enésima vez como limpar cache ou instalar o driver.

## 10. Formulário de abertura do atendimento

O cliente preenche antes de virar chamado — e chega tudo estruturado.

- **Campos configuráveis** — máquina (marca/modelo/SO), sintoma, quando começou, já tentou
  algo.
- **Anexos desde o início** — cliente já manda print/log no próprio formulário.
- **Sem cadastro obrigatório** — link direto, sem criar conta.
- **Resumo automático** — a IA gera o resumo do formulário no formato do chamado.

*Repetitivo que elimina:* a sequência de 5 perguntas de sempre no primeiro contato.

## 11. Histórico do cliente (linha do tempo)

Tudo que já aconteceu com aquele cliente/máquina num lugar só.

- **Timeline** — atendimentos, arquivos enviados, consentimentos, downloads, em ordem.
- **Máquinas do cliente** — cada equipamento com SO, specs (do diagnóstico 7) e problemas
  recorrentes.
- **Reabertura de caso** — clonar um atendimento anterior como novo caso.
- **Contexto para transferência** — passar o caso para outro técnico com tudo junto.

*Repetitivo que elimina:* procurar no WhatsApp "o que a gente fez na última vez nessa máquina?".

## 12. Protocolo, tempo e relatório do atendimento

Medir e documentar sem planilha na mão.

- **Protocolo automático** — número de chamado gerado e rastreável.
- **Cronômetro** — tempo gasto por atendimento (pausa automática quando esperando o cliente).
- **Relatório final gerado** — "o que foi feito, o que mudou, o que recomendo", em PDF,
  montado do registro do atendimento.
- **Indicadores simples** — tempo médio por tipo de problema, quantos atendimentos por
  semana.

*Repetitivo que elimina:* escrever relatório do zero e controlar horas em planilha.

## 13. FAQ / autoatendimento público

Página de soluções comuns que o cliente consulta **antes** de abrir chamado.

- **Base pública** — tutoriais e correções comuns publicados com link curto (usa a mesma
  base do item 5/9).
- **Redução de chamados** — o problema simples vira artigo, não vira atendimento.
- **"Tentar antes"** — o formulário do item 10 sugere artigos antes de enviar o chamado.

*Repetitivo que elimina:* o chamado de "esqueci a senha do Wi-Fi" que nasce toda semana.

---

## O que falta no backend (e para qual funcionalidade)

| Necessidade | Status | Usada em |
|---|---|---|
| `WebhookPublisher` (Nump avisa o n8n) | ❌ não existe | 4 |
| Upload com metadados (recipient, description, expiresAt, maxDownloads) | ❌ não existe | 1, 3, 7 |
| Expiração/uso único/revogação de token | ❌ não existe | 1, 2 |
| Endpoint público de consentimento AnyDesk | ❌ não existe | 2 |
| Storage externo (S3/MinIO) — disco local não escala | ❌ não existe | 1, 3, 7, 9 |
| Busca/histórico de arquivos (metadados, versões) | ❌ não existe | 3, 11 |
| Cadastro de clientes/máquinas (mínimo) | ❌ não existe | 7, 11, 12 |
| Formulários públicos configuráveis | ❌ não existe | 10 |

> **Nota:** 7 e 8 não exigem execução remota no servidor — o script é gerado, o cliente roda
> na máquina dele e o resultado volta pelo **upload reverso** (funcionalidade 1). Mesmo modelo
> já previsto no plano.

---

**Conclusão:** dá pra começar por qualquer funcionalidade isoladamente — as mais valiosas são
**1 (transferência)** e **2 (acesso remoto)**, que dependem só das fases 2–4 de
`docs/plano-n8n.md`. As demais (7–13) são ganhos de produtividade que crescem em cima da mesma
base.
