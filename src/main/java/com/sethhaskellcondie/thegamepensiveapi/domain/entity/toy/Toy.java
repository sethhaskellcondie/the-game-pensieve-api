package com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.Entity;
import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionMalformedEntity;

import java.sql.Timestamp;
import java.util.List;

public class Toy extends Entity<ToyRequestDto, ToyResponseDto> {

    private String name;
    private String set;

    public Toy() {
        super();
    }

    //only used in tests and repositories
    public Toy(Integer id, String name, String set,
               Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt,
               List<CustomFieldValue> customFieldValues) {
        super(id, createdAt, updatedAt, deletedAt, customFieldValues);
        this.name = name;
        this.set = set;
        this.validate();
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
        setCustomFieldValues(requestDto.customFieldValues());
        this.validate();
        return this;
    }

    public ToyResponseDto convertToResponseDto() {
        return new ToyResponseDto(getKey(), this.id, this.name, this.set, this.created_at, this.updated_at, this.deleted_at, this.customFieldValues);
    }

    @Override
    public ToyRequestDto convertToRequestDto() {
        return new ToyRequestDto(this.name, this.set, this.customFieldValues);
    }

    @Override
    public String getKey() {
        return Keychain.TOY_KEY;
    }

    private void validate() throws ExceptionMalformedEntity {
        if (null == this.name || this.name.isBlank()) {
            throw new ExceptionMalformedEntity(
                    List.of(new ExceptionInputValidation("Name is required for a Toy"))
            );
        }
    }
}

