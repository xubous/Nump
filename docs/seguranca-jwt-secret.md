# Segurança — trocar o `JWT_SECRET` em produção

> **Por que isso importa mais que qualquer outra coisa no Nump:** o `JWT_SECRET` assina os tokens
> de login. Quem descobre esse segredo **forja um token válido** (inclusive de `ADMIN`) e acessa
> **tudo**: arquivos de todos os clientes, sessões de acesso remoto, cadastro de usuários. Um
> segredo previsível = autenticação comprometida por inteiro.
>
> O `AGENTS.md` registra que o segredo de produção atual é "válido, 32 bytes, mas previsível".
> Este guia é o como trocar.

## 0. Regra de ouro

- **Gere aleatório**, nunca digite à mão nem reaproveite exemplo: `openssl rand -base64 48`.
- **Nunca commite** o `.env` (o `.gitignore` e o `.dockerignore` já cobrem).
- **Troque ao trocar de máquina/servidor** e se desconfiar de vazamento.

## 1. Gerar um segredo novo e forte

Na sua máquina (ou na VM):

```bash
openssl rand -base64 48
# → ex.: 3p8Kd0nW7xQ2fT5mB9vR1yL4sA6cE8gH0jN2kP5rU7wZ9xV1bD3f
```

Guarde o valor — ele **não** fica no git.

## 2. Aplicar no servidor (Docker / Oracle VM)

O compose lê `JWT_SECRET` do `.env` da raiz do repo. Passos:

```bash
ssh -i ~/.ssh/<chave>.ssh ubuntu@<IP-DA-VM>
cd Nump

# 1) gere o segredo novo
NOVO="$(openssl rand -base64 48)"

# 2) troque só a linha do JWT_SECRET no .env (mantém o resto)
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=${NOVO}|" .env
grep JWT_SECRET .env      # confira (aparece o valor novo)

# 3) recrie os containers para o app reler o .env
docker compose up -d
docker compose logs -f app | head    # espere "Started NumpApplication"
```

> `docker compose restart app` **não** garante reler variáveis novas do `.env` em todos os casos —
> use `up -d` (ele recria o container se a config mudou).

## 3. Consequência (esperada)

Trocar o segredo **invalida todos os tokens já emitidos**: todos os usuários precisam **fazer
login de novo**. Os links públicos (download, coleta, consentimento) **continuam funcionando** —
eles não dependem de JWT.

Se quiser evitar relogar todo mundo, **não é possível** com JWT simétrico simples: mudar a chave é
tudo-ou-nada. (Uma evolução futura seria suportar duas chaves durante uma janela de transição.)

## 4. Render / Neon (se ainda estiver usando)

No painel do Render: **Environment → JWT_SECRET → Edit** → cole o valor novo → **Save** (o Render
redeploya sozinho). Sem SSH, é só isso.

## 5. Conferir que está valendo

```bash
# um token antigo deve parar de funcionar (espera 403)
curl -s -o /dev/null -w '%{http_code}\n' http://<IP-DA-VM>:8081/files \
     -H "Authorization: Bearer <TOKEN_ANTIGO>"

# login novo funciona (espera 200 + token)
curl -s -X POST http://<IP-DA-VM>:8081/users/login \
     -H 'Content-Type: application/json' \
     -d '{"email":"SEU@EMAIL","password":"SUA_SENHA"}' | head -c 200
```

## 6. Bônus: HTTPS

O `JWT_SECRET` protege a **assinatura**, mas se o site for **HTTP** o token trafega em texto claro
na rede — qualquer um no caminho lê. Para clientes finais, suba HTTPS (domínio grátis DuckDNS +
Caddy) — passo a passo na seção final de `docs/deploy-oracle.md`. Com HTTPS,
`APP_BASE_URL=https://seu.dominio` e os links já saem seguros.
