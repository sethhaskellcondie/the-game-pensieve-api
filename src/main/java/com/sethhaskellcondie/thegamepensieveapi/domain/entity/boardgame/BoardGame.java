package com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.Entity;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox.SlimBoardGameBox;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionMalformedEntity;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class BoardGame extends Entity<BoardGameRequestDto, BoardGameResponseDto> {

    private String title;
    private List<SlimBoardGameBox> boardGameBoxes;

    public BoardGame() {
        super();
        this.boardGameBoxes = new ArrayList<>();
    }

    public BoardGame(Integer id, String title, Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt, List<CustomFieldValue> customFieldValues) {
        super(id, createdAt, updatedAt, deletedAt, customFieldValues);
        this.title = title;
        this.boardGameBoxes = new ArrayList<>();
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
        this.setCustomFieldValues(requestDto.customFieldValues());
        this.validate();
        return this;
    }

    @Override
    public BoardGameRequestDto convertToRequestDto() {
        return new BoardGameRequestDto(this.title, this.customFieldValues);
    }

    @Override
    public BoardGameResponseDto convertToResponseDto() {
        return new BoardGameResponseDto(this.getKey(), this.id, this.title, this.boardGameBoxes, this.created_at, this.updated_at, this.deleted_at, this.customFieldValues);
    }

    @Override
    public String getKey() {
        return Keychain.BOARD_GAME_KEY;
    }

    public SlimBoardGame convertToSlimBoardGame() {
        return new SlimBoardGame(this.id, this.title, this.created_at, this.updated_at, this.deleted_at, this.customFieldValues);
    }

    private void validate() throws ExceptionMalformedEntity {
        if (null == this.title || this.title.isBlank()) {
            throw new ExceptionMalformedEntity("Board Game object error, title cannot be blank");
        }
    }
}
