package com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityData;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.Entity;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox.SlimBoardGameBox;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionMalformedEntity;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BoardGame implements Entity<BoardGameRequestDto, BoardGameResponseDto> {

    private final EntityData entityData;
    private String title;
    private List<SlimBoardGameBox> boardGameBoxes;

    public BoardGame() {
        this.entityData = new EntityData();
        this.boardGameBoxes = new ArrayList<>();
    }

    public BoardGame(Integer id, String title, Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt, List<CustomFieldValue> customFieldValues) {
        this.entityData = new EntityData(id, createdAt, updatedAt, deletedAt, customFieldValues);
        this.title = title;
        this.boardGameBoxes = new ArrayList<>();
    }

    @Override
    public Integer getId() {
        return entityData.getId();
    }

    @Override
    public String getKey() {
        return Keychain.BOARD_GAME_KEY;
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

    public List<SlimBoardGameBox> getBoardGameBoxes() {
        return boardGameBoxes;
    }

    public void setBoardGameBoxes(List<SlimBoardGameBox> boardGameBoxes) {
        if (null == boardGameBoxes) {
            this.boardGameBoxes = new ArrayList<>();
            return;
        }
        this.boardGameBoxes = boardGameBoxes;
    }

    public boolean isBoardGameBoxesValid() {
        return !boardGameBoxes.isEmpty();
    }

    @Override
    public BoardGame updateFromRequestDto(BoardGameRequestDto requestDto) {
        this.title = requestDto.title();
        this.boardGameBoxes = new ArrayList<>();
        entityData.setCustomFieldValues(requestDto.customFieldValues());
        this.validate();
        return this;
    }

    @Override
    public BoardGameRequestDto convertToRequestDto() {
        return new BoardGameRequestDto(this.title, entityData.getCustomFieldValues());
    }

    @Override
    public BoardGameResponseDto convertToResponseDto() {
        return new BoardGameResponseDto(this.getKey(), entityData.getId(), this.title, this.boardGameBoxes,
                entityData.getCreatedAt(), entityData.getUpdatedAt(), entityData.getDeletedAt(), entityData.getCustomFieldValues());
    }

    public SlimBoardGame convertToSlimBoardGame() {
        return new SlimBoardGame(entityData.getId(), this.title,
                entityData.getCreatedAt(), entityData.getUpdatedAt(), entityData.getDeletedAt(), entityData.getCustomFieldValues());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof BoardGame other)) {
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
            throw new ExceptionMalformedEntity("Board Game object error, title cannot be blank");
        }
    }
}
