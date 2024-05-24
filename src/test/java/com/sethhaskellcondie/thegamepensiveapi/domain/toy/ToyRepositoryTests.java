package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityRepositoryTests;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValueRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ToyRepositoryTests extends EntityRepositoryTests<Toy, ToyRequestDto, ToyResponseDto> {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    private String startsWith = "somethingInteresting";

    @Override
    protected void setupRepositoryAndEntityName() {
        entityName = Toy.class.getSimpleName();
        CustomFieldValueRepository customFieldValueRepository = new CustomFieldValueRepository(jdbcTemplate);
        repository = new ToyRepository(jdbcTemplate, customFieldValueRepository);
    }

    @Override
    protected void setupFactory() {
        factory = new ToyFactory(startsWith);
    }

    @Override
    protected Filter startsWithFilter() {
        return new Filter("toy", "name", Filter.OPERATOR_STARTS_WITH, startsWith);
    }

    @Override
    protected void validateReturnedObject(Toy expected, Toy actual) {
        assertAll(
                "These " + entityName + " objects are invalid.",
                () -> assertNotNull(actual.getId()),
                () -> assertEquals(expected.getName(), actual.getName()),
                () -> assertEquals(expected.getSet(), actual.getSet()),
                () -> assertNotNull(actual.getCreatedAt()),
                () -> assertNotNull(actual.getUpdatedAt()),
                () -> assertNull(actual.getDeletedAt())
        );
    }

    @Override
    protected void validateReturnedObject(ToyRequestDto expected, Toy actual) {
        assertAll(
                "These " + entityName + " objects are invalid.",
                () -> assertNotNull(actual.getId()),
                () -> assertEquals(expected.name(), actual.getName()),
                () -> assertEquals(expected.set(), actual.getSet()),
                () -> assertNotNull(actual.getCreatedAt()),
                () -> assertNotNull(actual.getUpdatedAt()),
                () -> assertNull(actual.getDeletedAt())
        );
    }
}
