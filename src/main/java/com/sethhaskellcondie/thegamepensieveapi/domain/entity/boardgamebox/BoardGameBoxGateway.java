package com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityGatewayAbstract;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityService;
import org.springframework.stereotype.Component;

@Component
public class BoardGameBoxGateway extends EntityGatewayAbstract<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto>
        implements EntityGateway<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto> {
    public BoardGameBoxGateway(EntityService<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto> service) {
        super(service);
    }
}
