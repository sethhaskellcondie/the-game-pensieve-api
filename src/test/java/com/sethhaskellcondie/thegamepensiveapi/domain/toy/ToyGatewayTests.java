package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGatewayTests;

import static org.mockito.Mockito.mock;

public class ToyGatewayTests extends EntityGatewayTests<Toy, ToyRequestDto, ToyResponseDto> {
    @Override
    protected void setupGatewayMockService() {
        service = mock(ToyService.class);
        gateway = new ToyGateway(service);
    }

    @Override
    protected void setupFactory() {
        factory = new ToyFactory();
    }
}
