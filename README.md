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
- Download and Install Docker (for Postgres and Flyway)
    - https://www.docker.com/products/docker-desktop/
- -OR-
- Download and Install Postgres version 15
  - https://www.postgresql.org/download/
- This project runs on Java 17, download and install the SDK and JDK.
  - https://www.oracle.com/java/technologies/downloads/
- Clone project to local machine

Commands:
- Start the postgres database, the default username is 'postgres', the default password is 'root'
- -OR- 
- Run the docker container for postgres
- Run the project through your preferred IDE
- -OR-
- Run the maven commands to create a jar file and run it:
  - $ mvn install -DskipTests
  - $ java -jar /target/the_game_pensive_api.jar
- You can access the api on port 8080
- The test endpoint localhost:8080/v1/heartbeat will return the string "thump thump" when working correctly.

### Documentation
The Entity and System classes are heavily commented to explain the decisions behind the design.
