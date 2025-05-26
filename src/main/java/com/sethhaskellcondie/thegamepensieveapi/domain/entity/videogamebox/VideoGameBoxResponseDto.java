package com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.SlimVideoGame;

import java.sql.Timestamp;
import java.util.List;

public record VideoGameBoxResponseDto(
        String key,
        int id,
        String title,
        SystemResponseDto system,
        List<SlimVideoGame> videoGames,
        boolean isPhysical,
        boolean isCollection,
        Timestamp createdAt,
        Timestamp updatedAt,
        Timestamp deletedAt,
        List<CustomFieldValue> customFieldValues
) {
}
