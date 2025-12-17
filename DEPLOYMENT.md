# PesaTalk Deployment Guide - Render

This guide will walk you through deploying PesaTalk to Render.

## Prerequisites

1. **Render Account**: Sign up at https://render.com (free tier available)
2. **GitHub Repository**: Push your code to GitHub
3. **WhatsApp Business API Credentials**:
   - Phone Number ID
   - Access Token
   - Verify Token (create a random string)
   - App Secret

4. **MPesa API Credentials** (Sandbox or Production):
   - Consumer Key
   - Consumer Secret
   - Passkey
   - Business Shortcode

## Step 1: Database Already Configured ✓

You're using **Neon PostgreSQL** (serverless Postgres with generous free tier):
- **Host**: ep-billowing-snow-ah8jns5f-pooler.c-3.us-east-1.aws.neon.tech
- **Database**: pesatalk-db
- **Username**: neondb_owner
- **Free Tier**: 3GB storage, always-on

No action needed - already configured in your `.env` file!

## Step 2: Create Redis Instance (Choose One)

### Option A: Render Redis (Simple)

1. Click **New** → **Redis**
2. Configure:
   - **Name**: `pesatalk-redis`
   - **Region**: Oregon (same as database)
   - **Plan**: Free (25MB)
   - **Max Memory Policy**: `allkeys-lru` (recommended)
3. Click **Create Redis**
4. Once created, note down:
   - **Internal Redis URL** (e.g., `pesatalk-redis:6379`)
   - **Redis Host** (the hostname part)

### Option B: Upstash Redis (Free Tier Alternative)

1. Go to https://upstash.com
2. Create free account
3. Click **Create Database**
4. Choose **Global** (best for availability)
5. Once created, note down:
   - **Endpoint** (Redis host)
   - **Port** (usually 6379)
   - **Password** (if provided)

## Step 3: Deploy the Application

### Option A: Using render.yaml (Recommended)

1. Make sure `render.yaml` is in your repository root
2. Go to https://dashboard.render.com
3. Click **New** → **Blueprint**
4. Connect your GitHub repository
5. Select your repository
6. Click **Apply**
7. Render will automatically create the web service based on `render.yaml`

### Option B: Manual Setup

1. Click **New** → **Web Service**
2. Connect your GitHub repository
3. Configure:
   - **Name**: `pesatalk`
   - **Region**: Oregon
   - **Branch**: `main` (or your default branch)
   - **Runtime**: Java
   - **Build Command**: `./mvnw clean package -DskipTests`
   - **Start Command**: `java $JAVA_OPTS --enable-preview -jar target/pesatalk-1.0.0-SNAPSHOT.jar`
   - **Plan**: Free
4. Click **Create Web Service**

## Step 4: Configure Environment Variables

### Option A: Using Your .env File (Recommended)

You already have credentials in your `.env` file! Use the helper script to prepare them for Render:

```bash
./scripts/prepare-render-env.sh
```

This will output all your environment variables formatted for Render. Simply copy and paste the values into the Render dashboard.

### Option B: Manual Configuration

In your web service settings, go to **Environment** and add these variables:

#### Required Configuration

```bash
# Spring Configuration
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=10000

# Database (Neon PostgreSQL - already configured)
DATABASE_URL=jdbc:postgresql://ep-billowing-snow-ah8jns5f-pooler.c-3.us-east-1.aws.neon.tech/pesatalk-db?sslmode=require&channel_binding=require
DATABASE_USERNAME=neondb_owner
DATABASE_PASSWORD=npg_JgnF6VDR7xIA

# Redis (from Step 2)
REDIS_HOST=<Your Redis Host from Render or Upstash>
REDIS_PORT=6379
REDIS_PASSWORD=<Leave empty if no password, or Upstash password>

# WhatsApp API (from your .env file)
WHATSAPP_PHONE_NUMBER_ID=828972226976367
WHATSAPP_ACCESS_TOKEN=EAA8Ygm4PzYwBQCIZABRDjOyYAwCm6ohaKZAxoUSeK9r6FFZC1vZBgWsvrUZB9rIQBHPhO8BLjmyWH2aqdaISvmKERpibKZBNBKycRBGxw7XrduieVfHTdZCIOkinVAZBp9NIdAjRZBRqfa9wpxj27xHyVTGjz9YZCwLaePZBPNIIXswxdj14Qh95cASbZAGidd6la7FFZC9aFBtAz9wj6FBy8J1RFzZAMgtqU97mUgt4ZAnrB8LSxxNvFYZCUaqYQzbSyStIZCnhw2S5UThI7pegk6rhi00CL
WHATSAPP_VERIFY_TOKEN=8cf5d1480a2d5bdd27cf903b6af0beaf5fd513359726f28cf01da0f75f181e23
WHATSAPP_APP_SECRET=ce6de8e0a38c73e068d8fbde2b071561

# MPesa API (from your .env file)
MPESA_API_BASE_URL=https://sandbox.safaricom.co.ke
MPESA_CONSUMER_KEY=Qbd1Pk75NjEA7wkU7WOwoV8pndjClSnaSQ0jjvhrAUJUhdFO
MPESA_CONSUMER_SECRET=5dgNUz6VBrSC9xFmiaufWAbtNgXAAQdasCMTOpYYrOGhPkQ7NLDCXck5f78acGMl
MPESA_PASSKEY=bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919
MPESA_SHORTCODE=174379
MPESA_CALLBACK_URL=https://YOUR-APP-NAME.onrender.com/callback/mpesa

# Encryption Key (from your .env file)
ENCRYPTION_PHONE_KEY=8cf5d1480a2d5bdd27cf903b6af0beaf5fd513359726f28cf01da0f75f181e23

# JVM Options (Already set in render.yaml)
JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC -XX:+UseStringDeduplication -Djava.security.egd=file:/dev/./urandom
```

