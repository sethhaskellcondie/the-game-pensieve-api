package com.sethhaskellcondie.thegamepensiveapi.domain;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RepositoryTests are integration tests that will test the repository, and it's connection to the database
 * There were a variety of different options that Spring provides
 * <p>
 * DataJpaTest works with spring data JPA and looks for @Entities
 * JdbcTest works with projects with a data source, that do not use Spring Data JDBC it configures an in-memory database with a JdbcTemplate
 * DataJdbcTest are for projects that use Spring Data JDBC it also configures an in-memory database with a JdbcTemplate
 * <p>
 * JdbcTest would be the closest fit for this project, but I don't want to use an in memory database instead I decided to go with @Testcontainers
 */
@Testcontainers //Tells the test suite to look for @Container tags in this class
public abstract class EntityRepositoryTests<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> {
    //these tests will run real postgreSQL database in a docker container
    //then use the test containers library to run tests against it
    //https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing.testcontainers

    @Container
    //The service connection annotation will inform spring boot that it should configure this database connection
    @ServiceConnection
    // if the container is static then it will spun up once for the entire test suite
    // if the container is NOT static then it will be spun once for EVERY test
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:alpine");

    private EntityRepository<T, RequestDto, ResponseDto> repository;

    protected EntityRepositoryTests(EntityRepository<T, RequestDto, ResponseDto> repository) {
        this.repository = repository;
    }

    protected abstract T generateSavedEntity();
    protected abstract T generateNewEntity();
    protected abstract boolean validateReturnedObject(T expected, T actual);

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

    @Test
    void insertRequestDto_Success_ReturnEntity() throws ExceptionFailedDbValidation {
        T expected = generateNewEntity();

        T actual = repository.insert(expected);

        assertTrue(validateReturnedObject(expected, actual));
    }
//
//    @Test
//    void insertRequestDto_FailsValidation_ThrowException() {
//
//    }
//
//    @Test
//    void insertEntity_Success_ReturnEntity() {
//
//    }
//
//    @Test
//    void insertEntity_FailsValidation_ThrowException() {
//
//    }
//
//    @Test
//    void getWithFilters_NoFilters_ReturnList() {
//
//    }
//
//    @Test
//    void getById_Success_ReturnEntity() {
//
//    }
//
//    @Test
//    void getById_BadId_ThrowException() {
//
//    }
//
//    @Test
//    void update_Success_ReturnEntity() {
//
//    }
//
//    @Test
//    void update_FailedValidation_ThrowException() {
//
//    }
//
//    @Test
//    void deleteById_Success_NoException() {
//
//    }
//
//    @Test
//    void deleteById_BadId_ThrowException() {
//
//    }
}
