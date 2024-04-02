package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGatewayTests;

import static org.mockito.Mockito.mock;

public class SystemGatewayTests extends EntityGatewayTests<System, SystemRequestDto, SystemResponseDto> {
    @Override
    protected void setupGatewayMockService() {
        service = mock(SystemService.class);
        gateway = new SystemGateway(service);
    }

    @Override
    protected void setupFactory() {
        factory = new SystemFactory();
    }

}
