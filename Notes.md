# -- Notes --

## Design
This is a CRUD based Entity system. Entities all contain an ID, all entities must implement all the CRUD methods but not all of them need to expose that functionality through the controller. There are four layers to the system, they all follow this naming convention:
- Controller - (Entity)Controller.java - "SystemController.java"
- Gateway - (Entity)Gateway.java - "SystemGateway.java"
- Service - (Entity)Service.java - "SystemService.java"
- Repository - (Entity)Repository.java - "SystemRepository.java"

Each layer only calls the layers on the same level or below it.

## Domain Encapsulation
The domain is the core of the system, it could be compiled on its own or transplanted to another system if needed. The only way to access the domain is through the different Gateways. And the only things allowed to be exported from the domain are:
- The Gateway Classes
- Data Transfer Objects
- Exceptions
- The Keychain

## Return Body
The body of requests was inspired by the JSON API format. Responses will have "data" and "errors" attributes as part of the body. If the request was successful, then the errors will return with a null value. Otherwise, the data will be null, and the errors will display one or more messages. This will illustrate when the response was intentional or not. For example, a DELETE request will usually not return a body as the data will be blank, and the errors will be null, showing that the request was successful; there was just no data to return.

## Docker Flow
The project can be run by running the dockerfile this will spin up three containers.
- First the postgres database along with a volume to store the data in.
- Second a flyway container that will run the database migrations on the postgres container then stop.
- Third the api container that will run the Dockerfile found in this directory. 
  - When the program is run in docker it will load with the 'docker' profile "application-docker.properties"

## Test Plan / Flow
This project uses the diamond testing strategy, there are many integration tests that run tests from the controller, but there are parts of the program that needs more in-depth unit testing like custom fields and filters. The integration tests use test-containers to make sure that there is no cross containment between data for tests. The integration tests use the MockMvc library that comes included with Spring Boot library.

## Filter System
The system supports a powerful filtering mechanism for searching entities. All search endpoints use RPC-style calls: `POST /{resource}/function/search` with a filter array in the request body.

### Filter Types
- **Text**: String filtering with operators: equals, not_equals, contains, starts_with, ends_with
- **Number**: Numeric filtering with operators: equals, not_equals, greater_than, greater_than_equal_to, less_than, less_than_equal_to
- **Boolean**: Boolean filtering with operator: equals
- **Time**: Timestamp filtering with operators: since, before
- **System**: System ID filtering with operators: equals, not_equals (specifically for video games and video game boxes)
- **Sort**: Ordering results with operators: order_by, order_by_desc
- **Pagination**: Limiting results with operators: limit, offset

### System Filter
The system filter type is designed specifically for filtering video games and video game boxes by their associated system. Unlike the general number type, the system filter:
- Only supports `equals` and `not_equals` operators
- Provides semantic clarity that the field represents a system relationship
- Prevents inappropriate operations like greater_than/less_than on system IDs
- Returns clear validation error messages for invalid operators

**Example Usage:**
```json
{
  "filters": [
    {
      "entityKey": "videoGame",
      "type": "system",
      "field": "system_id",
      "operator": "equals",
      "operand": "1"
    }
  ]
}
```

**Available Fields:**
- Video Games: `system_id` (system filter type)
- Video Game Boxes: `system_id` (system filter type)

### Custom Fields
All entities support custom fields which can be filtered using their respective data types. Custom fields are dynamically defined and stored separately from the core entity data.

## Documentation
The design notes can be found with documentation style comments on the Entity, and System classes.
The requirements for each entity is listed on the integration tests for that entity. For example to find the requirements for the Video Game Box check the VideoGameBoxTests.java

# Multiplatform Deployment Steps

## One-time Setup
Create a builder that supports multiplatform builds:
```bash
docker buildx create --name multiplatform --use
docker buildx inspect --bootstrap
```

## Build and Deploy
1. Run the maven command to build the project:
   ```bash
   mvn install -DskipTests
   ```

2. Build and push the API image for multiple platforms:
   ```bash
   docker buildx build --platform linux/amd64,linux/arm64 \
     --build-arg JAR_FILE=target/the_game_pensieve_api.jar \
     -t sethcondie/the-game-pensive-api:latest \
     --push \
     .
   ```

3. Build and push the Flyway migration image for multiple platforms:
   ```bash
   docker buildx build --platform linux/amd64,linux/arm64 \
     -f Dockerfile.flyway \
     -t sethcondie/the-game-pensive-flyway:latest \
     --push \
     .
   ```

Note: The frontend image (sethcondie/the-game-pensieve-web:latest) should be built from the frontend repository using:
```bash
docker buildx build --platform linux/amd64,linux/arm64 \
  -t sethcondie/the-game-pensieve-web:latest \
  --push \
  .
```

## Running the Project
After these steps are complete, anyone can run the entire project with the production.yaml file:
```bash
docker compose -f compose.production.yaml up
```
The frontend will run on localhost:4200