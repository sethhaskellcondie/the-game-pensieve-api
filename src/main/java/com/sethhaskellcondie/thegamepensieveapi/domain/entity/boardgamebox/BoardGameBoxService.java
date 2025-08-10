package com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityService;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityServiceAbstract;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGame;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGameRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGameRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGameService;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionMalformedEntity;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.MultiException;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class BoardGameBoxService extends EntityServiceAbstract<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto>
        implements EntityService<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto> {

    private final BoardGameRepository boardGameRepository;
    private final BoardGameService boardGameService;

    public BoardGameBoxService(EntityRepository<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto> repository,
                               FilterService filterService, BoardGameRepository boardGameRepository, BoardGameService boardGameService) {
        super(repository, filterService);
        this.boardGameRepository = boardGameRepository;
        this.boardGameService = boardGameService;
    }

    @Override
    public List<BoardGameBox> getWithFilters(List<FilterRequestDto> dtoFilters) {
        final MultiException multiException = new MultiException();
        final List<BoardGameBox> boardGameBoxes = super.getWithFilters(dtoFilters);
        for (BoardGameBox boardGameBox : boardGameBoxes) {
            try {
                boardGameBox.setBoardGame(boardGameRepository.getById(boardGameBox.getBoardGameId()));
            } catch (Exception e) {
                multiException.addException(e);
            }
        }
        if (!multiException.isEmpty()) {
            throw new ExceptionMalformedEntity(multiException.getExceptions());
        }
        return boardGameBoxes;
    }

    @Override
    public BoardGameBox getById(int id) {
        final BoardGameBox boardGameBox = super.getById(id);
        try {
            boardGameBox.setBoardGame(boardGameRepository.getById(boardGameBox.getBoardGameId()));
        } catch (Exception e) {
            throw new ExceptionMalformedEntity("Problem getting parent board game from the database, board game box with title: '"
            + boardGameBox.getTitle() + "'", e);
        }
        return boardGameBox;
    }

    @Override
    @Transactional
    public BoardGameBox createNew(BoardGameBoxRequestDto requestDto) {
        BoardGameBox newBoardGameBox = new BoardGameBox().updateFromRequestDto(requestDto);
        int boardGameId = 0;
        if (requestDto.boardGameId() != null && requestDto.boardGameId() > 0) {
            boardGameId = requestDto.boardGameId();
        }
        if (boardGameId == 0) {
            BoardGame boardGame = new BoardGame();
            List<CustomFieldValue> boardGameCustomFields = new ArrayList<>();
            String boardGameTitle = requestDto.title();
            
            if (requestDto.boardGame() != null) {
                boardGameTitle = requestDto.boardGame().title();
                if (requestDto.boardGame().customFieldValues() != null) {
                    boardGameCustomFields = requestDto.boardGame().customFieldValues();
                }
            }
            
            boardGame.updateFromRequestDto(new BoardGameRequestDto(boardGameTitle, boardGameCustomFields));
            boardGame = boardGameRepository.insert(boardGame);
            newBoardGameBox.setBoardGame(boardGame);
        } else {
            try {
                BoardGame boardGame = boardGameRepository.getById(boardGameId);
                newBoardGameBox.setBoardGame(boardGame);
            } catch (ExceptionResourceNotFound exception) {
                throw new ExceptionMalformedEntity("Cannot create new BoardGameBox, the provided boardGameId: " + boardGameId
                        + " is not a valid boardGameId. Provide 'null' as the boardGameId to create a new Board Game", exception);
            }
        }
        final BoardGameBox insertedBoardGameBox = repository.insert(newBoardGameBox);
        insertedBoardGameBox.setBoardGame(boardGameRepository.getById(insertedBoardGameBox.getBoardGameId()));
        return insertedBoardGameBox;
    }

    @Override
    @Transactional
    public BoardGameBox updateExisting(int id, BoardGameBoxRequestDto requestDto) {
        BoardGameBox boardGameBox = repository.getById(id);
        if (!Objects.equals(boardGameBox.getBoardGameId(), requestDto.boardGameId())) {
            deleteParentBoardGameIfNeeded(boardGameBox.getBoardGameId());
        }
        boardGameBox.updateFromRequestDto(requestDto);
        if (!boardGameBox.isBoardGameValid()) {
            try {
                BoardGame boardGame = boardGameRepository.getById(boardGameBox.getBoardGameId());
                boardGameBox.setBoardGame(boardGame);
            } catch (ExceptionResourceNotFound exception) {
                throw new ExceptionMalformedEntity("Cannot update BoardGameBox, the provided boardGameId: " + boardGameBox.getBoardGameId()
                        + " is not a valid boardGameId.", exception);
            }
        }
        final BoardGameBox updatedBoardGameBox = repository.update(boardGameBox);
        updatedBoardGameBox.setBoardGame(boardGameRepository.getById(updatedBoardGameBox.getBoardGameId()));
        return updatedBoardGameBox;
    }

    @Override
    @Transactional
    public void deleteById(int id) {
        BoardGameBox boardGameBox = repository.getById(id);
        deleteParentBoardGameIfNeeded(boardGameBox.getBoardGameId());
        repository.deleteById(id);
    }

    public void deleteParentBoardGameIfNeeded(int boardGameId) {
        BoardGame boardGame = boardGameService.getById(boardGameId);
        if (boardGame.getBoardGameBoxes().size() == 1) {
            boardGameRepository.deleteById(boardGameId);
        }
    }

    public int getIdByTitleAndBoardGameId(String title, int boardGameId) {
        BoardGameBoxRepository boardGameBoxRepository = (BoardGameBoxRepository) repository;
        return boardGameBoxRepository.getIdByTitleAndBoardGameId(title, boardGameId);
    }
}
