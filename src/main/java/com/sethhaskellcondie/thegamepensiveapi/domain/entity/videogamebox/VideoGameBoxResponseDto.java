package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;

import java.sql.Timestamp;
import java.util.List;

public record VideoGameBoxResponseDto(
        String key,
        int id,
        String title,
        int systemId,
        String systemName,
        boolean isPhysical,
        boolean isCollection,
        Timestamp createdAt,
        Timestamp updatedAt,
        Timestamp deletedAt,
        List<CustomFieldValue> customFieldValues
) {
}
