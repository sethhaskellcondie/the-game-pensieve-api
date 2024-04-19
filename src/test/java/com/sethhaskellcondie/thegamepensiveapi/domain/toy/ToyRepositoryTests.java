package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityRepositoryTests;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        return new Filter("toy", "name", Filter.FILTER_OPERATOR_STARTS_WITH, startsWith);
    }

    @Override
    protected void validateReturnedObject(Toy expected, Toy actual) {
        assertAll(
                "These " + entityName + " objects are invalid.",
                () -> assertNotNull(actual.getId()),
                () -> assertEquals(expected.getName(), actual.getName()),
                () -> assertEquals(expected.getSet(), actual.getSet())
        );
    }

    @Override
    protected void validateReturnedObject(ToyRequestDto expected, Toy actual) {
        assertAll(
                "These " + entityName + " objects are invalid.",
                () -> assertNotNull(actual.getId()),
                () -> assertEquals(expected.name(), actual.getName()),
                () -> assertEquals(expected.set(), actual.getSet())
        );
    }
}
