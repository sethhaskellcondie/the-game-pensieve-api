package com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame;

import java.util.List;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;

public record VideoGameRequestDto(
	String title,
	int systemId,
	List<CustomFieldValue> customFieldValues
) {
}
