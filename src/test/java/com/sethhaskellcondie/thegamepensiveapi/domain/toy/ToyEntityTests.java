package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionMalformedEntity;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Deprecated
public class ToyEntityTests {

    @Test
    public void updateFromRequestDto_ValidDto_ToyUpdated() {
        final String name = "name";
        final String set = "set";

        final ToyRequestDto requestDto = new ToyRequestDto(name, set, new ArrayList<>());
        final Toy toy = new Toy();

        toy.updateFromRequestDto(requestDto);

        assertEquals(name, toy.getName());
        assertEquals(set, toy.getSet());
    }

    @Test
    public void updateFromRequestDto_FieldsNull_ThrowExceptionMalformedEntity() {
        final int numberOfErrors = 1;

        final ToyRequestDto requestDto = new ToyRequestDto(null, null, new ArrayList<>());
        final Toy toy = new Toy();

        try {
            toy.updateFromRequestDto(requestDto);
        } catch (ExceptionMalformedEntity e) {
            assertEquals(numberOfErrors, e.getMessages().size());
        }
    }

    @Test
    public void updateFromRequestDto_FieldsIncorrect_ThrowExceptionMalformedEntity() {
        final String name = ""; //name cannot be blank
        final String set = null;
        final int numberOfErrors = 1;

        final ToyRequestDto requestDto = new ToyRequestDto(name, set, new ArrayList<>());
        final Toy toy = new Toy();

        try {
            toy.updateFromRequestDto(requestDto);
        } catch (ExceptionMalformedEntity e) {
            assertEquals(numberOfErrors, e.getMessages().size());
        }
    }

    @Test
    public void convertToResponseDto_HappyPath_DtoCreated() {
        final int id = 99;
        final String name = "Super Mario";
        final String set = "Amiibo";
        final Instant created_at = Instant.now();
        final Instant updated_at = Instant.now();

        final Toy toy = new Toy(id, name, set, Timestamp.from(created_at), Timestamp.from(updated_at), null, new ArrayList<>());

        final ToyResponseDto responseDto = toy.convertToResponseDto();

        assertAll(
                "Converting a Toy to a DTO has failed",
                () -> assertEquals("toy", responseDto.key()),
                () -> assertEquals(id, responseDto.id()),
                () -> assertEquals(name, responseDto.name()),
                () -> assertEquals(Timestamp.from(created_at), responseDto.createdAt()),
                () -> assertEquals(Timestamp.from(updated_at), responseDto.updatedAt()),
                () -> assertNull(responseDto.deletedAt())
        );
    }
}
