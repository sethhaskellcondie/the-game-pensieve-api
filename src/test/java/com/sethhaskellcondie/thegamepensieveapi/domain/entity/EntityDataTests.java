package com.sethhaskellcondie.thegamepensieveapi.domain.entity;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EntityDataTests {

    @Test
    public void isPersisted_withId_True() {
        EntityData entityData = new EntityData(1, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());
        assertTrue(entityData.isPersisted());
    }

    @Test
    public void isPersisted_withoutId_False() {
        EntityData entityData = new EntityData();
        assertFalse(entityData.isPersisted());
    }

    @Test
    public void isDeleted_withDeletedAt_True() {
        EntityData entityData = new EntityData(1, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), new ArrayList<>());
        assertTrue(entityData.isDeleted());
    }

    @Test
    public void isDeleted_withoutDeletedAt_False() {
        EntityData entityData = new EntityData(1, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null, new ArrayList<>());
        assertFalse(entityData.isDeleted());
    }
}
