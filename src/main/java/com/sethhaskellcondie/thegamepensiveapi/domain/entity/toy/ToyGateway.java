package com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityGatewayAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityService;
import org.springframework.stereotype.Component;

@Component
public class ToyGateway extends EntityGatewayAbstract<Toy, ToyRequestDto, ToyResponseDto> implements EntityGateway<Toy, ToyRequestDto, ToyResponseDto> {
    public ToyGateway(EntityService<Toy, ToyRequestDto, ToyResponseDto> service) {
        super(service);
    }
}
