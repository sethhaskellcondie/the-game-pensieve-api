package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameRequestDto;

import java.util.List;

public record VideoGameBoxRequestDto(
        String title,
        int systemId,
        List<Integer> existingVideoGameIds,
        List<VideoGameRequestDto> newVideoGames,
        boolean isPhysical,
        List<CustomFieldValue> customFieldValues
) {
}
