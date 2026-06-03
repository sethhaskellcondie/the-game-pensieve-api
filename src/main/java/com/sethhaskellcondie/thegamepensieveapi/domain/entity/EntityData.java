package com.sethhaskellcondie.thegamepensieveapi.domain.entity;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the shared state and behavior from the Entity abstract class for use in composition-based entities.
 * Composition-based entities hold an instance of this class and delegate the common field access to it.
 */
public class EntityData {
    private final Integer id;
    private final Timestamp created_at;
    private final Timestamp updated_at;
    private final Timestamp deleted_at;
    private List<CustomFieldValue> customFieldValues;

    public EntityData() {
        this.id = null;
        this.created_at = null;
        this.updated_at = null;
        this.deleted_at = null;
        this.customFieldValues = new ArrayList<>();
    }

    public EntityData(Integer id, Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt,
                      List<CustomFieldValue> customFieldValues) {
        this.id = id;
        this.created_at = createdAt;
        this.updated_at = updatedAt;
        this.deleted_at = deletedAt;
        setCustomFieldValues(customFieldValues);
    }

    public Integer getId() {
        return id;
    }

    public Timestamp getCreatedAt() {
        return created_at;
    }

    public Timestamp getUpdatedAt() {
        return updated_at;
    }

    public Timestamp getDeletedAt() {
        return deleted_at;
    }

    public List<CustomFieldValue> getCustomFieldValues() {
        return customFieldValues;
    }

    public void setCustomFieldValues(List<CustomFieldValue> customFieldValues) {
        if (null != customFieldValues) {
            this.customFieldValues = customFieldValues;
        } else {
            this.customFieldValues = new ArrayList<>();
        }
    }

    public boolean isPersisted() {
        return id != null;
    }

    public boolean isDeleted() {
        return deleted_at != null;
    }
}
