package com.sethhaskellcondie.thegamepensiveapi.domain;

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

    public Integer getId() {
        return id;
    }

    public final boolean isPersistent() {
        return id != null;
    }

    public abstract Entity<RequestDto, ResponseDto> updateFromRequest(RequestDto requestDto);

    public abstract ResponseDto convertToResponseDto();

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Entity<?, ?> entity)) {
            return false;
        }
        if (!entity.isPersistent()) {
            return false;
        }
        return Objects.equals(this.id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
