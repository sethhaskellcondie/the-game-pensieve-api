package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.EntityServiceTests;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterService;

import static org.mockito.Mockito.mock;

@Deprecated
public class ToyServiceTests extends EntityServiceTests<Toy, ToyRequestDto, ToyResponseDto> {
    @Override
    protected void setupServiceMockRepository() {
        repository = mock(EntityRepository.class);
        filterService = mock(FilterService.class);
        service = new ToyService(repository, filterService);
    }

    @Override
    protected void setupFactory() {
        factory = new ToyFactory("StartsWithThis!");
    }
}
