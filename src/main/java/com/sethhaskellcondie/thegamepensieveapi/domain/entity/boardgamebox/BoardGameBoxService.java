package com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityService;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityServiceAbstract;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGame;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGameRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGameRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionMalformedEntity;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class BoardGameBoxService extends EntityServiceAbstract<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto>
        implements EntityService<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto> {

    private final BoardGameRepository boardGameRepository;

    public BoardGameBoxService(EntityRepository<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto> repository, FilterService filterService, BoardGameRepository boardGameRepository) {
        super(repository, filterService);
        this.boardGameRepository = boardGameRepository;
    }

    @Override
    public List<BoardGameBox> getWithFilters(List<FilterRequestDto> dtoFilters) {
        final List<BoardGameBox> boardGameBoxes = super.getWithFilters(dtoFilters);
        for (BoardGameBox boardGameBox : boardGameBoxes) {
            boardGameBox.setBoardGame(boardGameRepository.getById(boardGameBox.getBoardGameId()));
        }
        return boardGameBoxes;
    }

    @Override
    public BoardGameBox getById(int id) {
        final BoardGameBox boardGameBox = super.getById(id);
        boardGameBox.setBoardGame(boardGameRepository.getById(boardGameBox.getBoardGameId()));
        return boardGameBox;
    }

    @Override
    public BoardGameBox createNew(BoardGameBoxRequestDto requestDto) {
        BoardGameBox newBoardGameBox = new BoardGameBox().updateFromRequestDto(requestDto);
        int boardGameId = 0;
        if (requestDto.boardGameId() != null && requestDto.boardGameId() > 0) {
            boardGameId = requestDto.boardGameId();
        }
        if (boardGameId == 0) {
            BoardGame boardGame = new BoardGame();
            boardGame.updateFromRequestDto(new BoardGameRequestDto(requestDto.title(), new ArrayList<>()));
            boardGame = boardGameRepository.insert(boardGame);
            newBoardGameBox.setBoardGame(boardGame);
        } else {
            //validate the boardGameId passed in
            try {
                BoardGame boardGame = boardGameRepository.getById(boardGameId);
                newBoardGameBox.setBoardGame(boardGame);
            } catch (ExceptionResourceNotFound exception) {
                throw new ExceptionMalformedEntity("Cannot create new BoardGameBox, the provided boardGameId: " + boardGameId
                        + " is not a valid boardGameId. Provide 'null' as the boardGameId to create a new Board Game");
            }
        }
        final BoardGameBox insertedBoardGameBox = repository.insert(newBoardGameBox);
        insertedBoardGameBox.setBoardGame(boardGameRepository.getById(insertedBoardGameBox.getBoardGameId()));
        return insertedBoardGameBox;
    }

    @Override
    public BoardGameBox updateExisting(int id, BoardGameBoxRequestDto requestDto) {
        BoardGameBox boardGameBox = repository.getById(id);
        boardGameBox.updateFromRequestDto(requestDto);
        if (!boardGameBox.isBoardGameValid()) {
            try {
                BoardGame boardGame = boardGameRepository.getById(boardGameBox.getBoardGameId());
                boardGameBox.setBoardGame(boardGame);
            } catch (ExceptionResourceNotFound exception) {
                throw new ExceptionMalformedEntity("Cannot update BoardGameBox, the provided boardGameId: " + boardGameBox.getBoardGameId()
                        + " is not a valid boardGameId.");
            }
        }
        final BoardGameBox updatedBoardGameBox = repository.update(boardGameBox);
        updatedBoardGameBox.setBoardGame(boardGameRepository.getById(updatedBoardGameBox.getBoardGameId()));
        return updatedBoardGameBox;
    }


}
