package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import org.springframework.stereotype.Component;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGatewayImpl;
import com.sethhaskellcondie.thegamepensiveapi.domain.EntityService;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;

@Component
public class ToyGatewayImpl extends EntityGatewayImpl<Toy, ToyRequestDto, ToyResponseDto> implements ToyGateway {
    public ToyGatewayImpl(EntityService<Toy, ToyRequestDto, ToyResponseDto> service) {
        super(service);
    }

    @Override
    public ToyResponseDto createNew(ToyRequestDto toyRequestDto) throws ExceptionFailedDbValidation {
        Toy newToy = new Toy();
        newToy.updateFromRequest(toyRequestDto);
        return service.createNew(newToy).convertToResponseDto();
    }
}
