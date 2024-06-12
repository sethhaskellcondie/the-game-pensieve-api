package com.sethhaskellcondie.thegamepensiveapi.domain;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class Entity<RequestDto, ResponseDto> {
    protected final Integer id;
    protected final Timestamp created_at;
    protected final Timestamp updated_at;
    protected final Timestamp deleted_at;
    protected List<CustomFieldValue> customFieldValues;

    protected Entity() {
        id = null;
        created_at = null;
        updated_at = null;
        deleted_at = null;
        customFieldValues = new ArrayList<>();
    }

    //IDs are ONLY generated in tests and by the database
    protected Entity(Integer id, Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt, List<CustomFieldValue> customFieldValues) {
        this.id = id;
        created_at = createdAt;
        updated_at = updatedAt;
        deleted_at = deletedAt;
        setCustomFieldValues(customFieldValues);
    }

    public final Integer getId() {
        return id;
    }

    public final Timestamp getCreatedAt() {
        return created_at;
    }

    public final Timestamp getUpdatedAt() {
        return created_at;
    }

    public final Timestamp getDeletedAt() {
        return deleted_at;
    }

    public final List<CustomFieldValue> getCustomFieldValues() {
        return customFieldValues;
    }

    public final void setCustomFieldValues(List<CustomFieldValue> customFieldValues) {
        if (null != customFieldValues) {
            this.customFieldValues = customFieldValues;
        } else {
            this.customFieldValues = new ArrayList<>();
        }
    }

    public final boolean isPersisted() {
        return id != null;
    }

    public final boolean isDeleted() {
        return deleted_at != null;
    }

    /**
     * This function will take an entity and then update it with all the attributes of the requestDto.
     * Throws an ExceptionInputValidation if the object is invalid after the requestDto has been applied.
     */
    protected abstract Entity<RequestDto, ResponseDto> updateFromRequestDto(RequestDto requestDto);

    /**
     * Every Entity will need to be able to convert into a default responseDto to be returned
     * from the Gateway of that Entity. OtherDto objects may be created for that Entity,
     * following the open/closed principle.
     */
    protected abstract ResponseDto convertToResponseDto();

    /**
     * Every Entity will have a key that is a constant form of the name of the entity.
     * This function will return the proper key from the Keychain.
     */
    protected abstract String getKey();

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
