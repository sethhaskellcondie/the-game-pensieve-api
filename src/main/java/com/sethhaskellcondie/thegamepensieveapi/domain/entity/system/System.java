package com.sethhaskellcondie.thegamepensieveapi.domain.entity.system;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityData;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.Entity;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionMalformedEntity;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Each Entity object will implement the Entity interface, this will enforce the ID equality
 * and persistence check. This will include the EntityData class that holds the data common to each entity.
 * (ids, timestamps, and custom fields)
 */
public class System implements Entity<SystemRequestDto, SystemResponseDto> {

    private final EntityData entityData;
    private String name;
    private int generation;
    private boolean handheld;

    public System() {
        this.entityData = new EntityData();
    }

    /**
     * Every Entity will have a constructor that takes an ID
     * this constructor should only be used in repositories and tests.
     * To hydrate an Entity with an ID call getWithId on the repository.
     */
    public System(Integer id, String name, int generation, boolean handheld,
                  Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt,
                  List<CustomFieldValue> customFieldValues) {
        this.entityData = new EntityData(id, createdAt, updatedAt, deletedAt, customFieldValues);
        this.name = name;
        this.generation = generation;
        this.handheld = handheld;
        this.validate();
    }

    @Override
    public Integer getId() {
        return entityData.getId();
    }

    @Override
    public String getKey() {
        return Keychain.SYSTEM_KEY;
    }

    @Override
    public List<CustomFieldValue> getCustomFieldValues() {
        return entityData.getCustomFieldValues();
    }

    @Override
    public void setCustomFieldValues(List<CustomFieldValue> customFieldValues) {
        entityData.setCustomFieldValues(customFieldValues);
    }

    @Override
    public boolean isPersisted() {
        return entityData.isPersisted();
    }

    @Override
    public boolean isDeleted() {
        return entityData.isDeleted();
    }

    public Timestamp getCreatedAt() {
        return entityData.getCreatedAt();
    }

    public Timestamp getUpdatedAt() {
        return entityData.getUpdatedAt();
    }

    public Timestamp getDeletedAt() {
        return entityData.getDeletedAt();
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

    @Override
    public System updateFromRequestDto(SystemRequestDto requestDto) {
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
        entityData.setCustomFieldValues(requestDto.customFieldValues());
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
    public SystemResponseDto convertToResponseDto() {
        return new SystemResponseDto(
                this.getKey(), entityData.getId(), this.name, this.generation, this.handheld,
                entityData.getCreatedAt(), entityData.getUpdatedAt(), entityData.getDeletedAt(),
                entityData.getCustomFieldValues()
        );
    }

    @Override
    public SystemRequestDto convertToRequestDto() {
        return new SystemRequestDto(this.name, this.generation, this.handheld, entityData.getCustomFieldValues());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof System other)) {
            return false;
        }
        if (!other.isPersisted()) {
            return false;
        }
        return Objects.equals(this.getId(), other.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
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
