# Nump 📦

**Nump** é uma plataforma de compartilhamento de arquivos e encurtamento de links, construída com **Spring Boot** e containerizada com **Docker**. A proposta é ser uma alternativa ao MediaFire — com compactação automática de arquivos, geração de links curtos para download e, futuramente, suporte a uma funcionalidade offline.

---

## 💡 Visão do Projeto

O Nump resolve três problemas comuns no compartilhamento de arquivos:

- **Arquivos grandes** são compactados automaticamente em `.zip` antes do upload, reduzindo o tamanho e o tempo de transferência
- **Links longos e feios** são encurtados em tokens de 8 caracteres, fáceis de copiar e compartilhar
- **Dependência de internet** será endereçada futuramente com uma funcionalidade offline (em definição)

---

## ✨ Funcionalidades

- **Upload com compactação** — arquivos enviados são compactados automaticamente via `Compactor`
- **Link de download encurtado** — cada arquivo recebe um token único e um link curto para download
- **Encurtamento de URLs** — URLs longas viram links curtos com redirecionamento automático
- **Redirecionamento** — o token resolve para o destino original (URL ou arquivo)
- **Dockerizado** — execução consistente em qualquer ambiente via Docker Compose

---

## 🚀 Endpoints

### Links

#### Encurtar uma URL
```http
POST /links
Content-Type: application/json

{
  "url": "https://exemplo.com/link/muito/longo",
  "description": "Meu link"
}
```
**Resposta:**
```json
{
  "id": 1,
  "url": "https://exemplo.com/link/muito/longo",
  "urlReduced": "http://localhost:8080/links/r/a1b2c3d4",
  "token": "a1b2c3d4",
  "description": "Meu link"
}
```

#### Redirecionar pelo token
```http
GET /links/r/{token}
```
Redireciona para a URL original associada ao token.

#### Listar todos os links
```http
GET /links
```

#### Buscar link por ID
```http
GET /links/{id}
```

#### Deletar link
```http
DELETE /links/{id}
```

---

### Arquivos

#### Upload e compactação
```http
POST /files/upload
Content-Type: multipart/form-data

file: <arquivo>
```
**Resposta:**
```json
{
  "id": 1,
  "path": "/uploads/arquivo_a1b2c3d4.zip",
  "size": 204800,
  "token": "a1b2c3d4",
  "downloadUrl": "http://localhost:8080/files/r/a1b2c3d4"
}
```

#### Download pelo token
```http
GET /files/r/{token}
```
Retorna o arquivo `.zip` para download direto.

#### Listar todos os arquivos
```http
GET /files
```

#### Buscar arquivo por ID
```http
GET /files/{id}
```

#### Deletar arquivo
```http
DELETE /files/{id}
```

---

## 🐳 Executando com Docker

```bash
# Sobe os containers (API + Banco)
sudo docker-compose up --build
```

Para rebuild do `.jar` antes de subir:
```bash
# 1 — Regera o .jar
./mvnw clean package -DskipTests

# 2 — Sobe os containers
sudo docker-compose up --build
```

---

## 🏗️ Estrutura do Projeto

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
│   └── model/
│       ├── File.java                # Entidade de arquivo (path, size, token)
│       └── Link.java                # Entidade de link (url, urlReduced, token)
│
├── uploads/                         # Pasta local para armazenar os .zip gerados
├── docker-compose.yml
└── pom.xml
```

---

## 🛠️ Tech Stack

| Camada | Tecnologia |
|--------|-----------|
| Framework | Spring Boot |
| Containerização | Docker / Docker Compose |
| Linguagem | Java |
| Banco de Dados | Configurável (H2 / PostgreSQL) |
| Compactação | `java.util.zip` (ZipOutputStream) |

---
