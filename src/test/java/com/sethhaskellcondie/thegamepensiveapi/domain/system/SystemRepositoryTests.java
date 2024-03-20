package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@JdbcTest
@ActiveProfiles("test-container")
public class SystemRepositoryTests {
    //-------------------------get this working with a testcontainer
    //-------------------------get this working with the flyway scripts

    //I need the @JdbcTest for this Autowired to work but I don't want to use an in memory database
    //https://www.baeldung.com/spring-jdbctemplate-testing
    //https://medium.com/javarevisited/spring-boot-testing-testcontainers-and-flyway-df4a71376db4
    //I may need to manually instantiate the repository...but how do I get flyway to work with it?

    @Autowired
    private JdbcTemplate jdbcTemplate;
    private SystemRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SystemRepositoryImpl(jdbcTemplate);
    }

//    @DynamicPropertySource
//    public static void configureProperties(DynamicPropertyRegistry registry) {
//        registry.add("db.jdbc-url", postgres::getJdbcUrl);
//        registry.add("db.username", postgres::getUsername);
//        registry.add("db.password", postgres::getPassword);
//    }

//    @Test
//    void connectionEstablished() {
//        assertThat(postgres.isCreated()).isTrue();
//        assertThat(postgres.isRunning()).isTrue();
//    }


    @Test
    void insertRequestDto_Success_ReturnEntity() throws ExceptionFailedDbValidation {
        String name = "NES";
        int generation = 3;
        boolean handheld = false;
        SystemRequestDto expected = new SystemRequestDto(name, generation, handheld);

        System actual = repository.insert(expected);

        assertNotNull(actual.getId());
        assertEquals(expected.name(), actual.getName());
        assertEquals(expected.generation(), actual.getGeneration());
        assertEquals(expected.handheld(), actual.isHandheld());
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
