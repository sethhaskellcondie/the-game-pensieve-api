package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityRepositoryTests;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ToyRepositoryTests extends EntityRepositoryTests<Toy, ToyRequestDto, ToyResponseDto> {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    protected void setupRepositoryAndEntityName() {
        entityName = Toy.class.getSimpleName();
        repository = new ToyRepository(jdbcTemplate);
    }

    @Override
    protected Toy generateValidEntity() {
        final String name = "Donkey Kong";
        final String set = "Amiibo";
        ToyRequestDto requestDto = new ToyRequestDto(name, set);
        return new Toy().updateFromRequestDto(requestDto);
    }

    @Override
    protected ToyRequestDto generateRequestDto(Generate generate) {
        switch (generate) {
            case VALID -> {
                final String name = "MegaMan";
                final String set = null;
                return new ToyRequestDto(name, set);
            }
            case VALID_SECOND -> {
                final String name = "Loki";
                final String set = "Disney Infinity";
                return new ToyRequestDto(name, set);
            }
            case INVALID -> {
                final String name = ""; //the name cannot be blank
                final String set = null;
                return new ToyRequestDto(name, set);
            }
            default -> {
                return new ToyRequestDto(null, null);
            }
        }
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
