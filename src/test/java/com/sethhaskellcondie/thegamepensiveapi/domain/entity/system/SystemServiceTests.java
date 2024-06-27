package com.sethhaskellcondie.thegamepensiveapi.domain.entity.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityServiceTests;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterService;

import static org.mockito.Mockito.mock;

@Deprecated
public class SystemServiceTests extends EntityServiceTests<System, SystemRequestDto, SystemResponseDto> {
    @Override
    protected void setupServiceMockRepository() {
        repository = mock(EntityRepository.class);
        filterService = mock(FilterService.class);
        service = new SystemService(repository, filterService);
    }

    @Override
    protected void setupFactory() {
        String startsWith = "SuperConsole";
        factory = new SystemFactory(startsWith);
    }
}
