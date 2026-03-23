# Api-Link-Shortener

## Description

Api-Link-Shortener is a REST API designed to convert long URLs into short, unique identifiers. These shortened links redirect users to the original URL efficiently and can be extended with features such as analytics, expiration, and link management.

## Features

- URL shortening
- Redirection to original URL
- Unique code generation
- Scalable architecture for future enhancements

## Endpoints

### Create short URL
POST /shorten

Request:
{
  "url": "https://example.com/very/long/link"
}

Response:
{
  "short_url": "http://localhost:8080/abc123"
}

### Redirect
GET /{code}

Redirects to the original URL.

## Project Structure

Api-Link-Shortener/
│
├── src/
│   ├── controllers/
│   ├── services/
│   ├── repositories/
│   └── models/
│
├── database/
├── config/
└── main.*

## How to Run

1. Clone the repository:
git clone <repo-url>

2. Install dependencies and run:
make run
or
npm install
npm start

## Improvements

- Add caching (Redis)
- Implement rate limiting
- Add analytics dashboard
- Support custom URLs
- Structured logging

## License

Free to use for educational and development purposes.
