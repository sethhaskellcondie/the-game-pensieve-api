package com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityData;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.Entity;
import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionMalformedEntity;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

public class Toy implements Entity<ToyRequestDto, ToyResponseDto> {

    private final EntityData entityData;
    private String name;
    private String set;

    public Toy() {
        this.entityData = new EntityData();
    }

    //only used in tests and repositories
    public Toy(Integer id, String name, String set,
               Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt,
               List<CustomFieldValue> customFieldValues) {
        this.entityData = new EntityData(id, createdAt, updatedAt, deletedAt, customFieldValues);
        this.name = name;
        this.set = set;
        this.validate();
    }

    @Override
    public Integer getId() {
        return entityData.getId();
    }

    @Override
    public String getKey() {
        return Keychain.TOY_KEY;
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

    public String getSet() {
        return set;
    }

    public Toy updateFromRequestDto(ToyRequestDto requestDto) {
        this.name = requestDto.name();
        this.set = requestDto.set();
        entityData.setCustomFieldValues(requestDto.customFieldValues());
        this.validate();
        return this;
    }

    public ToyResponseDto convertToResponseDto() {
        return new ToyResponseDto(getKey(), entityData.getId(), this.name, this.set,
                entityData.getCreatedAt(), entityData.getUpdatedAt(), entityData.getDeletedAt(), entityData.getCustomFieldValues());
    }

    @Override
    public ToyRequestDto convertToRequestDto() {
        return new ToyRequestDto(this.name, this.set, entityData.getCustomFieldValues());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Toy other)) {
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
        if (null == this.name || this.name.isBlank()) {
            throw new ExceptionMalformedEntity(
                    List.of(new ExceptionInputValidation("Name is required for a Toy"))
            );
        }
    }
}
