package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGatewayTests;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;

import static org.mockito.Mockito.mock;

public class SystemGatewayTests extends EntityGatewayTests<System, SystemRequestDto, SystemResponseDto> {

    private String startsWith = "SuperConsole";

    @Override
    protected void setupGatewayMockService() {
        service = mock(SystemService.class);
        gateway = new SystemGateway(service);
    }

    @Override
    protected void setupFactory() {
        factory = new SystemFactory(startsWith);
    }

    @Override
    protected Filter startsWithFilter() {
        return new Filter("system", "name", Filter.OPERATOR_STARTS_WITH, startsWith);
    }
}
