package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityFactory;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionTestFactory;

public class SystemFactory implements EntityFactory<System, SystemRequestDto, SystemResponseDto> {

    @Override
    public System generateEntity(Generate generate) {
        switch (generate) {
            case VALID -> {
                SystemRequestDto requestDto = new SystemRequestDto("SystemName", 3, false);
                return new System().updateFromRequestDto(requestDto);
            }
            case ANOTHER_VALID -> {
                SystemRequestDto requestDto = new SystemRequestDto("AnotherSystemName", 4, true);
                return new System().updateFromRequestDto(requestDto);
            }
            case VALID_PERSISTED -> {
                return new System(1, "PersistedName", 5, false);
            }
            case ANOTHER_VALID_PERSISTED -> {
                return new System(2, "AnotherPersistedName", 6, true);
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
                return new SystemRequestDto("SystemDtoName", 7, false);
            }
            case ANOTHER_VALID -> {
                return new SystemRequestDto("AnotherSystemDtoName", 8, true);
            }
            case VALID_PERSISTED -> {
                throw new ExceptionTestFactory("Cannot call 'generateRequestDto()' with VALID_PERSISTED option");
            }
            case ANOTHER_VALID_PERSISTED -> {
                throw new ExceptionTestFactory("Cannot call 'generateRequestDto()' with ANOTHER_VALID_PERSISTED option");
            }
            case INVALID -> {
                //name cannot be blank
                //generation must be positive
                return new SystemRequestDto("", -1, false);
            }
            case EMPTY -> {
                return new SystemRequestDto(null, null, null);
            }
            default -> {
                throw new ExceptionTestFactory("Call made to 'generateRequestDto()' with unknown Generate enum");
            }
        }
    }

    @Override
    public SystemRequestDto generateRequestDtoFromEntity(System entity) {
        return new SystemRequestDto(entity.getName(), entity.getGeneration(), entity.isHandheld());
    }
}
