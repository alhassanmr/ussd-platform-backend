# USSD as a Service Platform

A multi-tenant platform that lets companies build and manage USSD applications through a visual UI — no coding required.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        USSD Platform                         │
│                                                             │
│  React Dashboard  ──►  Spring Boot API  ──►  PostgreSQL     │
│  (Menu Builder)         (REST + Engine)       (Menus/Apps)  │
│                              │                              │
│                         Redis Cache                         │
│                         (Sessions)                          │
│                              │                              │
│           ┌──────────────────┴──────────────────┐          │
│           ▼                                      ▼          │
│   Africa's Talking                            Hubtel        │
│     Gateway                                   Gateway       │
└─────────────────────────────────────────────────────────────┘
```

## Features

- **Multi-tenant** — each company has isolated apps and menus
- **Visual menu builder** — drag-and-drop style UI, no coding needed
- **Multiple gateways** — Africa's Talking, Hubtel, and custom (pluggable)
- **5 menu item types**: Display, Input, Webhook, Router, End
- **Variable capture** — collect user input and interpolate in messages (`{{phone_number}}`)
- **Session management** — Redis-backed with configurable timeout
- **JWT authentication** — per-tenant user accounts with roles

## Quick Start

### With Docker (recommended)
```bash
docker-compose up -d
```

The platform will be available at:
- Frontend: http://localhost:3000
- Backend API: http://localhost:8080

### Local Development

**Backend:**
```bash
cd backend
# Start PostgreSQL and Redis first (or use Docker)
docker run -d -p 5432:5432 -e POSTGRES_DB=ussd_platform -e POSTGRES_PASSWORD=postgres postgres:16
docker run -d -p 6379:6379 redis:7

mvn spring-boot:run
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```

## Webhook URL Format

Each USSD app gets a unique webhook URL:
```
POST http://YOUR_SERVER:8080/ussd/webhook/{appId}
```

Configure this URL in your gateway (Africa's Talking, Hubtel, etc.) as the callback URL.

## Menu Item Types

| Type | Description |
|------|-------------|
| `DISPLAY` | Shows a numbered option in the menu |
| `INPUT` | Prompts user for free-text input, stores to a variable |
| `WEBHOOK` | Calls an external API, can show the response |
| `ROUTER` | Navigates to another menu |
| `END` | Terminates the session with a message |

## API Endpoints

### Auth
- `POST /api/auth/register` — Create company + owner account
- `POST /api/auth/login` — Get JWT token
- `GET /api/auth/me` — Current user

### Apps
- `GET /api/apps` — List all apps for tenant
- `POST /api/apps` — Create app
- `GET /api/apps/:id` — Get app
- `PUT /api/apps/:id` — Update app
- `DELETE /api/apps/:id` — Delete app

### Menus
- `GET /api/apps/:appId/menus` — List menus
- `POST /api/apps/:appId/menus` — Create menu
- `PUT /api/apps/:appId/menus/:id` — Update menu
- `POST /api/apps/:appId/menus/:id/items` — Add menu item
- `PUT /api/apps/:appId/menus/:id/items/:itemId` — Update item
- `DELETE /api/apps/:appId/menus/:id/items/:itemId` — Delete item

### USSD Webhook (public)
- `POST /ussd/webhook/:appId` — Gateway callback (Africa's Talking or Hubtel)

## Adding a New Gateway

Implement the `UssdGateway` interface:

```java
@Component
public class MyGateway implements UssdGateway {

    @Override
    public UssdRequest parseRequest(String rawBody) {
        // parse your gateway's format
    }

    @Override
    public String formatResponse(UssdResponse response) {
        // format response for your gateway
    }

    @Override
    public String getGatewayType() {
        return "MY_GATEWAY";
    }
}
```

Spring will auto-discover and register it via the `GatewayFactory`.

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_USERNAME` | postgres | PostgreSQL user |
| `DB_PASSWORD` | postgres | PostgreSQL password |
| `REDIS_HOST` | localhost | Redis host |
| `JWT_SECRET` | (change this!) | 256-bit JWT secret |
| `AT_API_KEY` | — | Africa's Talking API key |
| `AT_USERNAME` | — | Africa's Talking username |
| `HUBTEL_CLIENT_ID` | — | Hubtel client ID |
| `HUBTEL_CLIENT_SECRET` | — | Hubtel client secret |
