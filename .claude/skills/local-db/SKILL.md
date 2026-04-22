---
name: local-db
description: Run SQL against the local Debbly Postgres (the `debbly` main DB or `pgvector` embeddings DB) via `docker exec`. Use when the user wants to query/inspect the local database, run SELECTs or DML/DDL, list tables, describe schema, check Liquibase migration state, or compare actual schema against Liquibase changelogs. Examples: "how many users are in local db", "show me the claims table", "what's in databasechangelog", "add a column to X", "does local schema match liquibase".
---

# Local Database Access

Runs psql against the local Postgres container — no host `psql` needed.

## Databases

One container (`debbly-postgres`) hosts two databases:

| DB         | Purpose                                | Env vars used                                         |
|------------|----------------------------------------|-------------------------------------------------------|
| `debbly`   | Main app DB (all Liquibase migrations) | `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`               |
| `pgvector` | Embeddings / pgvector migrations       | `PGVECTOR_DB_NAME`, `PGVECTOR_DB_USERNAME`, `PGVECTOR_DB_PASSWORD` |

Creds live in `/Users/vladimir.prudnikov/Documents/hobby/local/server/local/.env`. Read that file to get current values — don't hardcode them in commands.

**If `pgvector` DB doesn't exist yet**, `psql` errors with `database "pgvector" does not exist`. It isn't auto-created by the compose file (only `DB_NAME` is). Offer to create it: `CREATE DATABASE pgvector OWNER debbly_user;` (run against `debbly` or `postgres` DB).

## Connection

Always go through `docker compose exec` — host psql is not installed.

```
docker compose -f /Users/vladimir.prudnikov/Documents/hobby/local/server/local/docker-compose.yml exec -T postgres \
  psql -U <user> -d <db> <args>
```

- `-T` disables TTY allocation — required for non-interactive output capture.
- For brevity below, `<EXEC>` stands for the full `docker compose -f …/local/docker-compose.yml exec -T postgres` prefix.
- Default target DB is `debbly` unless the user mentions embeddings / vectors / pgvector.
- Precondition: postgres container must be running. If `docker compose ... ps` shows it stopped, tell the user and suggest `/local-env start`.

## Safety

Classify every statement before running:

| Class              | Examples                                                    | Behavior                          |
|--------------------|-------------------------------------------------------------|-----------------------------------|
| **Read-only**      | `SELECT`, `EXPLAIN`, `SHOW`, `\d`, `\dt`, `\df`, `\l`, `\x` | Run directly                      |
| **DML**            | `INSERT`, `UPDATE`, `DELETE`, `MERGE`, `COPY ... FROM`      | Show query, ask confirmation      |
| **DDL**            | `CREATE`, `ALTER`, `DROP`, `TRUNCATE`, `GRANT`, `REVOKE`    | Show query, ask confirmation      |
| **Catastrophic**   | `DROP DATABASE`, `DROP SCHEMA public`, `TRUNCATE` w/o WHERE | Show query, ask explicit confirmation and warn about data loss |

Always print the exact query you're about to run before running it. For multi-statement scripts, classify by the most destructive statement in the batch.

Never bypass the confirmation gate even if the user said "just do it" earlier in the session — authorization is per-query, not per-session, unless the user says so explicitly for this task.

## Running queries

### One-shot query
```
<EXEC> psql -U debbly_user -d debbly -c "SELECT count(*) FROM users;"
```

### Multi-line / complex SQL
Use a heredoc piped in — cleaner than escaping quotes:
```
<EXEC> psql -U debbly_user -d debbly <<'SQL'
SELECT u.id, u.username, count(c.id) AS claims
FROM users u LEFT JOIN claims c ON c.user_id = u.id
GROUP BY u.id, u.username
ORDER BY claims DESC
LIMIT 20;
SQL
```

### Useful flags
- `-P pager=off` — disable pager (already default under docker exec, but explicit is fine)
- `-x` / `\x` — expanded display for wide rows (one column per line). Use when the row is wider than the terminal.
- `-A -F ','` — unaligned CSV-ish output, handy for piping to other tools
- `--csv` — proper CSV
- `-q` — quiet (skip "(N rows)" footer)

