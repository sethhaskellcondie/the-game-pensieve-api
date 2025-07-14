package com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameRequestDto;

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
