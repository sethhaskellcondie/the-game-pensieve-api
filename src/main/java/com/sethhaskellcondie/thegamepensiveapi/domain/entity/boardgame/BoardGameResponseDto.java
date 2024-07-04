package com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgame;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgamebox.BoardGameBoxResponseDto;

import java.sql.Timestamp;
import java.util.List;

public record BoardGameResponseDto(
        String key,
        int id,
        String title,
        List<BoardGameBoxResponseDto> boardGameBoxes,
        Timestamp createdAt,
        Timestamp updatedAt,
        Timestamp deletedAt,
        List<CustomFieldValue> customFieldValues
) { }
