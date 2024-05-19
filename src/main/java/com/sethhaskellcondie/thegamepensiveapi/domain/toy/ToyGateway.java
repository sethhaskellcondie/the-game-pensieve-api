package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGateway;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import org.springframework.stereotype.Component;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGatewayAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.EntityService;

import java.util.HashMap;

@Component
public class ToyGateway extends EntityGatewayAbstract<Toy, ToyRequestDto, ToyResponseDto> implements EntityGateway<Toy, ToyRequestDto, ToyResponseDto> {
    public ToyGateway(EntityService<Toy, ToyRequestDto, ToyResponseDto> service) {
        super(service);
    }

    //This method is ONLY here to seed data in the HeartbeatController  TODO update how we seed data and remove this method
    @Deprecated
    public ToyResponseDto createNew(String name, String set) throws ExceptionFailedDbValidation {
        return this.createNew(new ToyRequestDto(name, set, new HashMap<>(), new HashMap<>()));
    }
}
