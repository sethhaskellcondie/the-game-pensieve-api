package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.Entity;
import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionMalformedEntity;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

public class Toy extends Entity<ToyRequestDto, ToyResponseDto> {

    private String name;
    private String set;

    public Toy() {
        super();
    }

    //only used in tests and repositories
    public Toy(Integer id, String name, String set,
               Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt,
               Map<String, String> customFields, Map<String, String> customFieldsValues) {
        super(id, createdAt, updatedAt, deletedAt, customFields, customFieldsValues);
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
        if (null != requestDto.customFields()) {
            this.customFields = requestDto.customFields();
        }
        if (null != requestDto.customFieldsValues()) {
            this.customFieldsValues = requestDto.customFieldsValues();
        }
        this.validate();
        return this;
    }

    public ToyResponseDto convertToResponseDto() {
        return new ToyResponseDto(getKey(), this.id, this.name, this.set,
                this.created_at, this.updated_at, this.deleted_at,
                this.customFields, this.customFieldsValues
        );
    }

    @Override
    protected String getKey() {
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

record ToyRequestDto(String name, String set, Map<String, String> customFields, Map<String, String> customFieldsValues) { }

record ToyResponseDto(String key, Integer id, String name, String set,
                      Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt,
                      Map<String, String> customFields, Map<String, String> customFieldsValues) { }
