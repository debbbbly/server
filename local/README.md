# Debbly Local Development

There are very few steps you have to do to run, test and develop Debbly locally.

### 1. Start infrastructure Docker containers

Go to `/local` folder and start Docker containers for Redis, Postgres, LiveKit, Egress, and MinIO:

```bash
cd local
docker-compose up -d
```

This will start:

- **Postgres** - Database on port 5432
- **Redis** - Cache and queue on port 6379
- **LiveKit** - Real-time video/audio server on port 7880
- **LiveKit Egress** - Recording service on port 9094

### 1.1 Verify all services are running

```bash
docker-compose ps
```

All services should show status "Up" or "Up (healthy)". If any service is restarting or exited, check the logs:

```bash
docker-compose logs [service-name]
```

### Postgres

**Option 1: Using docker exec**

```bash
docker exec -it debbly-postgres psql -U debbly_user -d debbly
```

**Option 2: Using connection string**

Connect to the Postgres database (empty initially) using:

```
postgresql://debbly_user:debbly_password@localhost:5432/debbly
```

### Redis

**Test with docker exec:**

```bash
docker exec -it debbly-redis redis-cli ping
```

Expected output: `PONG`

**If you have redis-cli installed locally:**

```bash
redis-cli -h localhost -p 6379
```

### LiveKit

Test the LiveKit server by opening in your browser:

```
http://localhost:7880
```

You should get `OK` back, indicating the LiveKit server is running properly.

## 2. Set Environment Variables

In IntelliJ IDEA go to Run > Edit Configurations and set Environment variables:

```
AUTH_JWT_SECRET=
AUTH_PUBLIC_URL=

DB_NAME=debbly
DB_PASSWORD=debbly_password
DB_SCHEMA=public
DB_URL=jdbc:postgresql://localhost:5432/debbly
DB_USER=debbly_user
DB_USERNAME=debbly_user

LIVEKIT_API_KEY=livekit-api-key-for-local-development
LIVEKIT_API_SECRET=livekit-api-secret-for-local-development
LIVEKIT_API_URL=http://localhost:7880
LIVEKIT_WS_URL=ws://localhost:7880

OPENAI_API_KEY=
OPENAI_API_MODEL=gpt-5-nano

PUSHER_APP_ID=2085654
PUSHER_CLUSTER=mt1
PUSHER_KEY=
PUSHER_SECRET=

REDIS_HOST=localhost

S3_DEFAULT_ACCESS_KEY=
S3_DEFAULT_BUCKET_USERS=
S3_DEFAULT_ENDPOINT=https://s3.us-east-1.amazonaws.com
S3_DEFAULT_REGION=us-east-1
S3_DEFAULT_SECRET=

S3_LK_ACCESS_KEY=
S3_LK_BUCKET_EGRESS=
S3_LK_ENDPOINT=https://s3.us-east-1.amazonaws.com
S3_LK_REGION=us-east-1
S3_LK_SECRET=
```

# 3. Start the service 