package com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityServiceTests;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterService;

import static org.mockito.Mockito.mock;

//Deprecated: This project is going to use full SpringBootTest Integration tests to test entity functionality
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
