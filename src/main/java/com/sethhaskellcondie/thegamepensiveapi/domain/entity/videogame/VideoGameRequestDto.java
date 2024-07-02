package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame;

import java.util.List;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;

public record VideoGameRequestDto(
	String title,
	int systemId,
	List<CustomFieldValue> customFieldValues
) {
}
