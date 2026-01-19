# Repository Guidelines

## Project Structure & Module Organization
- `src/main/kotlin/com/debbly/server/` contains the Spring Boot application code with these modules:
  - `ai/` - OpenAI integration (moderation, embeddings, topic extraction)
  - `auth/` - Supabase Auth (GoTrue), JWT
  - `category/` - Debate categories
  - `chat/` - Live chat with rate limiting
  - `claim/` - Claims, stances, topics (includes `claim/topic/` submodule)
  - `config/` - Spring Boot configuration
  - `embedding/` - pgvector embeddings and similarity search
  - `followers/` - User follow relationships
  - `infra/` - Error handling utilities
  - `livekit/` - LiveKit video/audio integration
  - `match/` - Matchmaking queue
  - `pusher/` - Real-time events via Pusher
  - `settings/` - User/app settings
  - `stage/` - Debate room management
  - `storage/` - S3 file storage
  - `user/` - User management
  - `util/` - Utilities
- `src/main/resources/` holds configuration and Liquibase migrations (`db/changelog/` for main DB, `db/pgvector/changelog/` for embeddings DB)
- `src/test/kotlin/` contains tests; test configuration lives in `src/test/resources/application-test.yml`
- `local/` provides Docker Compose and local infra configs
- `target/` is Maven build output (generated)

## Build, Test, and Development Commands
- `./mvnw compile` builds Kotlin sources.
- `./mvnw spring-boot:run` runs the API on port 8080.
- `./mvnw test` runs the full test suite.
- `./mvnw test -Dtest=ClassName` runs a specific test class.
- `./mvnw package` creates the executable JAR.
- `./mvnw clean` removes build output in `target/`.
- `./mvnw liquibase:update` applies DB migrations; `./mvnw liquibase:status` checks migration state.
- Local infra: `cd local && docker-compose up -d` (see `local/README.md`).

## Coding Style & Naming Conventions
- Follow existing Kotlin/Spring Boot conventions in `src/main/kotlin/com/debbly/server/`.
- Use 4-space indentation and keep files aligned with current formatting.
- Naming: classes in PascalCase, functions/variables in camelCase, packages in lowercase.
- No formatter or linter is configured; keep style consistent with nearby code.

## Testing Guidelines
- Frameworks: JUnit 5 and Spring Boot Test with H2 for tests.
- Tests live in `src/test/kotlin/` and use the `test` profile by default.
- Use `@ActiveProfiles("test")` for integration-style tests.
- Prefer `*Test` naming for test classes.

## Commit & Pull Request Guidelines
- Commit messages are short, descriptive, and imperative (e.g., `fix issue with switch stance`).
- PRs should include: a concise summary, test results (or reason for no tests), and any config or migration changes.
- If schema changes are involved, update the Liquibase files in `src/main/resources/db/changelog/`.

## Security & Configuration Tips
- Required env vars: DB (`DB_*`), pgvector (`PGVECTOR_DB_*`), LiveKit, Redis, OpenAI (`OPENAI_API_KEY`), Pusher, and storage keys
- Note: Two separate PostgreSQL databases are used (main + pgvector) due to hosting limitations - architecturally they could be one DB
- Avoid committing secrets; use environment variables for local runs
- See `local/README.md` and `application.yml` for defaults
