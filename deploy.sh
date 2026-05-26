#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# USSD Platform — VPS Deployment Script
# Tested on Ubuntu 22.04 (DigitalOcean, AWS EC2, Hetzner)
# Run as root: bash deploy.sh
# ─────────────────────────────────────────────────────────────────

set -e

echo "🚀 USSD Platform deployment starting..."

# ── 1. Install dependencies ───────────────────────────────────────
echo "📦 Installing Docker and Docker Compose..."
apt-get update -q
apt-get install -y docker.io docker-compose git curl nginx certbot python3-certbot-nginx

# ── 2. Clone or pull latest code ──────────────────────────────────
if [ -d "/opt/ussd-platform" ]; then
  echo "📥 Pulling latest code..."
  cd /opt/ussd-platform && git pull origin main
else
  echo "📥 Cloning repository..."
  git clone https://github.com/alhassanmr/ussd-platform.git /opt/ussd-platform
  cd /opt/ussd-platform
fi

# ── 3. Create .env if it doesn't exist ───────────────────────────
if [ ! -f "/opt/ussd-platform/.env" ]; then
  echo "⚙️  Creating .env file — EDIT THIS before restarting!"
  cat > /opt/ussd-platform/.env << 'ENV'
DB_USERNAME=postgres
DB_PASSWORD=change_this_db_password
REDIS_HOST=redis
REDIS_PORT=6379
JWT_SECRET=change_this_to_a_very_long_random_string_at_least_64_chars
PAYSTACK_SECRET_KEY=sk_live_your_key_here
PAYSTACK_PUBLIC_KEY=pk_live_your_key_here
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your@gmail.com
MAIL_PASSWORD=your_gmail_app_password
MAIL_FROM=noreply@yourdomain.com
APP_BASE_URL=https://yourdomain.com
ADMIN_SETUP_SECRET=change_this_to_a_strong_secret
ENV
  echo ""
  echo "⚠️  IMPORTANT: Edit /opt/ussd-platform/.env with your real values, then run this script again."
  exit 0
fi

# ── 4. Build and start containers ────────────────────────────────
echo "🐳 Building and starting Docker containers..."
cd /opt/ussd-platform
docker-compose down --remove-orphans
docker-compose up -d --build

echo "⏳ Waiting for services to start..."
sleep 20

# ── 5. Check health ───────────────────────────────────────────────
echo "🔍 Checking backend health..."
if curl -sf http://localhost:8080/api/auth/login -X POST \
  -H "Content-Type: application/json" \
  -d '{}' > /dev/null 2>&1; then
  echo "✅ Backend is up"
else
  echo "⚠️  Backend may still be starting. Check logs: docker-compose logs backend"
fi

echo ""
echo "✅ Deployment complete!"
echo ""
echo "📋 Next steps:"
echo "  1. Frontend:  http://$(curl -s ifconfig.me):3000"
echo "  2. API:       http://$(curl -s ifconfig.me):8080"
echo "  3. Create admin: see SETUP.md"
echo "  4. Set up SSL: certbot --nginx -d yourdomain.com"
echo ""
echo "📄 View logs: docker-compose -f /opt/ussd-platform/docker-compose.yml logs -f"
