# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
Debbly is a live video debating platform built with Kotlin/Spring Boot.
The backend handles user authentication via self hosted Supabase Auth (GoTrue), debate matching, live debate room management through LiveKit, claim/stance tracking, and AI-powered content moderation.

## Development Commands

### Building and Running
- **Build**: `./mvnw compile` - Compiles the Kotlin source code
- **Run application**: `./mvnw spring-boot:run` - Starts the Spring Boot server on port 8080
- **Package**: `./mvnw package` - Creates executable JAR
- **Clean**: `./mvnw clean` - Removes target directory

### Testing
- **Run all tests**: `./mvnw test`
- **Run specific test**: `./mvnw test -Dtest=ClassName`
- **Test with profile**: Tests automatically use `test` profile. Integration tests spin up PostgreSQL and Redis via Testcontainers; unit tests use plain JUnit/Mockito.

### Database Management
- **Liquibase update**: `./mvnw liquibase:update` - Apply database migrations
- **Liquibase status**: `./mvnw liquibase:status` - Check migration status
- Database migrations are in `src/main/resources/db/changelog/`

## Architecture Overview

### Core Components
- **Matchmaking**: Redis-based queue system for matching users for debates
- **Live Rooms**: LiveKit integration for real-time video/audio communication
- **Claims System**: Users can take stances on debate claims with categories and tags
- **AI Integration**: OpenAI-powered content moderation, topic extraction, and semantic similarity via embeddings

### Key Modules
All modules are under `src/main/kotlin/com/debbly/server/`:
- **analytics/**: Usage and engagement analytics
- **auth/**: Supabase Auth (GoTrue), JWT configuration
- **category/**: Debate categories management
- **challenge/**: User-to-user debate challenges
- **chat/**: Live chat during debates with rate limiting
- **claim/**: Claims, stances, and topics (includes `claim/topic/` submodule)
- **config/**: Spring Boot configuration (database, caching, AI, etc.)
- **embedding/**: Vector embeddings storage and similarity search (pgvector)
- **event/**: Domain event definitions and publishing
- **followers/**: User follow relationships
- **home/**: Home feed and discovery
- **infra/**: Infrastructure utilities (error handling)
- **livekit/**: LiveKit room token generation, room management, and webhook handling
- **match/**: Matchmaking queue and match management
- **moderation/**: OpenAI-powered content moderation (claims, usernames, chat, etc.)
- **pusher/**: Real-time event messaging via Pusher
- **settings/**: User preferences and app settings
- **stage/**: Live debate room/stage management with caching
- **storage/**: S3 file storage (avatars, recordings)
- **user/**: User management with caching
- **util/**: Utility functions
- **viewer/**: Viewer session tracking for live stages

### Data Layer
- **PostgreSQL**: Primary database with Liquibase migrations
- **PostgreSQL + pgvector**: Separate database for vector embeddings (hosting limitation workaround - could be single DB)
- **Redis**: Matchmaking queues and caching layer
- **JPA/Hibernate**: ORM with custom repository pattern (JPA + Cached repositories)
- Migrations: `db/changelog/` (main) and `db/pgvector/changelog/` (embeddings)

### Configuration
- Uses Spring profiles: default for production, `test` for testing
- Configuration in `application.yml` and `application-test.yml`
- Key environment variables:
  - Database: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
  - pgvector: `PGVECTOR_DB_URL`, `PGVECTOR_DB_USERNAME`, `PGVECTOR_DB_PASSWORD`
  - LiveKit: `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`
  - OpenAI: `OPENAI_API_KEY`
  - Pusher: `PUSHER_APP_ID`, `PUSHER_KEY`, `PUSHER_SECRET`

### Security
- JWT-based authentication with self hosted Supabase Auth (GoTrue)
- CORS configured for localhost:3000 (frontend)
- Rate limiting on authentication endpoints
- Most endpoints are permitAll except logout

### AI Features
- **Claim moderation**: Validates claims, extracts neutral topics, determines stance (FOR/AGAINST)
- **Content moderation**: Username, avatar, bio, and chat message validation
- **Embeddings**: Generates vector embeddings for semantic similarity search between claims/topics
- **Topic extraction**: Automatically extracts debate topics from user-submitted claims

### Testing Strategy
- Integration tests use Testcontainers (PostgreSQL + Redis) via `AbstractIntegrationTest`
- Unit tests use JUnit 5 and Mockito (see `MockitoHelpers.kt`)
- Test files located in `src/test/kotlin/`
- Apply `@ActiveProfiles("test")` for integration tests; real Liquibase migrations run against the Postgres container