package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

//@DataJpaTest works with spring data JPA and looks for @Entities
//@JdbcTest works with projects with a data source, that do not use Spring Data JDBC it configures an in-memory database with a JdbcTemplate
//@DataJdbcTest are for projects that use Spring Data JDBC it also configures an in-memory database with a JdbcTemplate
//@JdbcTest would be the closest fit for this project, but I don't want to use an in memory database instead I want to use a Testcontainer
@Testcontainers //Tells the test suite to look for @Container tags in this class
public class SystemDataTests {
    //these tests will run real postgreSQL database in a docker container
    //then use the test containers library to run tests against it
    //https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing.testcontainers

    @Container
    //The service connection annotation will inform spring boot that it should configure this database connection
    @ServiceConnection
    // if the container is static then it will spun up once for the entire test suite
    // if the container is NOT static then it will be spun once for EVERY test
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:alpine");

    @DynamicPropertySource
    public static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("db.jdbc-url", postgres::getJdbcUrl);
        registry.add("db.username", postgres::getUsername);
        registry.add("db.password", postgres::getPassword);
    }

    @Test
    void connectionEstablished() {
        assertThat(postgres.isCreated()).isTrue();
        assertThat(postgres.isRunning()).isTrue();
    }
}
