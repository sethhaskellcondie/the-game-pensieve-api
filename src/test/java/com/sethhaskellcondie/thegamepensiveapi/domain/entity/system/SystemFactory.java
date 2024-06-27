package com.sethhaskellcondie.thegamepensiveapi.domain.entity.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityFactory;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionTestFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;

@Deprecated
public class SystemFactory implements EntityFactory<System, SystemRequestDto, SystemResponseDto> {

    private final String startsWith;

    public SystemFactory(String startsWith) {
        this.startsWith = startsWith;
    }

    @Override
    public System generateEntity(Generate generate) {
        switch (generate) {
            case VALID -> {
                final SystemRequestDto requestDto = new SystemRequestDto("SystemName", 3, false, new ArrayList<>());
                return new System().updateFromRequestDto(requestDto);
            }
            case ANOTHER_VALID -> {
                final SystemRequestDto requestDto = new SystemRequestDto("AnotherSystemName", 4, true, new ArrayList<>());
                return new System().updateFromRequestDto(requestDto);
            }
            case VALID_PERSISTED -> {
                return new System(1, "PersistedName", 5, false, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());
            }
            case ANOTHER_VALID_PERSISTED -> {
                return new System(2, "AnotherPersistedName", 6, true, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());
            }
            case STARTS_WITH_VALID_PERSISTED -> {
                return new System(3, startsWith + "PersistedName", 5, false, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());
            }
            case ANOTHER_STARTS_WITH_VALID_PERSISTED -> {
                return new System(4, startsWith + "AnotherPersistedName", 6, true, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());
            }
            case INVALID -> {
                throw new ExceptionTestFactory("Cannot call 'generateEntity()' with INVALID option");
            }
            case EMPTY -> {
                return new System();
            }
            default -> {
                throw new ExceptionTestFactory("Call made to 'generateEntity()' with unknown Generate enum");
            }
        }
    }

    @Override
    public SystemRequestDto generateRequestDto(Generate generate) {
        switch (generate) {
            case VALID -> {
                return new SystemRequestDto("SystemDtoName", 7, false, new ArrayList<>());
            }
            case ANOTHER_VALID -> {
                return new SystemRequestDto("AnotherSystemDtoName", 8, true, new ArrayList<>());
            }
            case VALID_PERSISTED -> {
                throw new ExceptionTestFactory("Cannot call 'generateRequestDto()' with VALID_PERSISTED option");
            }
            case ANOTHER_VALID_PERSISTED -> {
                throw new ExceptionTestFactory("Cannot call 'generateRequestDto()' with ANOTHER_VALID_PERSISTED option");
            }
            //I know it says persisted but requestDtos are not persisted, these are intended to be persisted in the test
            case STARTS_WITH_VALID_PERSISTED -> {
                return new SystemRequestDto(startsWith + "SystemName", 9, false, new ArrayList<>());
            }
            case ANOTHER_STARTS_WITH_VALID_PERSISTED -> {
                return new SystemRequestDto(startsWith + "AnotherSystemName", 10, true, new ArrayList<>());
            }
            case INVALID -> {
                //name cannot be blank
                //generation must be positive
                return new SystemRequestDto("", -1, false, new ArrayList<>());
            }
            case EMPTY -> {
                return new SystemRequestDto(null, null, null, new ArrayList<>());
            }
            default -> {
                throw new ExceptionTestFactory("Call made to 'generateRequestDto()' with unknown Generate enum");
            }
        }
    }

    @Override
    public SystemRequestDto generateRequestDtoFromEntity(System entity) {
        return new SystemRequestDto(entity.getName(), entity.getGeneration(), entity.isHandheld(), new ArrayList<>());
    }
}
