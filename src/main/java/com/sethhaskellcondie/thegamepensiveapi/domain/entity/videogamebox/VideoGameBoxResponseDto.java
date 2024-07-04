package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameResponseDto;

import java.sql.Timestamp;
import java.util.List;

public record VideoGameBoxResponseDto(
        String key,
        int id,
        String title,
        int systemId,
        String systemName,
        List<Integer> videoGameIds,
        List<VideoGameResponseDto> videoGames,
        boolean isPhysical,
        boolean isCollection,
        Timestamp createdAt,
        Timestamp updatedAt,
        Timestamp deletedAt,
        List<CustomFieldValue> customFieldValues
) {
}
