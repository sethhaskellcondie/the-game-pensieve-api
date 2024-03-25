package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionMalformedEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ToyEntityTests {

    @Test
    public void updateFromRequestDto_ValidDto_ToyUpdated() {
        String name = "name";
        String set = "set";

        ToyRequestDto requestDto = new ToyRequestDto(name, set);
        Toy toy = new Toy();

        toy.updateFromRequestDto(requestDto);

        assertEquals(name, toy.getName());
        assertEquals(set, toy.getSet());
    }

    @Test
    public void updateFromRequestDto_FieldsNull_ThrowExceptionMalformedEntity() {
        int numberOfErrors = 1;

        ToyRequestDto requestDto = new ToyRequestDto(null, null);
        Toy toy = new Toy();

        try {
            toy.updateFromRequestDto(requestDto);
        } catch (ExceptionMalformedEntity e) {
            assertEquals(numberOfErrors, e.getMessages().size());
        }
    }

    @Test
    public void updateFromRequestDto_FieldsIncorrect_ThrowExceptionMalformedEntity() {
        String name = ""; //name cannot be blank
        String set = null;
        int numberOfErrors = 1;

        ToyRequestDto requestDto = new ToyRequestDto(name, set);
        Toy toy = new Toy();

        try {
            toy.updateFromRequestDto(requestDto);
        } catch (ExceptionMalformedEntity e) {
            assertEquals(numberOfErrors, e.getMessages().size());
        }
    }

    @Test
    public void convertToResponseDto_HappyPath_DtoCreated() {
        int id = 99;
        String name = "Super Mario";
        String set = "Amiibo";

        Toy toy = new Toy(id, name, set);

        ToyResponseDto responseDto = toy.convertToResponseDto();

        assertEquals("toy", responseDto.type());
        assertEquals(id, responseDto.id());
        assertEquals(name, responseDto.name());
    }
}
