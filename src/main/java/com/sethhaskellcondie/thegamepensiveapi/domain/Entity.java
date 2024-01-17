package com.sethhaskellcondie.thegamepensiveapi.domain;

import java.util.Objects;

abstract public class Entity {
	protected final Integer id;

	protected Entity() {
		id = null;
	}

	//IDs are ONLY generated in tests and by the database
	protected Entity(Integer id) {
		this.id = id;
	}

	public Integer getId() {
		return id;
	}

	public boolean isPersistent() {
		return id == null;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof Entity)) {
			return false;
		}
		Entity entity = ((Entity) obj);
		if (!entity.isPersistent()) {
			return false;
		}
		if (this.id == entity.id) {
			return true;
		}
		return false;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id);
	}
}
