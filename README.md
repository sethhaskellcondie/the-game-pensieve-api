# the-game-pensive-api
A Pensive in the Harry Potter series is a basin where wizards can store thoughts and memories outside themselves.
This project is like a pensive but just for information on games in a collection.

## API Design
This api is a combination of REST and RPC endpoint design. All the CRUD endpoints are implemented with a rest design.
The RPC endpoints all contain `/function/` in the path the most common is the Get All endpoint 
usually in a REST API GetAll() is `GET` `/{resource}` but in this system call the RPC endpoint `POST` `/{resource}/function/search`
passing in an array of filter objects for that resource, if no filters are passed in then all resources are returned.

## Tech Stack
Maven: Package Manager <br>
Java Spring Boot: Framework for the api <br>
Java: Language of the api <br>
Postgres: Database persistence (version 16) <br>
Flyway: Database migrations <br>
JDBC Template: Database connection <br>
Docker: Run-time container

## Project Setup
### Run in Docker
Requirements: 
- Download and Install Docker
  - https://www.docker.com/products/docker-desktop/
- Clone project to local machine

Commands:
- Build the jar file by running the maven command: 
  - $ mvn install -DskipTests
- Then run the docker compose file with this command:
  - $ docker-compose up
- This will start three docker containers, one for the api, one for the flyway migrations, and one for the database.
- You can access the api on port 8080
- The test endpoint localhost:8080/heartbeat will return the string "thump thump" when working correctly.

### Run Locally
Requirements:
- This project runs on java 17, download and install the SDK and JDK.
  - https://www.oracle.com/java/technologies/downloads/
- Then download and install docker (for Postgres and Flyway)
    - https://www.docker.com/products/docker-desktop/
- -OR-
- Download and install Postgres version 15
  - https://www.postgresql.org/download/
- Finally clone the project to your local machine

Commands:
- If you installed postgres locally start the postgres database, the default username is 'postgres', the default password is 'root'
- Else start the docker container for postgres 
- Then run the project through your preferred IDE or follow the commands in the run in docker section above to create a jar of the project and then run it.
- You can access the api on port 8080
- The test endpoint localhost:8080/v1/heartbeat will return the string "thump thump" when working correctly.

### Additional Information
Check the Notes.md file for more information on the design and documentation of this project

Note: This project uses test containers for integration tests, on some computers the tests will fail because the testcontainers are not all starting if this is the case the load can be reduced by commenting out the (GetWithFilters...Tests.java) series of tests.
