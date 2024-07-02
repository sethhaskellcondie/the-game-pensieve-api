package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame;

import java.sql.Timestamp;
import java.util.List;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;

public record VideoGameResponseDto(
	String key,
	int id,
	String title,
	int systemId,
	String systemName,
	Timestamp createdAt,
	Timestamp updatedAt,
	Timestamp deletedAt,
	List<CustomFieldValue> customFieldValues
) {
}
