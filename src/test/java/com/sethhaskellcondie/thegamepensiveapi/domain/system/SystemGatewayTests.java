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
    protected System generateEntity() {
        return new System(1, "SystemName", 3, false);
    }

    @Override
    protected SystemRequestDto generateRequestFromEntity(System entity) {
        return new SystemRequestDto(entity.getName(), entity.getGeneration(), entity.isHandheld());
    }

    @Override
    protected System generateAnotherEntity() {
        return new System(2, "AnotherSystemName", 4, true);
    }

    @Override
    protected System generateEmptyEntity() {
        return new System();
    }
}
