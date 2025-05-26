package com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityGatewayAbstract;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityService;
import org.springframework.stereotype.Component;

@Component
public class BoardGameGateway extends EntityGatewayAbstract<BoardGame, BoardGameRequestDto, BoardGameResponseDto>
        implements EntityGateway<BoardGame, BoardGameRequestDto, BoardGameResponseDto> {
    public BoardGameGateway(EntityService<BoardGame, BoardGameRequestDto, BoardGameResponseDto> service) {
        super(service);
    }
}
