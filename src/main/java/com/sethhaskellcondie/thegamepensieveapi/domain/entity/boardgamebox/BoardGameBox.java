package com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityData;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.Entity;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGame;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.SlimBoardGame;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionMalformedEntity;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

public class BoardGameBox implements Entity<BoardGameBoxRequestDto, BoardGameBoxResponseDto> {

    private final EntityData entityData;
    private String title;
    private boolean expansion;
    private boolean standAlone;
    private Integer baseSetId; //this is a BoardGameBox id to the base set if this box is an expansion
    private Integer boardGameId;
    private SlimBoardGame boardGame; //the board game is initially set to null, but can be hydrated in the service

    public BoardGameBox() {
        this.entityData = new EntityData();
    }

    public BoardGameBox(Integer id, String title, boolean isExpansion, boolean isStandAlone, Integer baseSetId, Integer boardGameId,
                        Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt, List<CustomFieldValue> customFieldValues) {
        this.entityData = new EntityData(id, createdAt, updatedAt, deletedAt, customFieldValues);
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

    @Override
    public Integer getId() {
        return entityData.getId();
    }

    @Override
    public String getKey() {
        return Keychain.BOARD_GAME_BOX_KEY;
    }

    @Override
    public List<CustomFieldValue> getCustomFieldValues() {
        return entityData.getCustomFieldValues();
    }

    @Override
    public void setCustomFieldValues(List<CustomFieldValue> customFieldValues) {
        entityData.setCustomFieldValues(customFieldValues);
    }

    @Override
    public boolean isPersisted() {
        return entityData.isPersisted();
    }

    @Override
    public boolean isDeleted() {
        return entityData.isDeleted();
    }

    public Timestamp getCreatedAt() {
        return entityData.getCreatedAt();
    }

    public Timestamp getUpdatedAt() {
        return entityData.getUpdatedAt();
    }

    public Timestamp getDeletedAt() {
        return entityData.getDeletedAt();
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
    public BoardGameBox updateFromRequestDto(BoardGameBoxRequestDto requestDto) {
        this.title = requestDto.title();
        this.expansion = requestDto.isExpansion();
        this.standAlone = requestDto.isStandAlone();
        this.baseSetId = requestDto.baseSetId();
        this.boardGameId = requestDto.boardGameId();
        entityData.setCustomFieldValues(requestDto.customFieldValues());
        this.validate();
        return this;
    }

    @Override
    public BoardGameBoxRequestDto convertToRequestDto() {
        return new BoardGameBoxRequestDto(this.title, this.expansion, this.standAlone, this.baseSetId, this.boardGameId, null, entityData.getCustomFieldValues());
    }

    @Override
    public BoardGameBoxResponseDto convertToResponseDto() {
        return new BoardGameBoxResponseDto(this.getKey(), entityData.getId(), this.title, this.expansion, this.standAlone, this.baseSetId, this.boardGame,
                entityData.getCreatedAt(), entityData.getUpdatedAt(), entityData.getDeletedAt(), entityData.getCustomFieldValues());
    }

    public SlimBoardGameBox convertToSlimBoardGameBox() {
        return new SlimBoardGameBox(
                entityData.getId(),
                this.title,
                this.expansion,
                this.standAlone,
                this.baseSetId,
                entityData.getCreatedAt(),
                entityData.getUpdatedAt(),
                entityData.getDeletedAt(),
                entityData.getCustomFieldValues()
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof BoardGameBox other)) {
            return false;
        }
        if (!other.isPersisted()) {
            return false;
        }
        return Objects.equals(this.getId(), other.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    private void validate() throws ExceptionMalformedEntity {
        if (null == this.title || this.title.isBlank()) {
            throw new ExceptionMalformedEntity("Board Game Box object error, title cannot be blank");
        }
    }
}
