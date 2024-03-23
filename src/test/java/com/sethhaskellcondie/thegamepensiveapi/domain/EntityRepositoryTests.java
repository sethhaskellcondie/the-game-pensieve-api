package com.sethhaskellcondie.thegamepensiveapi.domain;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionMalformedEntity;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
@JdbcTest
@ActiveProfiles("test-container")
public abstract class EntityRepositoryTests<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> {

    protected String EntityName;
    protected EntityRepository<T, RequestDto, ResponseDto> repository;
    protected enum Generate {
        VALID,
        VALID_SECOND,
        INVALID
    }

    /**
     * The tests are set up to use these abstract method that will be implemented with the Entity
     * Specific code in the <Entity>RepositoryTests.java
     * See SystemRepositoryTests.java for an example implementation
     */
    protected abstract void setupRepositoryAndEntityName();
    protected abstract T generateValidEntity();
    protected abstract RequestDto generateRequestDto(Generate generate);
    protected abstract void validateReturnedObject(T expected, T actual);
    protected abstract void validateReturnedObject(RequestDto expected, T actual);

    @BeforeEach
    public void setUp() {
        setupRepositoryAndEntityName();
    }

    @Test
    void insertRequestDto_HappyPath_ReturnEntity() throws ExceptionFailedDbValidation {
        final RequestDto expected = generateRequestDto(Generate.VALID);

        final T actual = repository.insert(expected);

        validateReturnedObject(expected, actual);
    }

    @Test
    void insertRequestDto_FailsEntityValidation_ThrowExceptionMalformedEntity() {
        assertThrows(ExceptionMalformedEntity.class, () -> repository.insert(generateRequestDto(Generate.INVALID)),
                "The " + EntityName + " repository didn't throw an ExceptionMalformedEntity when given an invalid " + EntityName);
    }

    @Test
    void insertEntity_HappyPath_ReturnEntity() throws ExceptionFailedDbValidation {
        final T expected = generateValidEntity();

        final T actual = repository.insert(expected);

        validateReturnedObject(expected, actual);
    }

    @Test
    void getWithFilters_NoFilters_ReturnAllEntities() throws ExceptionFailedDbValidation {
        final RequestDto requestDto1 = generateRequestDto(Generate.VALID);
        final T expected1 = repository.insert(requestDto1);
        final RequestDto requestDto2 = generateRequestDto(Generate.VALID_SECOND);
        final T expected2 = repository.insert(requestDto2);

        List<T> actual = repository.getWithFilters("");

        assertEquals(2, actual.size(), "There should only be 2 " + EntityName + " objects returned in the getWithFilters list.");
        assertEquals(expected1, actual.get(0), "The first " + EntityName + " object is out of order.");
        assertEquals(expected2, actual.get(1), "The second " + EntityName + " object is out of order.");
    }

    @Test
    void getById_HappyPath_ReturnEntity() throws ExceptionFailedDbValidation, ExceptionResourceNotFound {
        final RequestDto requestDto = generateRequestDto(Generate.VALID);
        final T expected = repository.insert(requestDto);

        final T actual = repository.getById(expected.getId());

        assertEquals(expected, actual, "The " + EntityName + " objects don't match after a getById() call.");
    }

    @Test
    void getById_BadId_ThrowExceptionResourceNotFound() {
        assertThrows(ExceptionResourceNotFound.class, () -> repository.getById(-1),
                "The " + EntityName + " repository didn't throw an ExceptionResourceNotFound when given an invalid id for getById().");
    }

    @Test
    void update_HappyPath_ReturnUpdatedEntity() throws ExceptionFailedDbValidation {
        final RequestDto requestDto = generateRequestDto(Generate.VALID);
        final T expected = repository.insert(requestDto);

        final RequestDto updateDto = generateRequestDto(Generate.VALID_SECOND);
        expected.updateFromRequestDto(updateDto);

        final T actual = repository.update(expected);

        validateReturnedObject(expected, actual);
    }

    @Test
    void deleteById_HappyPath_NoException() throws ExceptionFailedDbValidation, ExceptionResourceNotFound {
        final RequestDto requestDto = generateRequestDto(Generate.VALID);
        final T expected = repository.insert(requestDto);
        final int expectedId = expected.getId();

        repository.deleteById(expectedId);

        assertThrows(ExceptionResourceNotFound.class, () -> repository.getById(expectedId),
                "Delete failure: The " + EntityName + " repository could still find a " + EntityName + " after calling deleteById() with the id: " + expectedId);
    }

    @Test
    void deleteById_BadId_ThrowException() {
        assertThrows(ExceptionResourceNotFound.class, () -> repository.deleteById(-1),
                "The " + EntityName + " repository didn't throw an ExceptionResourceNotFound when given an invalid id for deleteById().");
    }
}
