package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGateway;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import org.springframework.stereotype.Component;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGatewayAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.EntityService;

import java.util.HashMap;

@Component
public class SystemGateway extends EntityGatewayAbstract<System, SystemRequestDto, SystemResponseDto> implements EntityGateway<System, SystemRequestDto, SystemResponseDto> {
    public SystemGateway(EntityService<System, SystemRequestDto, SystemResponseDto> service) {
        super(service);
    }

    //This method is ONLY here to seed data in the HeartbeatController  TODO update how we seed data and remove this method
    @Deprecated
    public SystemResponseDto createNew(String name, int generation, boolean handheld) throws ExceptionFailedDbValidation {
        return this.createNew(new SystemRequestDto(name, generation, handheld, new HashMap<>(), new HashMap<>()));
    }
}
