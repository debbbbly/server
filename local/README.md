# Debbly Local Development

There are very few steps you have to do to run, test and develop Debbly locally.

## Backend

### 1. Check out backend (server)

```bash
git clone git@github.com:debbbbly/server.git
```

### 2. Start Docker containers

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
- **MinIO** - S3-compatible storage on ports 9000 (API) and 9001 (console)

### 3. Verify all services are running

```bash
docker-compose ps
```

All services should show status "Up" or "Up (healthy)". If any service is restarting or exited, check the logs:

```bash
docker-compose logs [service-name]
```

## Quick Tests

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

### MinIO

Open your browser and navigate to:

```
http://localhost:9001
```

Login with credentials from `.env`:
- **Username:** `debbly_user`
- **Password:** `debbly_password`

**Important:** Create an `egress` bucket in the MinIO UI after first login. This bucket is required for LiveKit Egress to store debate recordings.

### LiveKit

Test the LiveKit server by opening in your browser:

```
http://localhost:7880
```

You should get `OK` back, indicating the LiveKit server is running properly.

## Environment Variables

All configuration is stored in the `.env` file in the `/local` directory. Default development credentials:

- **Database:** `debbly_user` / `debbly_password` / `debbly`
- **MinIO:** `debbly_user` / `debbly_password`
- **LiveKit API:** See `.env` file for keys

## Stopping Services

To stop all services:

```bash
docker-compose down
```

To stop and remove all volumes (WARNING: this deletes all data):

```bash
docker-compose down -v
```
