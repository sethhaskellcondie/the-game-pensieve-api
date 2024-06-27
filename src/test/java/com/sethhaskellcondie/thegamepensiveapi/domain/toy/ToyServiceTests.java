package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityServiceTests;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.Toy;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.ToyRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.ToyResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.ToyService;
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
