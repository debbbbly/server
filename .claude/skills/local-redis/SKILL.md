---
name: local-redis
description: Inspect and query the local Debbly Redis (cache + matchmaking + chat state) via `docker exec redis-cli`. Use when the user wants to see what's in Redis, look up chat messages, match/queue state, online users, Spring `@Cacheable` entries, or run ad-hoc redis-cli commands. Examples: "what chat messages are in redis", "show matches in cache", "list all redis keys", "who's in the matchmaking queue", "is user X online", "clear the user cache".
---

# Local Redis Access

Runs `redis-cli` against `debbly-redis` via `docker compose exec` — no host redis-cli needed.

## Connection

```
docker compose -f /Users/vladimir.prudnikov/Documents/hobby/local/server/local/docker-compose.yml exec -T redis redis-cli <cmd>
```

- `-T` disables TTY allocation — required for non-interactive output.
- No password is configured locally.
- For brevity below, `<CLI>` stands for the full `docker compose -f …/local/docker-compose.yml exec -T redis redis-cli` prefix.
- Precondition: redis container running. If `ps` shows it stopped, suggest `/local-env start`.

## Key map (what lives where)

Redis is used by several modules. Keep this table in mind when the user asks "what's in redis":

| Domain          | Pattern                                 | Type    | Notes                                                        |
|-----------------|-----------------------------------------|---------|--------------------------------------------------------------|
| Chat messages   | `chat:{chatId}:messages`                | ZSET    | score = timestamp ms; trimmed to last 10; TTL 15 min         |
| Chat mutes      | `chat:{chatId}:muted`                   | SET     | members = userIds                                            |
| Matches         | `match:{matchId}`                       | STRING  | JSON `Match`                                                 |
| User → match    | `user_match:{userId}`                   | STRING  | value = matchId                                              |
| All matches     | `matches:all`                           | SET     | members = `match:{id}` keys                                  |
| Match queue     | `user_match_request:{userId}` *(approx)* | STRING | see `MatchQueueRepository`                                   |
| Match queue set | `user_match_request_keys` *(approx)*    | SET     | index of all queue request keys                              |
| Online users    | `online_users` *(approx)*               | ZSET    | score = last-seen epoch; see `OnlineUsersService`            |
| Spring cache    | `<cacheName>::<key>`                    | STRING  | Jackson JSON, default TTL 10 min (see `RedisConfig.kt`)      |

**Spring `@Cacheable` caches in use** (names map to key prefix `<name>::...`):
- `users`, `usersByExternalId`, `usersByUsername`, `topUsers`
- `userFollowing`, `userFollowers`, `userFollowingCount`, `userFollowersCount`
- `userSettings`, `userSettingsByUser`
- `categoriesByCategoryId`, `allCategories`
- `socialUsernames`

Source-of-truth files — check these when the user asks about a domain you haven't seen before, or if the patterns above look stale:
- `src/main/kotlin/com/debbly/server/chat/repository/ChatRepository.kt` — chat
- `src/main/kotlin/com/debbly/server/match/repository/MatchRepository.kt` — matches
- `src/main/kotlin/com/debbly/server/match/repository/MatchQueueRepository.kt` — matchmaking queue
- `src/main/kotlin/com/debbly/server/user/OnlineUsersService.kt` — online presence
- `src/main/kotlin/com/debbly/server/config/RedisConfig.kt` — serializers, TTL
- `src/main/kotlin/com/debbly/server/**/Cached*Repository.kt` — `@Cacheable` names

## Safety

Classify every command:

| Class            | Examples                                       | Behavior                            |
|------------------|------------------------------------------------|-------------------------------------|
| **Read-only**    | `GET`, `LRANGE`, `ZRANGE`, `SMEMBERS`, `TYPE`, `TTL`, `KEYS`, `SCAN`, `DBSIZE`, `INFO`, `OBJECT` | Run directly                        |
| **Mutation**     | `SET`, `DEL`, `ZADD`, `SADD`, `SREM`, `EXPIRE`, `RENAME`, `HSET`, `LPUSH`, etc.                  | Show command, ask confirmation      |
| **Catastrophic** | `FLUSHDB`, `FLUSHALL`, `DEL` on wildcard-derived key lists, `SCRIPT FLUSH`, `CONFIG SET`         | Confirm explicitly with data-loss warning |

Print the exact command before running it. For pipelines / scripts, classify by the most destructive op.

