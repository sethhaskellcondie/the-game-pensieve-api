package com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgamebox;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.Entity;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgame.BoardGame;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionMalformedEntity;

import java.sql.Timestamp;
import java.util.List;

public class BoardGameBox extends Entity<BoardGameBoxRequestDto, BoardGameBoxResponseDto> {

    private String title;
    private boolean expansion;
    private boolean standAlone;
    private Integer baseSetId; //this is a BoardGameBox id to the base set if this box is not standAlone
    private Integer boardGameId;
    private BoardGame boardGame; //the board game is initially set to null, but can be hydrated in the service

    public BoardGameBox() {
        super();
    }

    public BoardGameBox(Integer id, String title, boolean isExpansion, boolean isStandAlone, Integer baseSetId, int boardGameId,
                        Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt, List<CustomFieldValue> customFieldValues) {
        super(id, createdAt, updatedAt, deletedAt, customFieldValues);
        this.title = title;
        this.expansion = isExpansion;
        this.standAlone = isStandAlone;
        this.baseSetId = baseSetId;
        this.boardGameId = boardGameId;
        this.boardGame = null;
    }

    public String getTitle() {
        return title;
    }

    public boolean isExpansion() {
        return expansion;
    }

    public boolean isStandAlone() {
        return standAlone;
    }

    public Integer getBaseSetId() {
        return baseSetId;
    }

    public int getBoardGameId() {
        return boardGameId;
    }

    public void setBoardGame(BoardGame boardGame) {
        this.boardGame = boardGame;
    }

    public BoardGame getBoardGame() {
        return boardGame;
    }

    public boolean isBoardGameValid() {
        return null != boardGame;
    }

    @Override
    protected BoardGameBox updateFromRequestDto(BoardGameBoxRequestDto requestDto) {
        this.title = requestDto.title();
        this.expansion = requestDto.isExpansion();
        this.standAlone = requestDto.isStandAlone();
        this.baseSetId = requestDto.baseSetId();
        this.boardGameId = requestDto.boardGameId();
        this.customFieldValues = requestDto.customFieldValues();
        this.validate();
        return this;
    }

    @Override
    public String getKey() {
        return Keychain.BOARD_GAME_BOX_KEY;
    }

    @Override
    protected BoardGameBoxRequestDto convertToRequestDto() {
        return new BoardGameBoxRequestDto(this.title, this.expansion, this.standAlone, this.baseSetId, this.boardGameId, this.boardGame.convertToRequestDto(), this.customFieldValues);
    }

    @Override
    public BoardGameBoxResponseDto convertToResponseDto() {
        return new BoardGameBoxResponseDto(this.getKey(), this.id, this.title, this.expansion, this.standAlone, this.baseSetId, this.boardGame.convertToResponseDto(),
                this.created_at, this.updated_at, this.deleted_at, this.customFieldValues);
    }

    private void validate() throws ExceptionMalformedEntity {
        if (null == this.title || this.title.isBlank()) {
            throw new ExceptionMalformedEntity("Board Game Box object error, title cannot be blank");
        }
    }
}
