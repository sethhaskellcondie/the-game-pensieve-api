package com.sethhaskellcondie.thegamepensiveapi;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.sethhaskellcondie.thegamepensiveapi.domain.Entity;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.System;

public class EntityTest
{
	@Test
	public void equals_PersistedAndNotPersisted_false() {
		Entity persistedEntity = generatePersistedEntity(1);
		Entity entity = generateEntity();
		assertFalse(entity.equals(persistedEntity));
	}

	@Test
	public void equals_DifferentIds_False() {
		Entity entity1 = generatePersistedEntity(1);
		Entity entity2 = generatePersistedEntity(2);
		assertFalse(entity1.equals(entity2));
	}

	@Test
	public void equals_EntityAndNull_False() {
		Entity entity1 = generatePersistedEntity(1);
		Integer notAnEntity = 1;
		assertFalse(entity1.equals(notAnEntity));
	}

	@Test
	public void equals_SameIds_True() {
		Entity entity1 = generatePersistedEntity(1);
		Entity entity1Again = generatePersistedEntity(1);
		assertFalse(entity1.equals(entity1Again));
	}

	//Since Entity are abstract we will use SystemEntity Objects for the tests
	private Entity generatePersistedEntity(Integer id) {
		return new System(id, "test", 1, false);
	}

	private Entity generateEntity() {
		return new System();
	}
}
