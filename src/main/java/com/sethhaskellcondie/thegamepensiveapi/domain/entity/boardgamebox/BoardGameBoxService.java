package com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgamebox;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityService;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityServiceAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgame.BoardGameRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterService;
import org.springframework.stereotype.Service;

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
        //TODO: finish this
        //if the board game isn't created yet then create the board game and get the id
        //then add that to the board game and insert the board game box
        //when getting the board game box that will also get the board game as well
        return super.createNew(requestDto);
    }

    @Override
    public BoardGameBox updateExisting(BoardGameBox boardGameBox) {
        //TODO: finish this
        //don't insert the board game there will be an existing board game id, validate that board game before updating the box
        return super.updateExisting(boardGameBox);
    }


}
