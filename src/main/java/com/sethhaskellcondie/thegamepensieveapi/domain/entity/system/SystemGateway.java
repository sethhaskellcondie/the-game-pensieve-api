package com.sethhaskellcondie.thegamepensieveapi.domain.entity.system;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityGatewayAbstract;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityService;
import org.springframework.stereotype.Component;

@Component
public class SystemGateway extends EntityGatewayAbstract<System, SystemRequestDto, SystemResponseDto> implements EntityGateway<System, SystemRequestDto, SystemResponseDto> {
    public SystemGateway(EntityService<System, SystemRequestDto, SystemResponseDto> service) {
        super(service);
    }
}
