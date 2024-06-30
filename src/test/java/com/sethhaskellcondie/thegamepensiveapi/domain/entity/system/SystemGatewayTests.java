package com.sethhaskellcondie.thegamepensiveapi.domain.entity.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityGatewayTests;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterRequestDto;

import static org.mockito.Mockito.mock;

//Deprecated: This project is going to use full SpringBootTest Integration tests to test entity functionality
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
