package com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;

import java.sql.Timestamp;
import java.util.List;

public record SlimBoardGame(
        Integer id,
        String title,
        Timestamp createdAt,
        Timestamp updatedAt,
        Timestamp deletedAt,
        List<CustomFieldValue> customFieldValues
) {
}