Default: plain aligned table output with column headers. Switch to `\x` automatically if a row has many columns or long text fields.

### Large result sets
When you don't know the row count, inject `LIMIT 50` or wrap with `SELECT count(*)` first. Don't dump thousands of rows into the conversation.

## Schema introspection

### List tables in current DB
```
<EXEC> psql -U debbly_user -d debbly -c "\dt"
```
(or `\dt+` for sizes, `\dt <schema>.*` to filter)

### Describe a table
```
<EXEC> psql -U debbly_user -d debbly -c "\d+ users"
```

### List schemas / views / functions / indexes
- `\dn` schemas, `\dv` views, `\df` functions, `\di` indexes

### Find a table/column by name
```
<EXEC> psql -U debbly_user -d debbly -c "
SELECT table_schema, table_name, column_name, data_type
FROM information_schema.columns
WHERE column_name ILIKE '%stance%'
ORDER BY table_schema, table_name;"
```

## Liquibase comparison workflow

Liquibase tracks applied changesets in the `databasechangelog` table. Both DBs have their own copy.

| DB         | Changelog dir                          |
|------------|----------------------------------------|
| `debbly`   | `src/main/resources/db/changelog/`     |
| `pgvector` | `src/main/resources/db/pgvector/changelog/` |

### What's applied locally
```
<EXEC> psql -U debbly_user -d debbly -c "
SELECT id, author, filename, dateexecuted, orderexecuted, exectype
FROM databasechangelog
ORDER BY orderexecuted DESC
LIMIT 20;"
```

### Compare applied vs. changelog files
1. Query `databasechangelog` — get the set of applied `(id, author, filename)` triples.
2. Read the corresponding changelog XML/YAML/SQL under `src/main/resources/db/changelog/` (or `pgvector/changelog/`).
3. Parse changeset IDs from the files (look for `<changeSet id="..." author="...">` in XML, or `- changeSet: { id: ..., author: ... }` in YAML).
4. Diff:
   - **In files but not applied** → migrations pending (user probably needs `./mvnw liquibase:update`).
   - **Applied but not in files** → orphaned/rolled-back or changelog was edited (bigger red flag — surface it clearly).
5. For a specific changeset: check its hash in `databasechangelog.md5sum` vs. recomputing from the file — but Liquibase usually screams on startup if hashes mismatch, so only bother if the user asks.

### Schema drift vs. changelog
- Dump actual table structure (`\d+`), compare against the `CREATE TABLE` in the changelog.
- Common drift sources: manual `ALTER` run via this skill (flag it!), `liquibase-rollback` not reversing everything, or tests leaving state behind.
- For a quick sanity check: `./mvnw liquibase:status` — reports pending changesets. Offer this as a fast path before running manual comparisons.

## Exec modes

- **Single SQL**: `<EXEC> psql -U <user> -d <db> -c "<sql>"` — best for short queries.
- **SQL file**: pipe via stdin: `cat file.sql | <EXEC> psql -U <user> -d <db>` (use `-T` as shown above).
- **Interactive shell**: tell the user to run `docker compose -f .../docker-compose.yml exec postgres psql -U <user> -d <db>` themselves (with `!` prefix in Claude Code). Claude can't drive an interactive session from within a tool call.

## Notes

- `.env` values read from `local/.env`. If the file is missing, template lives at `local/.env.example`.
- Current working users: `debbly_user` with password `debbly_password` (local only — not a secret).
- Don't run `VACUUM FULL`, `CLUSTER`, `REINDEX DATABASE`, or anything that takes global locks without confirming — even on local, it can hang the dev server.
- Timezone: the container runs UTC. Timestamps in query output are UTC unless cast.
- `COPY ... FROM '/path'` runs inside the container filesystem, not the host. Use `\copy` (psql meta-command) to read from the host instead, or mount a volume.