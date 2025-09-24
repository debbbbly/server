# Local Development Environment

This directory contains Docker configuration for running LiveKit services locally for development.

## Quick Start

1. **Setup environment variables:**
   ```bash
   cp .env.local.example .env.local
   # Edit .env.local with your actual S3 credentials if needed
   ```

2. **Start services:**
   ```bash
   docker-compose up -d
   ```

3. **Stop services:**
   ```bash
   docker-compose down
   ```

## Services

- **Redis**: `localhost:6379` - Caching and queuing
- **LiveKit**: `localhost:7880` - Real-time communication server
- **Egress**: Recording and streaming service

## Configuration

- `livekit.yaml` - LiveKit server configuration
- `egress.yaml` - Egress service configuration
- `.env.local` - Environment variables (create from `.env.local.example`)

All services are configured to work together within the Docker network using service names for internal communication.

## Environment Variables

The setup uses the following environment variables:
- `LIVEKIT_API_KEY` - API key for LiveKit authentication
- `LIVEKIT_API_SECRET` - API secret for LiveKit authentication
- `S3_ENDPOINT` - S3-compatible storage endpoint
- `S3_ACCESS_KEY` - S3 access key
- `S3_SECRET` - S3 secret key
- `S3_BUCKET` - S3 bucket name