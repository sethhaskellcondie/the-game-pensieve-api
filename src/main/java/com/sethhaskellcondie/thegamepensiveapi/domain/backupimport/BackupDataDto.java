package com.sethhaskellcondie.thegamepensiveapi.domain.backupimport;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgame.BoardGameRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgamebox.BoardGameBoxRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.ToyRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox.VideoGameBoxRequestDto;

import java.util.List;

/**
 * BackupData is a special DTO that contains all the entries in the database this sent to the api to be written to a file
 * or returned in a request.
 */
public record BackupDataDto(List<CustomField> customFields,
                            List<ToyRequestDto> toys,
                            List<SystemRequestDto> systems,
                            List<VideoGameRequestDto> videoGames,
                            List<VideoGameBoxRequestDto> videoGameBoxes,
                            List<BoardGameRequestDto> boardGames,
                            List<BoardGameBoxRequestDto> boardGameBoxes
) {
}
