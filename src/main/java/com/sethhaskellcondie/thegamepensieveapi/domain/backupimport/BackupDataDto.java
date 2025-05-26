package com.sethhaskellcondie.thegamepensieveapi.domain.backupimport;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGameRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox.BoardGameBoxRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxRequestDto;

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
