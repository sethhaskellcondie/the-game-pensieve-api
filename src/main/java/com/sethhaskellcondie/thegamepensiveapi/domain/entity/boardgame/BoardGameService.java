package com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgame;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityService;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityServiceAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgamebox.BoardGameBoxRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterService;
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
            boardGame.setBoardGameBoxes(boardGameBoxRepository.getBoardGameBoxesByBoardGameId(boardGame.getId()));
        }
        return boardGames;
    }

    @Override
    public BoardGame getById(int id) {
        final BoardGame boardGame = super.getById(id);
        boardGame.setBoardGameBoxes(boardGameBoxRepository.getBoardGameBoxesByBoardGameId(boardGame.getId()));
        return boardGame;
    }
}
