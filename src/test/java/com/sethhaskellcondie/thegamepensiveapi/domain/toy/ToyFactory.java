package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityFactory;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionTestFactory;

public class ToyFactory implements EntityFactory<Toy, ToyRequestDto, ToyResponseDto> {

    private final String startsWith;

    public ToyFactory(String startsWith) {
        this.startsWith = startsWith;
    }

    @Override
    public Toy generateEntity(Generate generate) {
        switch (generate) {
            case VALID -> {
                ToyRequestDto requestDto = new ToyRequestDto("ToyName", "ToySet");
                return new Toy().updateFromRequestDto(requestDto);
            }
            case ANOTHER_VALID -> {
                ToyRequestDto requestDto = new ToyRequestDto("AnotherToyName", "ToySet");
                return new Toy().updateFromRequestDto(requestDto);
            }
            case VALID_PERSISTED -> {
                return new Toy(1, "PersistedName", "ToySet");
            }
            case ANOTHER_VALID_PERSISTED -> {
                return new Toy(2, "AnotherPersistedName", "ToySet");
            }
            case STARTS_WITH_VALID_PERSISTED -> {
                return new Toy(3, startsWith + "PersistedName", "ToySet");
            }
            case ANOTHER_STARTS_WITH_VALID_PERSISTED -> {
                return new Toy(4, startsWith + "AnotherPersistedName", "ToySet");
            }
            case INVALID -> {
                throw new ExceptionTestFactory("Cannot call 'generateEntity()' with INVALID option");
            }
            case EMPTY -> {
                return new Toy();
            }
            default -> {
                throw new ExceptionTestFactory("Call made to 'generateEntity()' with unknown Generate enum");
            }
        }
    }

    @Override
    public ToyRequestDto generateRequestDto(Generate generate) {
        switch (generate) {
            case VALID -> {
                return new ToyRequestDto("ToyDtoName", "ToySet");
            }
            case ANOTHER_VALID -> {
                return new ToyRequestDto("AnotherToyDtoName", "ToySet");
            }
            case VALID_PERSISTED -> {
                throw new ExceptionTestFactory("Cannot call 'generateRequestDto()' with VALID_PERSISTED option");
            }
            case ANOTHER_VALID_PERSISTED -> {
                throw new ExceptionTestFactory("Cannot call 'generateRequestDto()' with ANOTHER_VALID_PERSISTED option");
            }
            case INVALID -> {
                //name cannot be blank
                return new ToyRequestDto("", "");
            }
            case EMPTY -> {
                return new ToyRequestDto(null, null);
            }
            default -> {
                throw new ExceptionTestFactory("Call made to 'generateRequestDto()' with unknown Generate enum");
            }
        }
    }

    @Override
    public ToyRequestDto generateRequestDtoFromEntity(Toy entity) {
        return new ToyRequestDto(entity.getName(), entity.getSet());
    }
}
