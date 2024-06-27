package com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepositoryTests;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Deprecated
public class ToyRepositoryTests extends EntityRepositoryTests<Toy, ToyRequestDto, ToyResponseDto> {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    private String startsWith = "somethingInteresting";

    @Override
    protected void setupRepositoryAndEntityName() {
        entityName = Toy.class.getSimpleName();
        repository = new ToyRepository(jdbcTemplate);
    }

    @Override
    protected void setupFactory() {
        factory = new ToyFactory(startsWith);
    }

    @Override
    protected Filter startsWithFilter() {
        return new Filter("toy", "text", "name", Filter.OPERATOR_STARTS_WITH, startsWith, false);
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
