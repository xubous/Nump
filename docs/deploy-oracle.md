# Deploy em VM Oracle Cloud (Always Free) — hospedagem que nunca dorme

> **Por quê:** no Render free o app **dorme após 15 min** sem tráfego (~1 min pra acordar) e o Neon
> free **suspende o banco após 5 min** (isso é rápido, ~0,5 s, mas o combo com o Render dava a
> sensação de "5 minutos pra ligar"). Na VM da Oracle: **nada dorme**, **não tem cota de horas** e
> os **uploads persistem** (no Render eles somem a cada deploy/restart — disco efêmero).
>
> **Custo:** R$0 **para sempre** (recursos "Always Free" da Oracle Cloud).

## Arquitetura final

```
cliente ──http://<IP-DA-VM>:8081──▶ nginx-free (Docker: app)
                                       ├── app   (Spring Boot, porta 8081)
                                       ├── db    (Postgres 16, volume db_data)
                                       └── uploads volume uploads_data  ← sobrevive a tudo
```

- Repo de deploy: **`github.com/xubous/Nump`** (o mesmo que o Render builda) — sincronizado com o
  monorepo `xubous/xub @ projects/Nump`.
- `docker compose` do próprio repo: app + Postgres, com `restart: unless-stopped` (volta sozinho
  após reboot), uploads em volume e Postgres só em `127.0.0.1`.

---

## Fase 1 — Criar a conta Oracle (~15 min, você faz)

1. <https://cloud.oracle.com/free> → **Start for free**.
2. País + telefone + e-mail; cartão de crédito é pedido **só para verificação — não é cobrado**.
3. ⚠️ **Home region é definitiva** (Always Free só existe nela). Tente **Brazil East (São Paulo)**
   `sa-saopaulo-1` (menor latência do Brasil). Se a criação de instância ARM falhar por falta de
   capacidade (Fase 2), veja "Sem capacidade A1?" abaixo — a capacidade da Oracle varia por horário.
4. Confirme o e-mail e entre no console.

## Fase 2 — Criar a instância (VM ARM)

1. Menu **Compute → Instances → Create instance**.
2. **Name:** `nump`.
3. **Image (Boot volume):** **Canonical Ubuntu** 22.04 ou 24.04 (aarch64) — qualquer um serve.
4. **Shape:** **VM.Standard.A1.Flex** → **1 OCPU + 6 GB** (sobra de sobra; o free dá até
   **2 OCPU + 12 GB 24/7** = 1.500 OCPU-h + 9.000 GB-h por mês). Pode ir até 2/12 se quiser.
5. **SSH keys:** selecione **Generate a key pair** e **baixe a chave `.ssh` private** (guarde!),
   ou cole a sua pública (`ssh-keygen -t ed25519 -C nump` → cole o `.pub`).
6. **Boot volume:** 100 GB (o free dá até 200 GB).
7. **Create** → anote o **IP público**.

## Fase 3 — Abrir a porta na rede (Security List)

1. **Networking → Virtual Cloud Networks →** a VCN criada → **Subnet** → **Security Lists →
   Default Security List → Add ingress rules:**
   - Source CIDR: `0.0.0.0/0`
   - Destination Port: **8081** (TCP)
   - (SSH/22 já vem aberto pela própria instância/VCN padrão)

## Fase 4 — Instalar Docker na VM

```bash
ssh -i ~/.ssh/<chave>.ssh ubuntu@<IP-DA-VM>

# Docker Engine oficial (funciona em ARM)
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
newgrp docker
docker version
```

## Fase 5 — Publicar o Nump

```bash
git clone https://github.com/xubous/Nump.git
cd Nump
```

Crie o `.env` (não existe no repo — é seu):

```bash
cat > .env <<'EOF'
DB_NAME=nump
DB_USER=nump
DB_PASSWORD=<troque-por-uma-senha-aleatoria>
JWT_SECRET=<troque-por-32-bytes-aleatorios>
APP_BASE_URL=http://<IP-DA-VM>:8081
# N8N_WEBHOOK_URL=           # opcional (webhook n8n)
EOF

# gerador rápido de segredos (roda na VM):
#   openssl rand -base64 24   # para DB_PASSWORD
#   openssl rand -hex 16      # para JWT_SECRET (32 chars)
```

Suba tudo:

```bash
docker compose up -d --build
docker compose ps            # app + db = Up/healthy
curl http://localhost:8081/actuator/health   # {"status":"UP"}
```

Fora da VM: `http://<IP-DA-VM>:8081/` → home do Nump. **Pronto: nunca mais dorme.**

## Fase 6 — (Opcional) migrar os dados do Neon

Hoje o banco de produção só tem dados de teste — pode começar zerado. Se quiser manter:

```bash
# no SEU PC (precisa do cliente psql/pg_dump: apt/brew install postgresql-client)
NEON_URL="$(grep DATABASE_URL ~/Downloads/Nump.env | cut -d= -f2- | tr -d '"' | sed 's/^jdbc://')"
pg_dump "$NEON_URL" -Fc -f nump.dump

scp -i ~/.ssh/<chave>.ssh nump.dump ubuntu@<IP-DA-VM>:
ssh -i ~/.ssh/<chave>.ssh ubuntu@<IP-DA-VM>
cd Nump
docker compose exec -T db pg_restore -U nump --clean --if-exists -d nump < ~/nump.dump
```

