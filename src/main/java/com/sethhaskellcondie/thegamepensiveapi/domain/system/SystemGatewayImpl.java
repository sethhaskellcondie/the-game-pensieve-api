package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import org.springframework.stereotype.Component;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGatewayImpl;
import com.sethhaskellcondie.thegamepensiveapi.domain.EntityService;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;

@Component
public class SystemGatewayImpl extends EntityGatewayImpl<System, SystemRequestDto, SystemResponseDto> implements SystemGateway {
    public SystemGatewayImpl(EntityService<System, SystemRequestDto, SystemResponseDto> service) {
        super(service);
    }

    @Override
    public SystemResponseDto createNew(SystemRequestDto requestDto) throws ExceptionFailedDbValidation {
        System newSystem = new System(); //I can't do this line with generics...perhaps I can do it with reflection?
        newSystem.updateFromRequest(requestDto);
        return service.createNew(newSystem).convertToResponseDto();
    }
}
