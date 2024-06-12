package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionMalformedEntity;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * EntityTests test specific implementation of the two abstract methods
 *  - updateFromRequestDto(RequestDto requestDto);
 *  - convertToResponseDto();
 * Along with any other logic specific to that Entity
 * <p>
 * This is also where Entity validation will be tested.
 */
@Deprecated
public class SystemEntityTests {

    @Test
    public void updateFromRequestDto_ValidDto_SystemUpdated() {
        final String name = "name";
        final int generation = 3;
        final boolean handheld = false;

        final SystemRequestDto requestDto = new SystemRequestDto(name, generation, handheld, new ArrayList<>());
        final System system = new System();

        system.updateFromRequestDto(requestDto);

        assertEquals(name, system.getName());
        assertEquals(generation, system.getGeneration());
        assertEquals(handheld, system.isHandheld());
    }

    @Test
    public void updateFromRequestDto_FieldsNull_ThrowMultipleErrors() {
        final int numberOfErrors = 3;

        final SystemRequestDto requestDto = new SystemRequestDto(null, null, null, new ArrayList<>());
        final System system = new System();

        try {
            system.updateFromRequestDto(requestDto);
        } catch (ExceptionMalformedEntity e) {
            // for (Exception exception: e.getErrors()) {
            //     java.lang.System.out.println(exception.getMessage() + "\n");
            // }
            assertEquals(numberOfErrors, e.getMessages().size());
        }
    }

    @Test
    public void updateFromRequestDto_FieldsIncorrect_ThrowMultipleErrors() {
        final String name = ""; //name cannot be blank
        final Integer generation = -1; //generation must be positive
        final Boolean handheld = false;
        final int numberOfErrors = 2;

        final SystemRequestDto requestDto = new SystemRequestDto(name, generation, handheld, new ArrayList<>());
        final System system = new System();

        try {
            system.updateFromRequestDto(requestDto);
        } catch (ExceptionMalformedEntity e) {
            // for (Exception exception: e.getErrors()) {
            //     java.lang.System.out.println(exception.getMessage() + "\n");
            // }
            assertEquals(numberOfErrors, e.getMessages().size());
        }
    }

    @Test
    public void convertToResponseDto_HappyPath_DtoCreated() {
        final int id = 987;
        final String name = "NES";
        final int generation = 3;
        final boolean handheld = false;
        final Instant created_at = Instant.now();
        final Instant updated_at = Instant.now();

        final System system = new System(id, name, generation, handheld, Timestamp.from(created_at), Timestamp.from(updated_at), null, new ArrayList<>());

        final SystemResponseDto responseDto = system.convertToResponseDto();

        assertAll(
                "Converting system to a DTO failed",
                () -> assertEquals("system", responseDto.key()),
                () -> assertEquals(id, responseDto.id()),
                () -> assertEquals(name, responseDto.name()),
                () -> assertEquals(generation, responseDto.generation()),
                () -> assertEquals(handheld, responseDto.handheld()),
                () -> assertEquals(Timestamp.from(created_at), responseDto.createdAt()),
                () -> assertEquals(Timestamp.from(updated_at), responseDto.updatedAt()),
                () -> assertNull(responseDto.deletedAt())
        );
    }
}
