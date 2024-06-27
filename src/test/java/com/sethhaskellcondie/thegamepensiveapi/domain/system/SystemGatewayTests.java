package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityGatewayTests;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemService;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterRequestDto;

import static org.mockito.Mockito.mock;

@Deprecated
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
    protected FilterRequestDto startsWithFilter() {
        return new FilterRequestDto("system", "name", Filter.OPERATOR_STARTS_WITH, startsWith);
    }
}
