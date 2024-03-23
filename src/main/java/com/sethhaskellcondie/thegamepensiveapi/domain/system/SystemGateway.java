package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGateway;
import org.springframework.stereotype.Component;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGatewayAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.EntityService;

@Component
public class SystemGateway extends EntityGatewayAbstract<System, SystemRequestDto, SystemResponseDto> implements EntityGateway<System, SystemRequestDto, SystemResponseDto> {
    public SystemGateway(EntityService<System, SystemRequestDto, SystemResponseDto> service) {
        super(service);
    }
}
