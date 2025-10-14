# Local Development Environment

This directory contains Docker configuration for running the complete Debbly stack locally for development.

## Quick Start

1. **Setup environment variables:**
   ```bash
   # For LiveKit/Egress
   cp .env.local.example .env.local
   # For the API
   cp .env.example .env
   # Edit both files with your actual credentials
   ```

2. **Build and start services:**
   ```bash
   cd local
   docker-compose up -d --build
   ```

3. **View logs:**
   ```bash
   docker-compose logs -f debbly-api
   docker-compose logs -f livekit
   ```

4. **Stop services:**
   ```bash
   docker-compose down
   ```

## Services

### Core Services
- **Debbly API**: `localhost:8080` - Spring Boot Kotlin application
  - Health: http://localhost:8080/actuator/health
  - Metrics: http://localhost:8080/actuator/prometheus
- **Redis**: `localhost:6379` - Caching and queuing
- **LiveKit**: `localhost:7880` - Real-time communication server
- **Egress**: Recording and streaming service (port 9094 for metrics)

### Monitoring Stack
- **Prometheus**: `localhost:9090` - Metrics collection
  - Targets: http://localhost:9090/targets
- **Grafana**: `localhost:4000` - Visualization dashboards
  - Username: `admin` / Password: `Xfal4XX7aD`
- **Loki**: `localhost:3100` - Log aggregation
- **Promtail**: Automatic Docker log collection

## Configuration Files

- `livekit.yaml` - LiveKit server configuration
- `egress.yaml` - Egress service configuration
- `prometheus.yml` - Prometheus scrape configuration
- `loki.yaml` - Loki log storage configuration
- `promtail.yaml` - Promtail log collection configuration
- `.env.local` - Environment variables for LiveKit/Egress
- `.env` - Environment variables for Debbly API (create from `.env.example`)

## Grafana Setup

1. **Access Grafana:** http://localhost:4000
2. **Add Prometheus data source:**
   - Go to Configuration → Data Sources → Add data source
   - Select Prometheus
   - URL: `http://prometheus:9090`
   - Click "Save & Test"

3. **Add Loki data source:**
   - Go to Configuration → Data Sources → Add data source
   - Select Loki
   - URL: `http://loki:3100`
   - Click "Save & Test"

4. **Explore logs:**
   - Go to Explore
   - Select Loki data source
   - Query examples:
     - `{container="debbly-api"}` - All API logs
     - `{container="debbly-livekit"}` - LiveKit logs
     - `{level="ERROR"}` - All error logs
     - `{service="debbly-api"} |= "exception"` - API exceptions

## Environment Variables

### Required for Debbly API:
- `DB_URL` - PostgreSQL connection string
- `DB_USERNAME` - Database username
- `DB_PASSWORD` - Database password
- `LIVEKIT_API_KEY` - API key for LiveKit authentication
- `LIVEKIT_API_SECRET` - API secret for LiveKit authentication
- `SUPABASE_URL` - Supabase project URL
- `SUPABASE_PUBLISHABLE_KEY` - Supabase publishable key
- `SUPABASE_SECRET_KEY` - Supabase secret key
- `OPENAI_API_KEY` - OpenAI API key
- `OPENAI_API_MODEL` - OpenAI model (default: gpt-4)

### Required for LiveKit/Egress (.env.local):
- `LIVEKIT_API_KEY` - API key (must match API config)
- `LIVEKIT_API_SECRET` - API secret (must match API config)
- `S3_ENDPOINT` - S3-compatible storage endpoint
- `S3_ACCESS_KEY` - S3 access key
- `S3_SECRET` - S3 secret key
- `S3_BUCKET` - S3 bucket name

## Building the API Image

To rebuild the API image after code changes:

```bash
cd local
docker-compose build debbly-api
docker-compose up -d debbly-api
```

Or rebuild everything:

```bash
docker-compose up -d --build
```

## Troubleshooting

### Check service health:
```bash
docker-compose ps
```

### View specific service logs:
```bash
docker-compose logs -f <service-name>
```

### Restart a service:
```bash
docker-compose restart <service-name>
```

### Clean up and start fresh:
```bash
docker-compose down -v  # Warning: removes all volumes
docker-compose up -d --build
```

## Network Architecture

All services run on a custom bridge network `debbly-network`, allowing them to communicate using service names:
- API connects to LiveKit via: `ws://livekit:7880`
- API connects to Redis via: `redis:6379`
- Prometheus scrapes metrics from: `livekit:6789`, `egress:9094`, `host.docker.internal:8080`
- Grafana connects to: `prometheus:9090`, `loki:3100`
- Promtail sends logs to: `loki:3100`