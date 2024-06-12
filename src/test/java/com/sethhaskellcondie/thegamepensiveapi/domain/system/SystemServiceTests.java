package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.EntityServiceTests;
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
