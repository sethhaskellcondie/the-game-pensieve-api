package com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgamebox;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgame.BoardGameRequestDto;

import java.util.List;

public record BoardGameBoxRequestDto(
        String title,
        boolean isExpansion,
        boolean isStandAlone,
        Integer baseSetId, //this is a board game box id
        Integer boardGameId, //this is a board game id
        BoardGameRequestDto newBoardGame,
        List<CustomFieldValue> customFieldValues
) {
}
