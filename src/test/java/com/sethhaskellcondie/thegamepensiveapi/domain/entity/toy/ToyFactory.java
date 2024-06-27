package com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityFactory;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.Toy;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.ToyRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.ToyResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionTestFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;

@Deprecated
public class ToyFactory implements EntityFactory<Toy, ToyRequestDto, ToyResponseDto> {

    private final String startsWith;

    public ToyFactory(String startsWith) {
        this.startsWith = startsWith;
    }

    @Override
    public Toy generateEntity(Generate generate) {
        switch (generate) {
            case VALID -> {
                ToyRequestDto requestDto = new ToyRequestDto("ToyName", "ToySet", new ArrayList<>());
                return new Toy().updateFromRequestDto(requestDto);
            }
            case ANOTHER_VALID -> {
                ToyRequestDto requestDto = new ToyRequestDto("AnotherToyName", "ToySet", new ArrayList<>());
                return new Toy().updateFromRequestDto(requestDto);
            }
            case VALID_PERSISTED -> {
                return new Toy(1, "PersistedName", "ToySet", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());
            }
            case ANOTHER_VALID_PERSISTED -> {
                return new Toy(2, "AnotherPersistedName", "ToySet", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());
            }
            case STARTS_WITH_VALID_PERSISTED -> {
                return new Toy(3, startsWith + "PersistedName", "ToySet", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());
            }
            case ANOTHER_STARTS_WITH_VALID_PERSISTED -> {
                return new Toy(4, startsWith + "AnotherPersistedName", "ToySet", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());
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
                return new ToyRequestDto("ToyDtoName", "ToySet", new ArrayList<>());
            }
            case ANOTHER_VALID -> {
                return new ToyRequestDto("AnotherToyDtoName", "ToySet", new ArrayList<>());
            }
            case VALID_PERSISTED -> {
                throw new ExceptionTestFactory("Cannot call 'generateRequestDto()' with VALID_PERSISTED option");
            }
            case ANOTHER_VALID_PERSISTED -> {
                throw new ExceptionTestFactory("Cannot call 'generateRequestDto()' with ANOTHER_VALID_PERSISTED option");
            }
            //I know it says persisted but requestDtos are not persisted, these are intended to be persisted in the test
            case STARTS_WITH_VALID_PERSISTED -> {
                return new ToyRequestDto(startsWith + "ToyName", "ToySet", new ArrayList<>());
            }
            case ANOTHER_STARTS_WITH_VALID_PERSISTED -> {
                return new ToyRequestDto(startsWith + "AnotherToyName", "ToySet", new ArrayList<>());
            }
            case INVALID -> {
                //name cannot be blank
                return new ToyRequestDto("", "", new ArrayList<>());
            }
            case EMPTY -> {
                return new ToyRequestDto(null, null, new ArrayList<>());
            }
            default -> {
                throw new ExceptionTestFactory("Call made to 'generateRequestDto()' with unknown Generate enum");
            }
        }
    }

    @Override
    public ToyRequestDto generateRequestDtoFromEntity(Toy entity) {
        return new ToyRequestDto(entity.getName(), entity.getSet(), new ArrayList<>());
    }
}
