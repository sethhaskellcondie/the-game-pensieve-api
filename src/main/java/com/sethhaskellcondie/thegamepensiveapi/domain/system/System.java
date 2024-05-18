package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.Entity;
import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionMalformedEntity;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInputValidation;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * The Entity object will extend the Entity abstract class, this will enforce the ID equality
 * and persistence check.
 */
public class System extends Entity<SystemRequestDto, SystemResponseDto> {

    // Inherit ID from Entity
    private String name;
    private int generation;
    private boolean handheld;

    public System() {
        super();
    }

    /**
     * Every Entity will have a constructor that takes an ID
     * this constructor should only be used in repositories and tests.
     * To hydrate an Entity with an ID call getWithId on the repository.
     */
    public System(Integer id, String name, int generation, boolean handheld, Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt) {
        super(id, createdAt, updatedAt, deletedAt);
        this.name = name;
        this.generation = generation;
        this.handheld = handheld;
        this.validate();
    }

    public String getName() {
        return name;
    }

    public int getGeneration() {
        return generation;
    }

    public boolean isHandheld() {
        return handheld;
    }

    /**
     * Also inherit isPersisted() from Entity
     * this will return True if the Entity has an ID
     * meaning that the Entity has been persisted to the database
     */

    @Override
    protected System updateFromRequestDto(SystemRequestDto requestDto) {
        List<Exception> exceptions = new ArrayList<>();
        this.name = requestDto.name();
        try {
            this.generation = requestDto.generation();
        } catch (NullPointerException e) {
            exceptions.add(new ExceptionInputValidation("System object error, generation can't be null"));
        }
        try {
            this.handheld = requestDto.handheld();
        } catch (NullPointerException e) {
            exceptions.add(new ExceptionInputValidation("System object error, handheld can't be null"));
        }
        try {
            this.validate();
        } catch (ExceptionMalformedEntity e) {
            exceptions.addAll(e.getExceptions());
        }
        if (!exceptions.isEmpty()) {
            throw new ExceptionMalformedEntity(exceptions);
        }
        return this;
    }

    @Override
    protected SystemResponseDto convertToResponseDto() {
        return new SystemResponseDto(this.getKey(), this.id, this.name, this.generation, this.handheld, this.created_at, this.updated_at, this.deleted_at);
    }

    @Override
    protected String getKey() {
        return Keychain.SYSTEM_KEY;
    }

    private void validate() throws ExceptionMalformedEntity {
        List<Exception> exceptions = new ArrayList<>();
        if (null == this.name || this.name.isBlank()) {
            exceptions.add(new ExceptionInputValidation("System object error, name cannot be blank"));
        }
        if (generation < 0) {
            exceptions.add(new ExceptionInputValidation("System object error, generation must be a positive number"));
        }
        if (!exceptions.isEmpty()) {
            throw new ExceptionMalformedEntity(exceptions);
        }
    }
}

/**
 * Define the DTO on the Entity if the shape of an object needs to be changed
 * all of those changes can be made here on the Entity with minimal changes elsewhere
 * in the project
 * <p>
 * The request DTO will use the wrapper classes for Primitives to allow nulls to be passed
 * in as input then it will be validated when they are used for the object to be created
 * this way we can pass all validation errors back at the same time.
 */
record SystemRequestDto(String name, Integer generation, Boolean handheld) { }
record SystemResponseDto(String key, int id, String name, int generation, boolean handheld, Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt) { }
