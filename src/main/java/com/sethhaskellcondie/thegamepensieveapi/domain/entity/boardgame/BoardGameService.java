package com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityService;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityServiceAbstract;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox.BoardGameBoxRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BoardGameService extends EntityServiceAbstract<BoardGame, BoardGameRequestDto, BoardGameResponseDto>
        implements EntityService<BoardGame, BoardGameRequestDto, BoardGameResponseDto> {

    private final BoardGameBoxRepository boardGameBoxRepository;

    public BoardGameService(EntityRepository<BoardGame, BoardGameRequestDto, BoardGameResponseDto> repository, FilterService filterService, BoardGameBoxRepository boardGameBoxRepository) {
        super(repository, filterService);
        this.boardGameBoxRepository = boardGameBoxRepository;
    }

    @Override
    public List<BoardGame> getWithFilters(List<FilterRequestDto> dtoFilters) {
        final List<BoardGame> boardGames = super.getWithFilters(dtoFilters);
        for (BoardGame boardGame : boardGames) {
            boardGame.setBoardGameBoxes(boardGameBoxRepository.getSlimBoardGameBoxesByBoardGameId(boardGame.getId()));
        }
        return boardGames;
    }

    @Override
    public BoardGame getById(int id) {
        final BoardGame boardGame = super.getById(id);
        boardGame.setBoardGameBoxes(boardGameBoxRepository.getSlimBoardGameBoxesByBoardGameId(boardGame.getId()));
        return boardGame;
    }

    @Override
    public BoardGame createNew(BoardGameRequestDto boardGameRequestDto) {
        BoardGame boardGame = new BoardGame().updateFromRequestDto(boardGameRequestDto);
        return repository.insert(boardGame);
    }
}
