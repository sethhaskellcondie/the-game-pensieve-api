# Notes

## Design
This is a CRUD based Entity system. Entities all contain an ID, all entities must implement all the CRUD methods but not all of them need to expose that functionality through the controller. There are four layers to the system:
- Controller - <Entity>Controller.java "SystemController.java"
- Gateway - <Entity>Gateway.java "SystemGateway.java"
- Service - <Entity>Service.java "SystemService.java"
- Repository - <Entity>Repository.java "SystemRepository.java"

Each layer only calls the layers on the same level or below it. For more information on the responsibilities of each layer check the Entity and System classes for each layer.

## Docker Flow
The project can be run by running the dockerfile this will spin up three containers.
- First the postgres database along with a volume to store the data in.
- Second a flyway container that will run the database migrations on the postgres container then stop.
- Third the api container that will run the Dockerfile found in this directory. 
  - When the program is run in docker it will load with the 'docker' profile "application-docker.properties"

## Test Plan / Flow
Integration tests with the database use Testcontainers, they are called "RepositoryTests" they use the file format <Entity>RepositoryTests. They test the connection with the database and the hydration of objects.
Entity Tests using the file format "<Entity>EntityTests" test the validation of objects as they are created making sure that multiple errors are thrown when multiple issues are found with an object.
Controller tests using the file format "<Entity>WebTests" test the serialization and deserialization of request and response bodies along with the proper status codes.
There is also a suite of unit tests for each layer making sure that the base functionality and added functionality work as intended.

Repository Tests use test containers to spin up a database to run tests on. They run a @JdbcTest that would usually run an in memory database, we can change that to a test container with a different @ActiveProfile "test-container."
This profile has two lines to tell Spring to not spin up an in memory database spring.test.database.replace=none and then another line that changes the data source to a test container. With this setup we can autowire the jdbcTemplate and 
use that to create an instance of a repository to run tests on.

Currently the tests will spin up a new docker container and reinitialize the repository between each test, if this were implemented in a larger system steps would need to be taken to reuse the docker containers.