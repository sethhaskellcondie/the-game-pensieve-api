package com.sethhaskellcondie.thegamepensiveapi.domain;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionMalformedEntity;

import java.util.Objects;

public abstract class Entity<RequestDto, ResponseDto> {
    protected final Integer id;

    protected Entity() {
        id = null;
    }

    //IDs are ONLY generated in tests and by the database
    protected Entity(Integer id) {
        this.id = id;
    }

    public final Integer getId() {
        return id;
    }

    public final boolean isPersisted() {
        return id != null;
    }

    /**
     * New Entity objects are created in that Entities' repository, after creation all entities need
     * to be able to take the data from the request and apply that to the Entity then return it.
     * Throws an ExceptionInputValidation if the object is invalid after the requestDto has been applied.
     */
    protected abstract Entity<RequestDto, ResponseDto> updateFromRequestDto(RequestDto requestDto) throws ExceptionInputValidation;

    /**
     * Every Entity will need to be able to convert into a default responseDto to be returned
     * from the Gateway of that Entity. OtherDto objects may be created for that Entity,
     * following the open/closed principle.
     */
    protected abstract ResponseDto convertToResponseDto();

    /**
     * Every Entity will need to validate itself to make sure that the object is a valid state
     * throws ExceptionMalformedEntity if the object is not in a valid state
     */
    public abstract void validate() throws ExceptionMalformedEntity;

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Entity<?, ?> entity)) {
            return false;
        }
        if (!entity.isPersisted()) {
            return false;
        }
        return Objects.equals(this.id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
