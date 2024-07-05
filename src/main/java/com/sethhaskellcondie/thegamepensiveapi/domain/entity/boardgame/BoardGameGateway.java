package com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgame;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityGatewayAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityService;
import org.springframework.stereotype.Component;

@Component
public class BoardGameGateway extends EntityGatewayAbstract<BoardGame, BoardGameRequestDto, BoardGameResponseDto>
        implements EntityGateway<BoardGame, BoardGameRequestDto, BoardGameResponseDto> {
    public BoardGameGateway(EntityService<BoardGame, BoardGameRequestDto, BoardGameResponseDto> service) {
        super(service);
    }
}
