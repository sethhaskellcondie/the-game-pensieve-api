package com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemResponseDto;

import java.sql.Timestamp;
import java.util.List;

public record SlimVideoGame(
        Integer id,
        String title,
        SystemResponseDto system,
        Timestamp createdAt,
        Timestamp updatedAt,
        Timestamp deletedAt,
        List<CustomFieldValue> customFieldValues
) {
}
