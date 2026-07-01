# Nump

![Build](https://img.shields.io/badge/build-passing-brightgreen)
![Java](https://img.shields.io/badge/java-21-orange)
![Spring Boot](https://img.shields.io/badge/spring--boot-3.x-brightgreen)
![License](https://img.shields.io/badge/license-MIT-blue)

**Nump** é uma plataforma de compartilhamento de arquivos e encurtamento de links, construída com Spring Boot e containerizada com Docker. A proposta é ser uma alternativa ao MediaFire — com compactação automática de arquivos, geração de links curtos para download e, futuramente, suporte a funcionalidade offline.

🔗 **Demo:** [numpfm.vercel.app](https://numpfm.vercel.app) · **API:** [nump-tvat.onrender.com](https://nump-tvat.onrender.com)

> ⚠️ A API roda em instância gratuita do Render e pode levar até ~1 minuto para responder após período de inatividade (cold start).

---

## Índice

- [Visão do Projeto](#visão-do-projeto)
- [Funcionalidades](#funcionalidades)
- [Tech Stack](#tech-stack)
- [Autenticação](#autenticação)
- [Endpoints](#endpoints)
- [Variáveis de Ambiente](#variáveis-de-ambiente)
- [Como Executar](#como-executar)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Licença](#licença)

---

## Visão do Projeto

O Nump resolve três problemas comuns no compartilhamento de arquivos:

- **Arquivos grandes** são compactados automaticamente em `.zip` antes do upload, reduzindo o tamanho e o tempo de transferência.
- **Links longos e feios** são encurtados em tokens de 8 caracteres, fáceis de copiar e compartilhar.
- **Dependência de internet** será endereçada futuramente com uma funcionalidade offline (em definição).

---

## Funcionalidades

- **Upload com compactação** — arquivos enviados são compactados automaticamente via `Compactor`
- **Link de download encurtado** — cada arquivo recebe um token único e um link curto para download
- **Encurtamento de URLs** — URLs longas viram links curtos com redirecionamento automático
- **Redirecionamento** — o token resolve para o destino original (URL ou arquivo)
- **Autenticação via JWT** — rotas sensíveis protegidas por token, com controle de acesso por role
- **Dockerizado** — execução consistente em qualquer ambiente via Docker Compose

---

## Tech Stack

| Camada | Tecnologia |
|---|---|
| Framework | Spring Boot |
| Segurança | Spring Security + JWT |
| Containerização | Docker / Docker Compose |
| Linguagem | Java 21 |
| Banco de Dados | H2 (dev) / PostgreSQL (produção) |
| Compactação | `java.util.zip` (ZipOutputStream) |
| Deploy | Render (API) · Vercel (frontend) |

---

## Autenticação

A API usa autenticação stateless via **JWT**. O fluxo básico é:

1. Registre um usuário:
   ```http
   POST /users/register
   ```
2. Faça login para obter o token:
   ```http
   POST /users/login
   ```
   **Resposta:**
   ```json
   {
     "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
   }
   ```
3. Envie o token no header `Authorization` das requisições protegidas:
   ```http
   Authorization: Bearer <token>
   ```

Rotas administrativas (`GET /users`, `DELETE /users/{id}`) exigem role `ADMIN`.

---

## Endpoints

### Links

#### Encurtar uma URL
```http
POST /links
Content-Type: application/json
Authorization: Bearer <token>

{
  "url": "https://exemplo.com/link/muito/longo",
  "description": "Meu link"
}
```

**Resposta `201 Created`:**
```json
{
  "id": 1,
  "url": "https://exemplo.com/link/muito/longo",
  "urlReduced": "https://nump-tvat.onrender.com/links/r/a1b2c3d4",
  "token": "a1b2c3d4",
  "description": "Meu link"
}
```

| Status | Descrição |
|---|---|
| `201` | Link criado com sucesso |
| `400` | URL inválida ou corpo malformado |
| `401` | Token ausente ou inválido |

#### Redirecionar pelo token
```http
GET /links/r/{token}
```
Redireciona para a URL original associada ao token. Rota pública.

| Status | Descrição |
|---|---|
| `302` | Redirecionamento bem-sucedido |
| `404` | Token não encontrado |

#### Listar todos os links
```http
GET /links
Authorization: Bearer <token>
```

#### Buscar link por ID
```http
GET /links/{id}
Authorization: Bearer <token>
```

| Status | Descrição |
|---|---|
| `200` | Link encontrado |
| `404` | Link não existe |

#### Deletar link
```http
DELETE /links/{id}
Authorization: Bearer <token>
```

| Status | Descrição |
|---|---|
| `204` | Removido com sucesso |
| `403` | Sem permissão para remover este link |
| `404` | Link não existe |

---

### Arquivos

#### Upload e compactação
```http
POST /files/upload
Content-Type: multipart/form-data
Authorization: Bearer <token>

file: <arquivo>
```

**Resposta `201 Created`:**
```json
{
  "id": 1,
  "path": "/uploads/arquivo_a1b2c3d4.zip",
  "size": 204800,
  "token": "a1b2c3d4",
  "downloadUrl": "https://nump-tvat.onrender.com/files/r/a1b2c3d4"
}
```

| Status | Descrição |
|---|---|
| `201` | Upload realizado com sucesso |
| `400` | Arquivo ausente ou inválido |
| `413` | Arquivo excede o tamanho máximo permitido |

#### Download pelo token
```http
GET /files/r/{token}
```
Retorna o arquivo `.zip` para download direto. Rota pública.

| Status | Descrição |
|---|---|
| `200` | Download iniciado |
| `404` | Token não encontrado |

#### Listar todos os arquivos
```http
GET /files
Authorization: Bearer <token>
```

#### Buscar arquivo por ID
```http
GET /files/{id}
Authorization: Bearer <token>
```

#### Deletar arquivo
```http
DELETE /files/{id}
Authorization: Bearer <token>
```

| Status | Descrição |
|---|---|
| `204` | Removido com sucesso |
| `403` | Sem permissão para remover este arquivo |
| `404` | Arquivo não existe |

---

## Variáveis de Ambiente

Crie um arquivo `.env` na raiz do projeto (veja `.env.example`) com as seguintes variáveis:

| Variável | Descrição | Obrigatória | Padrão |
|---|---|---|---|
| `DATABASE_URL` | URL de conexão JDBC | Produção | `jdbc:h2:mem:testdb` |
| `DATABASE_USER` | Usuário do banco de dados | Produção | `sa` |
| `DATABASE_PASS` | Senha do banco de dados | Produção | *(vazio)* |
| `DATABASE_DRIVER` | Driver JDBC | Produção | `org.h2.Driver` |
| `SPRING_JPA_DATABASE_PLATFORM` | Dialeto do Hibernate | Não | `org.hibernate.dialect.H2Dialect` |
| `JWT_SECRET` | Chave secreta para assinatura dos tokens JWT | **Sim** | — |
| `APP_BASE_URL` | URL base usada na geração de links | Não | `http://localhost:8080` |
| `H2_CONSOLE_ENABLED` | Habilita o console web do H2 (apenas dev) | Não | `false` |

> A aplicação **não sobe** sem `JWT_SECRET` definida — é uma escolha intencional para evitar segredos fracos em produção.

---

## Como Executar

### Com Docker (recomendado)

```bash
# Sobe os containers (API + Banco)
docker-compose up --build
```

Para rebuild do `.jar` antes de subir:
```bash
./mvnw clean package -DskipTests
docker-compose up --build
```

### Localmente, sem Docker

Requer Java 21+ e Maven.

```bash
# Usando o banco H2 em memória (padrão de dev)
./mvnw spring-boot:run
```

A API sobe em `http://localhost:8080`.

---

## Estrutura do Projeto

```
Nump/
│
├── src/
│   ├── controller/
│   │   ├── FileController.java      # Rotas de upload e download de arquivos
│   │   └── LinkController.java      # Rotas de encurtamento e redirecionamento
│   │
│   ├── service/
│   │   ├── FileService.java         # Lógica de upload, compactação e download
│   │   ├── LinkService.java         # Lógica de criação e resolução de links
│   │   ├── Compactor.java           # Compactação de arquivos em .zip
│   │   └── Shortener.java           # Geração de tokens e links curtos
│   │
│   ├── repository/
│   │   ├── FileRepository.java      # Acesso ao banco para arquivos
│   │   └── LinkRepository.java      # Acesso ao banco para links
│   │
│   ├── security/
│   │   ├── SecurityConfig.java      # Regras de autorização e CORS
│   │   ├── JwtAuthFilter.java       # Filtro de validação de token
│   │   └── JwtService.java          # Geração e validação de JWT
│   │
│   └── model/
│       ├── File.java                # Entidade de arquivo (path, size, token)
│       └── Link.java                # Entidade de link (url, urlReduced, token)
│
├── uploads/                         # Pasta local para armazenar os .zip gerados
├── docker-compose.yml
├── Dockerfile
└── pom.xml
```

---

## Licença

Este projeto está licenciado sob a licença MIT — veja o arquivo [LICENSE](LICENSE) para mais detalhes.