## Fase 7 — Validar

1. `http://<IP>:8081/` → home 200 (não 403).
2. Registrar conta → login → subir arquivo → **baixar o link em aba anônima**.
3. Reiniciar tudo e conferir que upload sobrevive:
   ```bash
   docker compose down      # derruba os containers (NÃO apaga os volumes)
   docker compose up -d
   # o arquivo baixado antes ainda tem que funcionar
   ```
4. Painel **Acesso remoto**: gerar link → abrir em aba anônima → autorizar.

---

## Operação do dia a dia

```bash
cd Nump
git pull                              # atualizar código
docker compose up -d --build          # rebuilda só o que mudou (cache do Maven)
docker compose logs -f app            # logs
docker compose exec db pg_dump -U nump nump > backup-$(date +%F).sql   # backup
docker compose restart app            # reiniciar só o app
```

- **Deploy barato:** o Dockerfile baixa as dependências numa camada separada — mudanças de código
  só recompilam (minutos); só mexer no `pom.xml` baixa de novo.
- **Reboot da VM:** `restart: unless-stopped` traz tudo de volta sozinho.

## Segurança

- Postgres escuta **só em `127.0.0.1`** na VM (compose já bindeia assim) — nunca abra 5432 na
  Security List.
- `JWT_SECRET` e `DB_PASSWORD` aleatórios, `.env` fora do git (o `.dockerignore` já impede entrar
  na imagem).
- É **HTTP em IP** — para clientes finais, vale subir um domínio grátis com HTTPS (próxima seção).

## (Opcional) Domínio grátis + HTTPS — DuckDNS + Caddy

Deixa o endereço com cara de produto (`nump.seudominio.duckdns.org`) e certificado automático:

1. Crie uma chave no <https://www.duckdns.org> (ex.: `nump`) e aponte para o IP da VM.
2. Abra as portas **80** e **443** na Security List.
3. Crie `docker-compose.caddy.yaml` ao lado do compose principal:

```yaml
services:
  caddy:
    image: caddy:2
    restart: unless-stopped
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - caddy_data:/data
    depends_on: [app]
volumes:
  caddy_data:
```

4. `Caddyfile`:

```
nump.seudominio.duckdns.org {
    reverse_proxy app:8081
}
```

5. `APP_BASE_URL=https://nump.seudominio.duckdns.org` no `.env` e
   `docker compose -f compose.yaml -f docker-compose.caddy.yaml up -d`.

## Troubleshooting

| Sintoma | Causa provável | Correção |
|---|---|---|
| "Out of capacity" ao criar A1 | Sem capacidade ARM na região/horário | Tente em outro horário (capacidade é dinâmica). Ver "Sem capacidade A1?" |
| `exec format error` no build | Imagem sem variante ARM | Troque a runtime por `eclipse-temurin:21-jdk` (verificado: as imagens usadas hoje **têm** arm64) |
| `Connection refused` fora da VM | Security List sem a 8081 | Fase 3 |
| Página 403 no `/` | Código antigo no ar | `git pull && docker compose up -d --build` |
| Upload some após rebuild | Volume sem montar | Confira `uploads_data:/app/uploads` no compose |
| `password authentication failed for user "nump"` | `DB_PASSWORD` mudou depois do primeiro `up` (volume já existe com a senha antiga) | Reverta o `.env` — ou, **só se os dados forem descartáveis**, `docker compose down -v` (apaga o banco local) |
| ~1 min de espera | É o **Render** que dorme — não é esta VM | Confirme que está acessando o IP da VM |

### Sem capacidade A1?

1. **Retry:** a disponibilidade da A1 varia por horário/dia — tentar de novo costuma funcionar.
2. **Plano B (AMD):** 2× `VM.Standard.E2.1.Micro` (1 GB RAM cada) — dá pra dividir app/db, mas é
   apertado (ex.: `JAVA_TOOL_OPTIONS=-Xmx440m` no app e `shared_buffers=64MB` no Postgres).
   A A1 com 6-12 GB é infinitamente melhor — insistir nela vale a pena.
3. Home region não muda depois; se a conta já estiver em outra região, tudo bem — só espere a
   capacidade lá ou use o plano B.

## Limites Always Free (o que cabe)

| Recurso | Limite |
|---|---|
| VMs ARM (A1) | até **2 OCPU + 12 GB** somando tudo (1.500 OCPU-h + 9.000 GB-h/mês = 24/7) |
| VMs AMD | 2× E2.1.Micro (1/8 OCPU, 1 GB cada) |
| Armazenamento | 200 GB (boot volumes) |
| Tráfego de saída | 10 TB/mês |
| Duração | **para sempre**, mantendo dentro dos limites |

Depois que a VM estiver no ar, **o Render pode ficar como está** (transição) — ou você desliga o
serviço free dele pra não confundir. Links antigos do `nump-tvat.onrender.com` continuam valendo
enquanto o Render existir; os novos já saem com o `APP_BASE_URL` da VM.
