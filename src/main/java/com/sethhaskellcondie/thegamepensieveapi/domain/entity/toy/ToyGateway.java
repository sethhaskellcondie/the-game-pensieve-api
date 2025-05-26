package com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityGatewayAbstract;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityService;
import org.springframework.stereotype.Component;

@Component
public class ToyGateway extends EntityGatewayAbstract<Toy, ToyRequestDto, ToyResponseDto> implements EntityGateway<Toy, ToyRequestDto, ToyResponseDto> {
    public ToyGateway(EntityService<Toy, ToyRequestDto, ToyResponseDto> service) {
        super(service);
    }
}
