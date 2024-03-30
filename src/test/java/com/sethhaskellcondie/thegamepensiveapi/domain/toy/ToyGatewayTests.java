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
    protected Toy generateEntity() {
        return new Toy(1, "ToyName", "ToySet");
    }

    @Override
    protected ToyRequestDto generateRequestFromEntity(Toy entity) {
        return new ToyRequestDto(entity.getName(), entity.getSet());
    }

    @Override
    protected Toy generateAnotherEntity() {
        return new Toy(2, "AnotherToyName", "AnotherToySet");
    }

    @Override
    protected Toy generateEmptyEntity() {
        return new Toy();
    }
}
