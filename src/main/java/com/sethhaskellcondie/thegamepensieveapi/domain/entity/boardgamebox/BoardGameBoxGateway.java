package com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityGatewayAbstract;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityService;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import org.springframework.stereotype.Component;

@Component
public class BoardGameBoxGateway extends EntityGatewayAbstract<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto>
        implements EntityGateway<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto> {
    public BoardGameBoxGateway(EntityService<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto> service) {
        super(service);
    }

    @Override
    public BoardGameBoxResponseDto updateExisting(int id, BoardGameBoxRequestDto requestDto) {
        validateBoardGameBoxUpdate(requestDto);
        return service.updateExisting(id, requestDto).convertToResponseDto();
    }

    private void validateBoardGameBoxUpdate(BoardGameBoxRequestDto requestDto) {
        if (requestDto.boardGameId() == null) {
            throw new ExceptionFailedDbValidation("BoardGameBox must have a valid parent Board Game. BoardGameId cannot be null.");
        }
        if (requestDto.boardGameId() <= 0) {
            throw new ExceptionFailedDbValidation("BoardGameBox must have a valid parent Board Game. BoardGameId must be greater than 0.");
        }
    }
}
