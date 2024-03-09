package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.Entity;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionMalformedEntity;

import java.util.List;

public class Toy extends Entity<ToyRequestDto, ToyResponseDto> {

    private String name;
    private String set;


    public Toy() {
        super();
    }

    //only used in tests and repositories
    public Toy(Integer id, String name, String set) {
        super(id);
        this.name = name;
        this.set = set;
    }

    public String getName() {
        return name;
    }

    public String getSet() {
        return set;
    }

    public Toy updateFromRequestDto(ToyRequestDto requestDto) {
        this.name = requestDto.name();
        this.set = requestDto.set();
        return this;
    }

    public ToyResponseDto convertToResponseDto() {
        return new ToyResponseDto(this.id, this.name, this.set);
    }

    public void validate() throws ExceptionMalformedEntity {
        if (this.name.isBlank()) {
            throw new ExceptionMalformedEntity(
                    List.of(new ExceptionInputValidation("Name is required for a Toy"))
            );
        }
    }
}

record ToyRequestDto(String name, String set) { }
record ToyResponseDto(Integer id, String name, String set) { }
