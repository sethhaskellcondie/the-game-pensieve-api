package com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.Entity;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGame;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.SlimBoardGame;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionMalformedEntity;

import java.sql.Timestamp;
import java.util.List;

public class BoardGameBox extends Entity<BoardGameBoxRequestDto, BoardGameBoxResponseDto> {

    private String title;
    private boolean expansion;
    private boolean standAlone;
    private Integer baseSetId; //this is a BoardGameBox id to the base set if this box is an expansion
    private Integer boardGameId;
    private SlimBoardGame boardGame; //the board game is initially set to null, but can be hydrated in the service

    public BoardGameBox() {
        super();
    }

    public BoardGameBox(Integer id, String title, boolean isExpansion, boolean isStandAlone, Integer baseSetId, Integer boardGameId,
                        Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt, List<CustomFieldValue> customFieldValues) {
        super(id, createdAt, updatedAt, deletedAt, customFieldValues);
        this.title = title;
        this.expansion = isExpansion;
        this.standAlone = isStandAlone;
        this.baseSetId = baseSetId;
        if (this.baseSetId == 0) {
            this.baseSetId = null;
        }
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

    public Integer getBoardGameId() {
        return boardGameId;
    }

    public void setBoardGame(BoardGame boardGame) {
        if (null == boardGame.getId()) {
            this.boardGame = null;
            return;
        }
        this.boardGameId = boardGame.getId();
        this.boardGame = boardGame.convertToSlimBoardGame();
    }

    public void setSlimBoardGame(SlimBoardGame slimBoardGame) {
        if (null == slimBoardGame.id()) {
            this.boardGame = null;
            return;
        }
        this.boardGameId = slimBoardGame.id();
        this.boardGame = slimBoardGame;
    }

    public SlimBoardGame getBoardGame() {
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
    public BoardGameBoxRequestDto convertToRequestDto() {
        return new BoardGameBoxRequestDto(this.title, this.expansion, this.standAlone, this.baseSetId, this.boardGameId, null, this.customFieldValues);
    }

    @Override
    public BoardGameBoxResponseDto convertToResponseDto() {
        return new BoardGameBoxResponseDto(this.getKey(), this.id, this.title, this.expansion, this.standAlone, this.baseSetId, this.boardGame,
                this.created_at, this.updated_at, this.deleted_at, this.customFieldValues);
    }

    private void validate() throws ExceptionMalformedEntity {
        if (null == this.title || this.title.isBlank()) {
            throw new ExceptionMalformedEntity("Board Game Box object error, title cannot be blank");
        }
    }

    public SlimBoardGameBox convertToSlimBoardGameBox() {
        return new SlimBoardGameBox(
                this.id,
                this.title,
                this.expansion,
                this.standAlone,
                this.baseSetId,
                this.created_at,
                this.updated_at,
                this.deleted_at,
                this.customFieldValues
        );
    }
}
