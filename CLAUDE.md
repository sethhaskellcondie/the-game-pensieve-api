# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Building and Running
- Build jar file: `mvn install -DskipTests`
- Run with Docker: `docker compose up` (starts API, database, and Flyway migrations)
- Run locally: Start PostgreSQL, then run via IDE or `java -jar target/the_game_pensieve_api.jar`
- Test endpoint: `localhost:8080/v1/heartbeat` should return "thump thump"

### Testing
- Run all tests: `mvn test`
- Run single test class: `mvn test -Dtest=ClassName`
- Run single test method: `mvn test -Dtest=ClassName#methodName`
- Note: If testcontainers fail due to resource constraints, comment out `GetWithFilters...Tests.java` files
- Tests use testcontainers for integration testing with PostgreSQL
- Integration tests follow diamond testing strategy with MockMvc

### Database
- Database migrations are managed by Flyway in `/src/main/resources/migrations/`
- Test profiles use separate application properties files (e.g., `application-test-container.properties`)
- Local database: PostgreSQL with username `postgres`, password `root`, database `pensieve-db`
- Docker setup includes database, API, and Flyway migration containers

## Architecture Overview

### Entity-Based Design
This is a CRUD-based entity system with a strict 4-layer architecture:

1. **Controller Layer**: `(Entity)Controller.java` - REST/RPC endpoints
2. **Gateway Layer**: `(Entity)Gateway.java` - Domain boundary, converts between DTOs and entities
3. **Service Layer**: `(Entity)Service.java` - Business logic
4. **Repository Layer**: `(Entity)Repository.java` - Database access via JDBC Template

Each layer only calls same-level or lower layers. All entities extend `Entity<RequestDto, ResponseDto>` abstract class.

### Domain Encapsulation
The domain is completely self-contained and could be transplanted to another system. Only these items are exported:
- Gateway classes
- Data Transfer Objects (DTOs)
- Exceptions
- The Keychain

### API Design
Hybrid REST/RPC design:
- Standard CRUD operations use REST patterns
- Search operations use RPC: `POST /{resource}/function/search` with filter array
- Response format follows JSON API inspiration with `data` and `errors` attributes

### Key Components

#### Keychain (`/src/main/java/com/sethhaskellcondie/thegamepensieveapi/domain/Keychain.java`)
Central registry of all entity keys. Each entity has a unique key used for:
- Filter operations
- Custom field associations
- Table alias mapping for SQL generation

Current entities: `system`, `toy`, `videoGame`, `videoGameBox`, `boardGame`, `boardGameBox`
- Each entity follows strict naming convention: singular, camelCase keys
- Table aliases are defined in Keychain for SQL generation (e.g., `systems`, `toys`, `video_games`)

#### Custom Fields System
Flexible metadata system allowing arbitrary fields on any entity:
- Custom fields are defined per entity type
- Values are stored separately and joined during queries
- Supported types: text, number, boolean, timestamp

#### Filter System
Powerful filtering with operators like equals, not equals, contains, greater than, etc.
- Filters work on entity fields and custom fields
- Multiple filters can be combined
- SQL generation uses table aliases from Keychain

#### Backup Import System
Comprehensive import functionality for collection data:
- Imports entity data with preserved relationships and custom field values
- Uses response DTOs (with database IDs) rather than request DTOs for import format
- Maintains referential integrity during import process
- Located in `/src/main/java/com/sethhaskellcondie/thegamepensieveapi/domain/backupimport/`

### Testing Patterns

#### TestFactory (`/src/test/java/com/sethhaskellcondie/thegamepensieveapi/TestFactory.java`)
Central factory for creating test data and validation. Provides:
- Helper methods for creating entities via HTTP endpoints
- JSON payload formatting
- Response validation utilities
- Random data generation for tests

#### Test Organization
- Integration tests are in `/src/test/java/.../controllers/`
- Unit tests for complex logic in `/src/test/java/.../domain/`
- Filter tests are extensively covered in dedicated test classes
- Tests use Spring Boot's `@SpringBootTest` with testcontainers

### Database Schema
- PostgreSQL 16 with JDBC Template (no JPA/Hibernate)
- All entities have: `id`, `created_at`, `updated_at`, `deleted_at`
- Custom fields stored in separate tables with foreign key relationships
- Migrations use Flyway with numbered versioning

## Code Conventions
- Java 17 with Spring Boot 3.1.4
- Package structure follows domain-driven design
- Strict separation between layers
- All entities implement common CRUD patterns
- Extensive use of records for DTOs
- Exception handling through custom exception hierarchy
- Maven wrapper available (`./mvnw`) but Maven can be used directly
- Use `./mvnw` instead of `mvn` if Maven is not installed locally
- No JPA/Hibernate - uses JDBC Template for database access
- Custom field aliases: `fields` table, `values` table for custom field values