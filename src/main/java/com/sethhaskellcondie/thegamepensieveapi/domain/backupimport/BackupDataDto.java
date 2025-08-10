package com.sethhaskellcondie.thegamepensieveapi.domain.backupimport;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox.BoardGameBoxResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxResponseDto;

import java.util.List;

/**
 * BackupData is a special DTO that contains all the entries in the database this sent to the api to be written to a file
 * or returned in a request.
 */
public record BackupDataDto(List<CustomField> customFields,
                            List<ToyResponseDto> toys,
                            List<SystemResponseDto> systems,
                            List<VideoGameBoxResponseDto> videoGameBoxes,
                            List<BoardGameBoxResponseDto> boardGameBoxes
) {
}
