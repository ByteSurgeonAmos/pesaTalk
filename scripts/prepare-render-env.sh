#!/bin/bash

# Script to prepare environment variables for Render deployment
# This reads your .env file and outputs the values formatted for Render

set -e

echo "=========================================="
echo "PesaTalk - Render Environment Variables"
echo "=========================================="
echo ""
echo "Copy these values to your Render dashboard:"
echo "Go to: https://dashboard.render.com → Your Service → Environment"
echo ""
echo "=========================================="
echo ""

# Check if .env file exists
if [ ! -f .env ]; then
    echo "ERROR: .env file not found!"
    echo "Please create .env file with your credentials."
    exit 1
fi

# Source the .env file
set -a
source .env
set +a

echo "# Spring Configuration"
echo "SPRING_PROFILES_ACTIVE=prod"
echo "SERVER_PORT=10000"
echo ""

echo "# Database Configuration (Neon PostgreSQL)"
echo "DATABASE_URL=${DATABASE_URL}"
echo "DATABASE_USERNAME=${DATABASE_USERNAME}"
echo "DATABASE_PASSWORD=${DATABASE_PASSWORD}"
echo ""

echo "# Redis Configuration (Upstash)"
echo "REDIS_HOST=${REDIS_HOST}"
echo "REDIS_PORT=${REDIS_PORT}"
echo "REDIS_PASSWORD=${REDIS_PASSWORD}"
echo "REDIS_SSL=${REDIS_SSL}"
echo ""

echo "# WhatsApp Configuration"
echo "WHATSAPP_PHONE_NUMBER_ID=${WHATSAPP_PHONE_NUMBER_ID}"
echo "WHATSAPP_ACCESS_TOKEN=${WHATSAPP_ACCESS_TOKEN}"
echo "WHATSAPP_VERIFY_TOKEN=${WHATSAPP_VERIFY_TOKEN}"
echo "WHATSAPP_APP_SECRET=${WHATSAPP_APP_SECRET}"
echo ""

echo "# MPesa Configuration"
echo "MPESA_API_BASE_URL=${MPESA_API_BASE_URL}"
echo "MPESA_CONSUMER_KEY=${MPESA_CONSUMER_KEY}"
echo "MPESA_CONSUMER_SECRET=${MPESA_CONSUMER_SECRET}"
echo "MPESA_PASSKEY=${MPESA_PASSKEY}"
echo "MPESA_SHORTCODE=${MPESA_SHORTCODE}"
echo "MPESA_CALLBACK_URL=https://YOUR-APP-NAME.onrender.com/callback/mpesa"
echo ""

echo "# Encryption Key"
echo "ENCRYPTION_PHONE_KEY=${ENCRYPTION_PHONE_KEY}"
echo ""

echo "# JVM Options"
echo "JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC -XX:+UseStringDeduplication -Djava.security.egd=file:/dev/./urandom"
echo ""

echo "=========================================="
echo "IMPORTANT REMINDERS:"
echo "=========================================="
echo "1. Replace YOUR-APP-NAME in MPESA_CALLBACK_URL with your actual Render app name"
echo "2. Database is already configured (Neon PostgreSQL)"
echo "3. Redis is already configured (Upstash)"
echo "4. All credentials are from your .env file"
echo ""
echo "All services are FREE FOREVER! 🎉"
echo ""
echo "For production MPesa, change MPESA_API_BASE_URL to:"
echo "https://api.safaricom.co.ke"
echo ""
