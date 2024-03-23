package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import org.springframework.stereotype.Component;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGatewayAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.EntityService;

@Component
public class ToyGatewayAbstract extends EntityGatewayAbstract<Toy, ToyRequestDto, ToyResponseDto> implements ToyGateway {
    public ToyGatewayAbstract(EntityService<Toy, ToyRequestDto, ToyResponseDto> service) {
        super(service);
    }
}
