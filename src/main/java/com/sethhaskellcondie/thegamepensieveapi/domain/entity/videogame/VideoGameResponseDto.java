package com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame;

import java.sql.Timestamp;
import java.util.List;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.SlimVideoGameBox;

public record VideoGameResponseDto(
    String key,
    int id,
    String title,
    SystemResponseDto system,
    List<SlimVideoGameBox> videoGameBoxes,
    Timestamp createdAt,
    Timestamp updatedAt,
    Timestamp deletedAt,
    List<CustomFieldValue> customFieldValues
) {
}
