package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGatewayTests;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterRequestDto;

import static org.mockito.Mockito.mock;

@Deprecated
public class ToyGatewayTests extends EntityGatewayTests<Toy, ToyRequestDto, ToyResponseDto> {

    private String startsWith = "Tacocat!";

    @Override
    protected void setupGatewayMockService() {
        service = mock(ToyService.class);
        gateway = new ToyGateway(service);
    }

    @Override
    protected void setupFactory() {
        factory = new ToyFactory(startsWith);
    }

    @Override
    protected FilterRequestDto startsWithFilter() {
        return new FilterRequestDto("toy", "name", Filter.OPERATOR_STARTS_WITH, startsWith);
    }
}
