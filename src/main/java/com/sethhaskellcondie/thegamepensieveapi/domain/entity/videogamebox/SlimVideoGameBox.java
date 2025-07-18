package com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemResponseDto;

import java.sql.Timestamp;
import java.util.List;

public record SlimVideoGameBox(
        Integer id,
        String title,
        SystemResponseDto system,
        boolean physical,
        boolean collection,
        Timestamp createdAt,
        Timestamp updatedAt,
        Timestamp deletedAt,
        List<CustomFieldValue> customFieldValues
) {
}
