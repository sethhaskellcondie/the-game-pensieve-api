package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGateway;
import org.springframework.stereotype.Component;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGatewayAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.EntityService;

@Component
public class ToyGateway extends EntityGatewayAbstract<Toy, ToyRequestDto, ToyResponseDto> implements EntityGateway<Toy, ToyRequestDto, ToyResponseDto> {
    public ToyGateway(EntityService<Toy, ToyRequestDto, ToyResponseDto> service) {
        super(service);
    }
}
