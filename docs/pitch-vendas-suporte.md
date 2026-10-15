# Pitch — IA de triagem + Nump: atendimento de suporte mais rápido

> Em uma frase: **ticket entra, IA decide se dá pra resolver remoto, roteia sozinho para a
> pessoa certa e — durante o atendimento — Nump entrega links e arquivos sem atrito.**

---

## 1. O problema

O atendimento de manutenção/suporte tem dois vazamentos de tempo:

1. **Triagem manual** — todo ticket passa pela mão de alguém só pra descobrir "é simples ou
   complexo? resolve remoto ou presencial?".
2. **Ida e volta de arquivo** — reenviar o mesmo instalador, pedir log por e-mail, esperar
   confirmação de download.

Resultado: o técnico sênior gasta horas com o que é básico, e o básico demora mais do que deveria.

## 2. A oportunidade (nossa IA de triagem)

A ideia central: **uma IA que lê o ticket e decide o caminho antes de qualquer humano agir.**

- **Simples / resolve remoto** → cai direto no **nível 1** (equipe de base).
- **Complexo / exige presencial ou sênior** → vai para o **técnico sênior**.

A IA não decide sozinha (boas práticas de automação: **humano no circuito**). Ela classifica,
sugere a causa provável e o primeiro diagnóstico — mas a **decisão final é da pessoa** que recebe.

Base de conhecimento via **RAG**: em vez de a IA chutar da memória, ela consulta os documentos
internos (manuais, tickets resolvidos, procedimentos) e responde *com fonte*. Isso reduz invenção
e padroniza o "como resolvo da primeira vez".

## 3. A engrenagem que faz isso rodar

```text
       TICKET
         │
         ▼
   ┌─ n8n (orquestrador) ────────────────┐
   │  triagem com IA ──► simples  → n1   │
   │                  └──► complexo → sênior
   │  entrega links/arquivos via Nump     │
   │  registra resultado/auditoria        │
   └──────────────────────────────────────┘
```

- **n8n** — o fluxo visual que liga tudo (já rodando em Docker na sua máquina).
- **IA (Gemini/Claude)** — classifica o ticket e redige a primeira resposta.
- **Nump** — o "hub de arquivos do suporte": link curto + compactação automática + download público.
- **Humano no circuito** — para ação com impacto, é a pessoa que aprova.

## 4. Fluxo de ponta a ponta

1. Cliente abre ticket (form/WhatsApp/e-mail) → n8n captura.
2. **IA classifica**: simples ou complexo? remoto ou presencial? com base na base de conhecimento (RAG).
3. Roteia: **simples → você (nível 1)** · **complexo → técnico sênior**.
4. Durante o atendimento, **Nump entrega** instaladores/ferramentas/logs por **link curto**.
5. Cliente consome o link; n8n confirma o download e devolve o chamado.
6. **Registro de tudo** (classificação, quem atendeu, o que foi enviado) — aprendizagem e auditoria.

## 5. Funcionalidades

**Curto prazo (configuração, baixo esforço):**

| # | O que | Ganho |
|---|-------|-------|
| 1 | **Classificador de tickets por IA** (simples × complexo / remoto × presencial) | triagem automática |
| 2 | Upload de arquivo → **link curto** entregue no WhatsApp/e-mail | fim do "manda por e-mail" |
| 3 | **Resposta inicial padrão** do ticket com sugestão de diagnóstico | atendimento mais rápido |

**Médio prazo (pequenas melhorias no backend):**

| # | O que | Ganho |
|---|-------|-------|
| 4 | **Base de conhecimento com RAG** (consultar manuais/tickets resolvidos com citação) | resposta com fonte |
| 5 | Links **expiráveis e de uso único** + senha opcional | segurança em conteúdos sensíveis |
| 6 | **Webhooks**: Nump avisa o n8n no upload/download | confirmação de recebimento |
| 7 | **Consentimento de acesso remoto (AnyDesk)** com registro de IP/horário | auditoria de acesso |

**Mais adiante (diferencial):**

- Agente que **roda o combo completo**: lê o ticket, consulta a base, atende simples e documenta.
- Painel do atendimento com histórico das classificações e taxa de acerto da IA.
- Agendamento + pesquisa de satisfação pós-atendimento.

## 6. Por que vale

- **Vira o gargalo da triagem** em algo automático — sênior só vê o que é sênior.
- **Nível 1 cresce mais rápido** (assistido pela IA com fonte, não no escuro).
- **Padronização**: mesmo problema → mesmo procedimento → mesmo resultado.
- **Auditoria**: tudo registrado e rastreável (quem, quando, IP).
- **Segurança**: links temporários; decisões impactantes passam por pessoa.
- **Scale**: regra nova = fluxo novo no n8n, **sem reescrever sistema**.
- Aproveita o que já existe: **Nump pronto**, **n8n + IA já rodando em Docker**.

## 7. Esforço e custo honestos

- **Nump** (upload, link curto, auth): investimento já feito.
- **n8n + IA em Docker**: já estão rodando na sua máquina (`localhost:5678`, Gemini Flash grátis).
- **Fase 1** (classificador + link no fluxo): essencialmente **configuração** — dias.
- **Fase 2+** (RAG, expiração, webhooks, consentimento): mudanças **pontuais** no backend.
- Custo principal: tempo de setup + API de IA (ou modelo local). Sem licença fechada.
- Cuidados previstos: entradas externas são tratadas como **não confiáveis** (anti *prompt
  injection*); só ferramentas mínimas conectadas à IA; ações com impacto revistas por humano.

## 8. Próximo passo

**PoC:** um fluxo que recebe um ticket de exemplo, a IA o classifica (simples × complexo /
remoto × presencial) com base numa base pequena, e roteia para você ou para o sênior — mostrando
a sugestão e o motivo para revisão humana.

---

**Resumo em uma linha:** *a IA separa o joio do trigo, o n8n faz o trabalho correr sozinho, e o
Nump entrega o que o cliente precisa — com um link, um clique, zero atrito.*