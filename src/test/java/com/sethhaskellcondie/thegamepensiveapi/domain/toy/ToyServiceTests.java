package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.EntityServiceTests;

import static org.mockito.Mockito.mock;

public class ToyServiceTests extends EntityServiceTests<Toy, ToyRequestDto, ToyResponseDto> {
    @Override
    protected void setupServiceMockRepository() {
        repository = mock(EntityRepository.class);
        service = new ToyService(repository);
    }

    @Override
    protected void setupFactory() {
        factory = new ToyFactory();
    }
}