**`KEYS *` on prod is forbidden, but local is fine** — the DB is tiny. Still prefer `SCAN` in examples you hand the user for muscle memory.

## Common queries

### Overview — how full is redis
```
<CLI> DBSIZE
<CLI> INFO keyspace
```

### List all keys (safe locally)
```
<CLI> --scan
<CLI> --scan --pattern 'chat:*'
<CLI> --scan --pattern 'match:*'
<CLI> --scan --pattern '*::*'   # Spring cache entries
```

### Inspect a single key
```
<CLI> TYPE <key>
<CLI> TTL  <key>
<CLI> OBJECT ENCODING <key>
```
Then dispatch on the type:
- STRING → `GET <key>` (most app values are JSON — pipe through `python3 -m json.tool` if useful)
- SET    → `SMEMBERS <key>`
- ZSET   → `ZRANGE <key> 0 -1 WITHSCORES` (or `ZREVRANGE` for latest-first)
- HASH   → `HGETALL <key>`
- LIST   → `LRANGE <key> 0 -1`
- STREAM → `XRANGE <key> - +`

### Chat messages for a room
```
<CLI> ZREVRANGE "chat:{chatId}:messages" 0 9 WITHSCORES
```
Each member is a JSON `ChatMessage`. Pretty-print:
```
<CLI> ZREVRANGE "chat:{chatId}:messages" 0 9 | python3 -c 'import sys,json;[print(json.dumps(json.loads(l),indent=2)) for l in sys.stdin if l.strip()]'
```

### Who's muted in a chat
```
<CLI> SMEMBERS "chat:{chatId}:muted"
```

### All current matches
```
<CLI> SMEMBERS matches:all
# then for each key:
<CLI> GET "match:{matchId}"
```
Or in one shot (shell):
```
<CLI> SMEMBERS matches:all | while read k; do echo "== $k =="; <CLI> GET "$k"; done
```

### Who's in the matchmaking queue
Look up the index set from `MatchQueueRepository` — the exact name may be `user_match_request_keys` or similar; confirm via the repo file if needed.
```
<CLI> --scan --pattern 'user_match_request:*'
```

### Is a user online
```
<CLI> ZSCORE online_users <userId>
<CLI> ZRANGE online_users 0 -1 WITHSCORES
```

### Peek at a Spring `@Cacheable` entry
Spring Data Redis writes keys as `<cacheName>::<key>`:
```
<CLI> --scan --pattern 'users::*'
<CLI> GET 'users::<userId>'
<CLI> TTL 'users::<userId>'
```
Values are Jackson JSON with type info (default typing is on — expect a JSON array of `[className, payload]` for polymorphic values).

### Count keys by prefix
```
<CLI> --scan --pattern 'match:*' | wc -l
```

## Mutations (ask first)

### Delete a specific key
```
<CLI> DEL <key>
```

### Clear a cache namespace
```
<CLI> --scan --pattern 'users::*' | xargs -r <CLI> DEL
```
(⚠️ xargs-to-DEL on a pattern is a common footgun — confirm the scan output before piping to DEL.)

### Nuke everything in the local DB (CATASTROPHIC)
```
<CLI> FLUSHDB
```
Requires an explicit "yes, flush it" from the user. Suggest `<CLI> DBSIZE` beforehand so they know what they're losing.

### Expire / change TTL
```
<CLI> EXPIRE <key> <seconds>
<CLI> PERSIST <key>   # remove TTL
```

## Tips

- **Unaligned output** for scripting: most redis-cli commands already emit one-per-line; add `--no-raw` if you need escaped strings, `--raw` to suppress quoting.
- **Large result sets**: `--scan` streams; for `ZRANGE`/`LRANGE` prefer bounded indices like `0 49` instead of `0 -1` when a key could be huge.
- **JSON payloads**: Redis values for matches/chat are stringified JSON — pretty-print with `python3 -m json.tool` or `jq` (jq is not installed on the host by default; python3 is).
- **Interactive shell**: if the user wants to poke around by hand, tell them to run `docker compose -f .../docker-compose.yml exec redis redis-cli` themselves (via `!` prefix). Claude can't drive an interactive REPL from a tool call.
- **Persistence**: the compose file uses `--appendonly yes` with a `redis_data` volume, so keys survive `docker compose stop`/`start`. `down -v` wipes them.
- **DB index**: everything lives in DB 0 unless the code says otherwise. None of the repos above set a non-default DB.
