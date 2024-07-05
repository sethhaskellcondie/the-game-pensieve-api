package com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgamebox;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityGatewayAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityService;
import org.springframework.stereotype.Component;

@Component
public class BoardGameBoxGateway extends EntityGatewayAbstract<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto>
        implements EntityGateway<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto> {
    public BoardGameBoxGateway(EntityService<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto> service) {
        super(service);
    }
}
