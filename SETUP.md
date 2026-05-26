# Setup Guide

## 1. Prerequisites
- Java 17+
- Node.js 20+
- Docker & Docker Compose
- A Paystack account (https://paystack.com)
- A Gmail account (for sending emails)

---

## 2. Clone & configure

```bash
git clone https://github.com/alhassanmr/ussd-platform.git
cd ussd-platform
```

Create a `.env` file in the root:

```env
# Database
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT — change this to a random 256-bit string
JWT_SECRET=replace-with-a-very-long-random-secret-string-at-least-32-chars

# Paystack (get from https://dashboard.paystack.com/#/settings/developers)
PAYSTACK_SECRET_KEY=sk_test_xxxxxxxxxxxxxxxxxxxx
PAYSTACK_PUBLIC_KEY=pk_test_xxxxxxxxxxxxxxxxxxxx

# Email (Gmail example — use an App Password, not your real password)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your@gmail.com
MAIL_PASSWORD=your_gmail_app_password
MAIL_FROM=noreply@yourdomain.com

# App
APP_BASE_URL=http://localhost:3000

# Admin setup — change this before deploying!
ADMIN_SETUP_SECRET=pick-a-strong-secret-here
```

---

## 3. Start infrastructure

```bash
# Start PostgreSQL and Redis
docker-compose up postgres redis -d
```

---

## 4. Run the backend

```bash
cd backend
mvn spring-boot:run
```

The API will be at http://localhost:8080. Flyway will auto-run both migrations on startup.

---

## 5. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

The app will be at http://localhost:3000.

---

## 6. Create your admin account ⭐

This is a one-time step. Once done, the endpoint locks itself.

```bash
curl -X POST http://localhost:8080/api/admin/setup \
  -H "Content-Type: application/json" \
  -H "X-Setup-Secret: pick-a-strong-secret-here" \
  -d '{
    "email": "your@email.com",
    "password": "your_strong_password",
    "fullName": "Your Name"
  }'
```

You should get:
```json
{ "message": "Admin account created successfully. You can now log in at /admin" }
```

Now visit http://localhost:3000/admin and log in.

---

## 7. Run everything with Docker

```bash
docker-compose up -d
```

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- Admin panel: http://localhost:3000/admin

---

## 8. Configure Paystack webhook

In your Paystack dashboard (Settings → Webhooks), add:
```
https://yourdomain.com/api/billing/paystack/webhook
```

This handles payment confirmations, subscription cancellations, and failed charges automatically.

---

## 9. Configure your gateway

When a tenant creates a USSD app, give them this webhook URL to paste into their gateway (Africa's Talking / Hubtel):

```
https://yourdomain.com/ussd/webhook/{appId}
```

The app ID is shown in the tenant's dashboard under **Integration**.

---

## Production checklist

- [ ] Change `JWT_SECRET` to a strong random value
- [ ] Change `ADMIN_SETUP_SECRET` to a strong value (or remove the env var after setup)
- [ ] Use a real domain with SSL (HTTPS)
- [ ] Set `APP_BASE_URL` to your production domain
- [ ] Switch Paystack keys from `sk_test_` to `sk_live_`
- [ ] Set up a proper SMTP provider (SendGrid, Mailgun, or AWS SES) instead of Gmail
- [ ] Back up your PostgreSQL database regularly
