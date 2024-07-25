package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame;

import java.sql.Timestamp;
import java.util.List;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox.SlimVideoGameBox;

public record VideoGameResponseDto(
	String key,
	int id,
	String title,
	int systemId,
	SystemResponseDto system,
	List<SlimVideoGameBox> videoGameBoxes,
	Timestamp createdAt,
	Timestamp updatedAt,
	Timestamp deletedAt,
	List<CustomFieldValue> customFieldValues
) {
}
