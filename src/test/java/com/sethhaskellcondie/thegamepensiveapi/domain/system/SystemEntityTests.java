package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionMalformedEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * EntityTests test specific implementation of the two abstract methods
 *  - updateFromRequestDto(RequestDto requestDto);
 *  - convertToResponseDto();
 * Along with any other logic specific to that Entity
 * <p>
 * This is also where Entity validation will be tested.
 */
public class SystemEntityTests {

    @Test
    public void updateFromRequestDto_ValidDto_SystemUpdated() {
        String name = "name";
        int generation = 3;
        boolean handheld = false;

        SystemRequestDto requestDto = new SystemRequestDto(name, generation, handheld);
        System system = new System();

        system.updateFromRequestDto(requestDto);

        assertEquals(name, system.getName());
        assertEquals(generation, system.getGeneration());
        assertEquals(handheld, system.isHandheld());
    }

    @Test
    public void updateFromRequestDto_FieldsNull_ThrowMultipleErrors() {
        String name = null;
        Integer generation = null;
        Boolean handheld = null;
        int numberOfErrors = 3;

        SystemRequestDto requestDto = new SystemRequestDto(name, generation, handheld);
        System system = new System();

        try {
            system.updateFromRequestDto(requestDto);
        } catch (ExceptionMalformedEntity e) {
            // for (Exception exception: e.getErrors()) {
            //     java.lang.System.out.println(exception.getMessage() + "\n");
            // }
            assertEquals(numberOfErrors, e.getErrors().size());
        }
    }

    @Test
    public void updateFromRequestDto_FieldsIncorrect_ThrowMultipleErrors() {
        String name = "";
        Integer generation = -1;
        Boolean handheld = false;
        int numberOfErrors = 2;

        SystemRequestDto requestDto = new SystemRequestDto(name, generation, handheld);
        System system = new System();

        try {
            system.updateFromRequestDto(requestDto);
        } catch (ExceptionMalformedEntity e) {
            // for (Exception exception: e.getErrors()) {
            //     java.lang.System.out.println(exception.getMessage() + "\n");
            // }
            assertEquals(numberOfErrors, e.getErrors().size());
        }
    }

    @Test
    public void convertToResponseDto_HappyPath_DtoCreated() {
        int id = 987;
        String name = "NES";
        int generation = 3;
        boolean handheld = false;

        System system = new System(id, name, generation, handheld);

        SystemResponseDto responseDto = system.convertToResponseDto();

        assertEquals(id, responseDto.id());
        assertEquals(name, responseDto.name());
        assertEquals(generation, responseDto.generation());
        assertEquals(handheld, responseDto.handheld());
    }
}
