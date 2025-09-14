# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
Debbly is a live video debating platform built with Kotlin/Spring Boot. The backend handles user authentication via AWS Cognito, debate matching, live debate room management through LiveKit, and claim/stance tracking.

## Development Commands

### Building and Running
- **Build**: `./mvnw compile` - Compiles the Kotlin source code
- **Run application**: `./mvnw spring-boot:run` - Starts the Spring Boot server on port 8080
- **Package**: `./mvnw package` - Creates executable JAR
- **Clean**: `./mvnw clean` - Removes target directory

### Testing
- **Run all tests**: `./mvnw test`
- **Run specific test**: `./mvnw test -Dtest=ClassName`
- **Test with profile**: Tests automatically use `test` profile with H2 database

### Database Management
- **Liquibase update**: `./mvnw liquibase:update` - Apply database migrations
- **Liquibase status**: `./mvnw liquibase:status` - Check migration status
- Database migrations are in `src/main/resources/db/changelog/`

## Architecture Overview

### Core Components
- **Authentication**: AWS Cognito integration with JWT tokens, supports both header and cookie-based auth
- **Matchmaking**: Redis-based queue system for matching users for debates
- **Live Rooms**: LiveKit integration for real-time video/audio communication
- **Claims System**: Users can take stances on debate claims with categories and tags

### Key Modules
- **auth/**: Cognito authentication, JWT configuration, rate limiting
- **backstage/**: Matchmaking queue and match management  
- **stage/**: Live debate room/stage management with caching layer
- **claim/**: Debate claims, categories, tags, and user stances
- **user/**: User management with caching
- **livekit/**: LiveKit room token generation and management

### Data Layer
- **PostgreSQL**: Primary database with Liquibase migrations
- **Redis**: Matchmaking queues and caching layer
- **JPA/Hibernate**: ORM with custom repository pattern (JPA + Cached repositories)

### Configuration
- Uses Spring profiles: default for production, `test` for testing
- Environment variables required: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`
- Configuration in `application.yml` and `application-test.yml`

### Security
- JWT-based authentication with AWS Cognito
- CORS configured for localhost:3000 (frontend)
- Rate limiting on authentication endpoints
- Most endpoints are permitAll except logout

### Testing Strategy
- Uses H2 in-memory database for tests
- JUnit 5 with Spring Boot Test
- Test files located in `src/test/kotlin/`
- Apply `@ActiveProfiles("test")` for integration tests