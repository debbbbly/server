---
name: local-env
description: Manage the Debbly local Docker environment (postgres, redis, livekit, egress) and inspect container logs. Use when the user wants to start/stop the local stack, check container status, or view logs for any of the services (e.g. "start local env", "stop containers", "show livekit logs", "tail egress logs", "are my containers up?").
---

# Local Environment Management

Controls the Docker Compose stack defined at `local/docker-compose.yml` (project name: `debbly`).

## Services

| Service    | Container name     | Purpose                                    |
|------------|--------------------|--------------------------------------------|
| `postgres` | `debbly-postgres`  | Primary DB (pgvector/pg17) on :5432        |
| `redis`    | `debbly-redis`     | Cache + matchmaking queues on :6379        |
| `livekit`  | `debbly-livekit`   | Real-time video/audio server               |
| `egress`   | `debbly-egress`    | Records debate sessions (host networking)  |

## Running compose commands

**Always use `-f <absolute-path>` — do not `cd` into `local/`:**

```
docker compose -f /Users/vladimir.prudnikov/Documents/hobby/local/server/local/docker-compose.yml <cmd>
```

This is more reliable than changing directories and avoids any surprises with cwd-sensitive tool calls. Prefer `docker compose` (v2) over `docker-compose`.

For brevity below, `<COMPOSE>` stands in for the full `docker compose -f …/local/docker-compose.yml` prefix.

## Instructions

Parse the user's request and map it to one of the actions below.

### Start the stack
- Full stack: `<COMPOSE> up -d`
- Single service: `<COMPOSE> up -d <service>`
- Rebuild + start: `<COMPOSE> up -d --build`
- Clean up stray orphan containers while starting: `<COMPOSE> up -d --remove-orphans`

If you see a warning like `Found orphan containers ([debbly-egress2])`, that's a stale container from a service that's no longer in the compose file. Mention it to the user and offer `--remove-orphans` rather than silently ignoring.

After starting, run `<COMPOSE> ps` and report which services are running/healthy.

### Stop the stack
- Stop (keep volumes): `<COMPOSE> stop`
- Stop + remove containers: `<COMPOSE> down`
- Nuke volumes too (DESTRUCTIVE — confirm with user first): `<COMPOSE> down -v`

Never run `down -v` without explicit user confirmation — it wipes the postgres and redis data volumes.

### Status / health check
`<COMPOSE> ps` shows state + port bindings for each service. For "is everything up?", summarize: expect all four of `postgres/redis/livekit/egress` in `Up` state, with `postgres` showing `(healthy)`. Flag anything else.

### View logs

**LiveKit logs are a landmine.** LiveKit periodically dumps full goroutine stacktraces into a single log line, so even `--tail=5` can return ~50KB of output and blow up the context window. Always filter LiveKit logs through a grep that keeps only timestamped log lines:

```
<COMPOSE> logs --tail=200 livekit 2>&1 | grep -E 'INFO|WARN|ERROR|DEBUG' | grep -v 'goroutine' | tail -30
```

Or use `--since` to time-bound the output (counts of log lines in the last 30 min: postgres ~180, redis ~30, egress 0 when idle, livekit ~2):

```
<COMPOSE> logs --since=30m livekit 2>&1 | grep -E 'INFO|WARN|ERROR|DEBUG'
```

For the other services, plain `--tail` is safe:

- `<COMPOSE> logs --tail=100 postgres`
- `<COMPOSE> logs --tail=100 redis`
- `<COMPOSE> logs --tail=100 egress`

**Follow live:** run with `run_in_background: true` so the user can keep working; use Monitor/BashOutput to stream. Example: `<COMPOSE> logs -f --tail=50 egress`.

**All services combined:** `<COMPOSE> logs --tail=30` — but exclude livekit or grep-filter it as above to avoid the stacktrace flood.

**Common LiveKit warnings to recognize (not bugs):**
- `invalid API key: test-key` on `/twirp/livekit.Egress/ListEgress` — periodic poll from a client with stale creds, safe to ignore unless the user is debugging auth.
- `status update delayed, possible deadlock` in `redisrouter.go` — this is the trigger for the goroutine dump; usually transient.

### Restart a single service
`<COMPOSE> restart <service>` — useful when config files (`livekit.yaml`, `egress.yaml`) change.

### Exec into a container
- Postgres shell: `<COMPOSE> exec postgres psql -U "$DB_USERNAME" -d "$DB_NAME"` (read env from `local/.env`)
- Redis CLI: `<COMPOSE> exec redis redis-cli`
- Generic shell: `<COMPOSE> exec <service> sh`

## Notes

- Compose reads `local/.env` for `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, and livekit/egress keys. If a command fails with missing variables, check that `local/.env` exists (template at `local/.env.example`).
- `egress` uses `network_mode: host`, so on macOS it behaves differently than the others — it shares the host's network namespace instead of the `debbly-network` bridge and won't show port bindings in `ps`.
- Don't start Spring Boot (`./mvnw spring-boot:run`) as part of this skill — scope is the Docker stack only.
