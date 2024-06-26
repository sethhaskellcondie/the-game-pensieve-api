package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;

import java.sql.Timestamp;
import java.util.List;

public record SystemResponseDto(
        String key,
        int id,
        String name,
        int generation,
        boolean handheld,
        Timestamp createdAt,
        Timestamp updatedAt,
        Timestamp deletedAt,
        List<CustomFieldValue> customFieldValues
) { }
