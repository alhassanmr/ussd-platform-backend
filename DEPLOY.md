# Deployment Guide

Three options from easiest to most control:

---

## Option A — Railway (Recommended, ~10 mins)

Railway deploys directly from GitHub. No server management needed.

### Steps

1. Go to [railway.app](https://railway.app) and sign up with GitHub
2. Click **New Project → Deploy from GitHub repo**
3. Select `alhassanmr/ussd-platform`
4. Railway will detect the `railway.toml` and start building

5. Add a **PostgreSQL** database:
   - In your project, click **+ New** → **Database** → **PostgreSQL**
   - Railway auto-injects `DATABASE_URL`

6. Add a **Redis** instance:
   - Click **+ New** → **Database** → **Redis**

7. Set environment variables (in Railway dashboard → Variables):
```
JWT_SECRET=your_long_random_secret
PAYSTACK_SECRET_KEY=sk_live_xxxx
PAYSTACK_PUBLIC_KEY=pk_live_xxxx
MAIL_USERNAME=your@gmail.com
MAIL_PASSWORD=your_app_password
MAIL_FROM=noreply@yourdomain.com
ADMIN_SETUP_SECRET=your_setup_secret
APP_BASE_URL=https://your-app.railway.app
```

8. Deploy the frontend as a second service from the same repo, pointing to `frontend/Dockerfile`

9. Set up a custom domain in Railway → Settings → Domains

**Cost:** ~$10-15/month for hobby plan

---

## Option B — Render.com (Has free tier)

1. Go to [render.com](https://render.com) and connect your GitHub
2. Click **New** → **Blueprint**
3. Select `alhassanmr/ussd-platform` — Render reads `render.yaml` automatically
4. Fill in the secret environment variables when prompted
5. Click **Apply** — Render builds and deploys everything

**Cost:** Free tier available (services sleep after 15 mins inactivity — upgrade to $7/mo to keep always-on)

---

## Option C — Your own VPS (Most control)

Best for production. Use DigitalOcean ($12/mo), Hetzner (€4/mo), or AWS EC2.

### 1. Create a server
- DigitalOcean: Create Droplet → Ubuntu 22.04 → Basic → $12/mo
- Note the IP address

### 2. SSH in and run the deploy script
```bash
ssh root@YOUR_SERVER_IP
curl -o deploy.sh https://raw.githubusercontent.com/alhassanmr/ussd-platform/main/deploy.sh
bash deploy.sh
```

The script will:
- Install Docker and dependencies
- Clone the repo
- Create a `.env` template for you to fill in

### 3. Edit the .env file
```bash
nano /opt/ussd-platform/.env
```
Fill in all the values, then run `bash deploy.sh` again.

### 4. Set up SSL (HTTPS)
```bash
# Point your domain's A record to your server IP first, then:
certbot --nginx -d yourdomain.com -d www.yourdomain.com
```

### 5. Create your admin account
```bash
curl -X POST https://yourdomain.com/api/admin/setup \
  -H "Content-Type: application/json" \
  -H "X-Setup-Secret: your_setup_secret" \
  -d '{"email":"you@email.com","password":"strongpassword","fullName":"Your Name"}'
```

---

## After deployment — checklist

- [ ] App loads at your domain
- [ ] Can register a new tenant account
- [ ] Admin login works at `/admin`
- [ ] Create a test USSD app and copy the webhook URL
- [ ] Paste webhook URL into Africa's Talking or Hubtel
- [ ] Test by dialling your short code
- [ ] Paystack webhook configured at `https://yourdomain.com/api/billing/paystack/webhook`
- [ ] SSL certificate active (HTTPS)

---

## Troubleshooting

**Backend not starting:**
```bash
docker-compose logs backend
```

**Database connection error:**
- Check `DB_USERNAME`, `DB_PASSWORD`, and `SPRING_DATASOURCE_URL` are correct
- Make sure PostgreSQL container is running: `docker-compose ps`

**Emails not sending:**
- Gmail requires an [App Password](https://myaccount.google.com/apppasswords), not your regular password
- Enable 2FA on Gmail first, then generate an App Password
