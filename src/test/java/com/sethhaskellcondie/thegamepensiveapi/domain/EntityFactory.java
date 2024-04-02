package com.sethhaskellcondie.thegamepensiveapi.domain;

public interface EntityFactory<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> {

    enum Generate {
        VALID,
        ANOTHER_VALID,
        VALID_PERSISTED,
        ANOTHER_VALID_PERSISTED,
        INVALID,
        EMPTY
    }

    T generateEntity(Generate generate);

    RequestDto generateRequestDto(Generate generate);
    RequestDto generateRequestDtoFromEntity(T entity);

    //implement generateCustomEntity(); as needed
    //implement generateCustomRequestDto(); as needed
}
