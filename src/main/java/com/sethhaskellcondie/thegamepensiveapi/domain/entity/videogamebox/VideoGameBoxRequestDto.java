package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;

import java.util.List;

public record VideoGameBoxRequestDto(
        String title,
        int systemId,
        List<Integer> videoGameIds,
        boolean isPhysical,
        boolean isCollection,
        List<CustomFieldValue> customFieldValues
) {
}
