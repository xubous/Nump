# Nump 🔗

**Nump** is a REST API for URL shortening, built with **Spring Boot** and containerized with **Docker**. It converts long URLs into short, unique identifiers and redirects users to the original destination efficiently.

---

## ✨ Features

- **URL Shortening** — generates a compact, unique code for any long URL
- **Fast Redirection** — resolves the short code and redirects to the original URL
- **Unique Code Generation** — collision-resistant identifier generation
- **Dockerized** — fully containerized for consistent execution across environments
- **Scalable Architecture** — designed to grow with caching, analytics, and more

---

## 🚀 Endpoints

### Shorten a URL
```http
POST /shorten
Content-Type: application/json

{
  "url": "https://example.com/very/long/link"
}
```
**Response:**
```json
{
  "short_url": "http://localhost:8080/abc123"
}
```

### Redirect
```http
GET /{code}
```
Redirects the user to the original URL associated with the given code.

---

## 🐳 Running with Docker

```bash
# Build the image
docker build -t nump .

# Run the container
docker run -p 8080:8080 nump
```

Or with Docker Compose:

```bash
docker-compose up
```

---

## 🏗️ Project Structure

```
Nump/
│
├── src/
│   ├── controllers/     # HTTP layer — handles incoming requests
│   ├── services/        # Business logic — URL shortening and resolution
│   ├── repositories/    # Data access layer
│   └── models/          # Domain entities
│
├── database/            # Database migrations and configuration
├── config/              # Application configuration files
└── main.*               # Application entry point
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot |
| Containerization | Docker / Docker Compose |
| Language | Java |
| Database | Configurable (H2 / PostgreSQL) |

---

## 📈 Planned Improvements

- **Caching** with Redis for faster redirects
- **Rate Limiting** to prevent abuse
- **Analytics Dashboard** for tracking link usage
- **Custom Short URLs** — user-defined slugs
- **Link Expiration** — time-based URL invalidation
- **Structured Logging** for observability

---

## 📄 License

Free to use for educational and development purposes.
