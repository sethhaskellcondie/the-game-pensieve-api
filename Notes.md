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

## Documentation
The design notes can be found with documentation style comments on the Entity, and System classes.
The requirements for each entity is listed on the integration tests for that entity. For example to find the requirements for the Video Game Box check the VideoGameBoxTests.java

### Features and Roadmap
- Systems, Toys and basic CRUD design (done)
- Filters for All Entities (done)
- Custom Fields for All Entities (done)
- Video Games and Video Game Boxes (done)
- Board Games and Board Game Boxes (in progress)
- Import and Export backup data (in progress)
- External Swagger Documentation (backlog)
- Front End Implementation (backlog)