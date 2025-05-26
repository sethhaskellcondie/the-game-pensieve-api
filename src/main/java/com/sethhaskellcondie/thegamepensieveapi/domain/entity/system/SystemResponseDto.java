package com.sethhaskellcondie.thegamepensieveapi.domain.entity.system;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;

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
