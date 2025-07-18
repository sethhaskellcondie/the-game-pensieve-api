package com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;

import java.sql.Timestamp;
import java.util.List;

public record SlimBoardGameBox(
        Integer id,
        String title,
        boolean isExpansion,
        boolean isStandAlone,
        Integer baseSetId,
        Timestamp createdAt,
        Timestamp updatedAt,
        Timestamp deletedAt,
        List<CustomFieldValue> customFieldValues
) {
}
