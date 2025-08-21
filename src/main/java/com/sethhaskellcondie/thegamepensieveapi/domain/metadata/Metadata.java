package com.sethhaskellcondie.thegamepensieveapi.domain.metadata;

import java.time.LocalDateTime;

//Metadata is NOT an entity, it doesn't follow the entity pattern
//Metadata is a pseudo-DTO this is why it is allowed to be public and used in the api layer
public record Metadata(Integer id, String key, String value, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime deletedAt) {
}