#### Important Notes

1. **Replace** `YOUR-APP-NAME` in `MPESA_CALLBACK_URL` with your actual Render app name
2. **Get** `REDIS_HOST` (and optionally `REDIS_PASSWORD`) from your Render Redis or Upstash service
3. **Database** is already configured (Neon PostgreSQL)
4. All WhatsApp and MPesa credentials are already configured in your `.env` file

## Step 5: Database Migration

The application uses Flyway for database migrations. Migrations will run automatically on startup.

The migration files are located in `src/main/resources/db/migration/`.

## Step 6: Configure WhatsApp Webhook

1. Once deployed, get your app URL: `https://your-app-name.onrender.com`
2. Go to your WhatsApp Business API settings
3. Configure the webhook:
   - **Callback URL**: `https://your-app-name.onrender.com/webhook`
   - **Verify Token**: Use the same value as `WHATSAPP_VERIFY_TOKEN`
4. Subscribe to webhook fields: `messages`

## Step 7: Configure MPesa Callback

1. Update `MPESA_CALLBACK_URL` environment variable:
   ```
   MPESA_CALLBACK_URL=https://your-app-name.onrender.com/callback/mpesa
   ```
2. Register this callback URL in your MPesa dashboard

## Step 8: Verify Deployment

1. Check deployment logs in Render dashboard
2. Visit health endpoint: `https://your-app-name.onrender.com/actuator/health`
3. Should return:
   ```json
   {
     "status": "UP"
   }
   ```

## Important Notes

### Free Tier Limitations

- **PostgreSQL**: 90 days free, then $7/month
- **Redis**: 25MB storage, 10 connections
- **Web Service**: 750 hours/month free, spins down after 15 minutes of inactivity

### Spin Down Warning

Render's free tier spins down your service after 15 minutes of inactivity. The first request after spin-down will take 30-60 seconds to wake up. This may cause:

- WhatsApp webhook timeouts (WhatsApp expects responses within 5 seconds)
- MPesa callback failures

**Solutions**:
1. Upgrade to paid plan ($7/month) to prevent spin-down
2. Use an external uptime monitor (e.g., UptimeRobot) to ping your service every 10 minutes
3. Accept the limitation for development/testing

### Production Considerations

Before going to production:

1. **Switch to Paid Plan**: Free tier is not suitable for production
2. **Use Production MPesa API**:
   ```
   MPESA_API_BASE_URL=https://api.safaricom.co.ke
   ```
3. **Enable HTTPS** (Render provides this automatically)
4. **Set up monitoring**: Use Render's metrics or external services
5. **Configure alerts**: Set up email/Slack alerts for downtime
6. **Database backups**: Paid PostgreSQL plans include automatic backups
7. **Review rate limits**: Adjust in `application-prod.yml` if needed

## Monitoring

### Health Checks

- **Application Health**: `https://your-app-name.onrender.com/actuator/health`
- **Metrics**: `https://your-app-name.onrender.com/actuator/metrics`
- **Prometheus**: `https://your-app-name.onrender.com/actuator/prometheus`

### Logs

View logs in Render dashboard:
1. Go to your service
2. Click **Logs** tab
3. View real-time logs

## Troubleshooting

### Build Failures

If the build fails:

1. Check Java version (requires Java 21)
2. Check Maven wrapper permissions:
   ```bash
   git update-index --chmod=+x mvnw
   ```
3. View build logs in Render dashboard

### Application Won't Start

Check logs for:

1. **Database connection errors**: Verify `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
2. **Redis connection errors**: Verify `REDIS_HOST`, `REDIS_PORT`
3. **Missing environment variables**: Ensure all required variables are set
4. **Port binding**: Ensure `SERVER_PORT=10000` (Render expects this port)

### Webhook Verification Failing

1. Verify `WHATSAPP_VERIFY_TOKEN` matches in both Render and WhatsApp dashboard
2. Check webhook URL is correct: `https://your-app-name.onrender.com/webhook`
3. Check logs for webhook verification attempts

### MPesa Callbacks Not Working

1. Verify `MPESA_CALLBACK_URL` is correct
2. Ensure URL is registered in MPesa dashboard
3. Check firewall/security settings
4. Review logs for incoming callback attempts

## Costs

### Free Tier (Current Setup)

- **Neon PostgreSQL**: Free forever (3GB storage, 0.5GB RAM)
- **Redis**: Free (Render: 25MB or Upstash: 10k commands/day)
- **Web Service**: Free (750 hours/month with spin-down)

**Total**: $0/month indefinitely! 🎉

### Production Upgrade (Optional)

- **Neon PostgreSQL**: Free (current plan is fine)
- **Redis**: Free (Render) or $10/month (Upstash Pro for more connections)
- **Web Service**: $7/month (Render Starter - no spin-down)

**Total**: $7/month for production-ready setup (just upgrade web service)

## Support

- **Render Documentation**: https://render.com/docs
- **Render Community**: https://community.render.com
- **Application Issues**: Check logs in Render dashboard

## Next Steps

1. Test WhatsApp integration by sending a message to your WhatsApp number
2. Test MPesa integration by initiating a transaction
3. Monitor logs for any errors
4. Set up uptime monitoring
5. Plan for production upgrade (if needed)
