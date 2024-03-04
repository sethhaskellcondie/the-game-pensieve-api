package com.sethhaskellcondie.thegamepensiveapi.domain;

import com.sethhaskellcondie.thegamepensiveapi.domain.system.System;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class EntityTests {
    @Test
    public void equals_PersistedAndNotPersisted_false() {
        Entity persistedEntity = generatePersistedEntity(1);
        Entity entity = generateEntity();
        assertNotEquals(entity, persistedEntity);
    }

    @Test
    public void equals_DifferentIds_False() {
        Entity entity1 = generatePersistedEntity(1);
        Entity entity2 = generatePersistedEntity(2);
        assertNotEquals(entity1, entity2);
    }

    @Test
    public void equals_EntityAndNull_False() {
        Entity entity1 = generatePersistedEntity(1);
        Integer notAnEntity = 1;
        assertNotEquals(entity1, notAnEntity);
    }

    @Test
    public void equals_SameIds_True() {
        Entity entity1 = generatePersistedEntity(1);
        Entity entity1Again = generatePersistedEntity(1);
        assertEquals(entity1, entity1Again);
    }

    //Since Entity are abstract we will use System Objects for the tests
    private Entity generatePersistedEntity(Integer id) {
        return new System(id, "test", 1, false);
    }

    private Entity generateEntity() {
        return new System();
    }
}
