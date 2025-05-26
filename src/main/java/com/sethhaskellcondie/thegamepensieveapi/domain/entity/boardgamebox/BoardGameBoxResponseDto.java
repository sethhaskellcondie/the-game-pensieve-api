package com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGameResponseDto;

import java.sql.Timestamp;
import java.util.List;

public record BoardGameBoxResponseDto(
        String key,
        int id,
        String title,
        boolean isExpansion,
        boolean isStandAlone,
        Integer baseSetId,
        BoardGameResponseDto boardGame,
        Timestamp createdAt,
        Timestamp updatedAt,
        Timestamp deletedAt,
        List<CustomFieldValue> customFieldValues
) {
}
