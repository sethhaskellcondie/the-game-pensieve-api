package com.sethhaskellcondie.thegamepensieveapi.domain.metadata;

import java.sql.Timestamp;

//Metadata is NOT an entity, it doesn't follow the entity pattern
//Metadata is a pseudo-DTO this is why it is allowed to be public and used in the api layer
//Timestamps use java.sql.Timestamp to match the rest of the system, this serializes to epoch-millis
public record Metadata(Integer id, String key, String value, Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt) {
}