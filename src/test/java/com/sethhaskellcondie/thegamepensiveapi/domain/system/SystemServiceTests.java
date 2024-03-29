package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.EntityServiceTests;

import static org.mockito.Mockito.mock;

public class SystemServiceTests extends EntityServiceTests<System, SystemRequestDto, SystemResponseDto> {
    @Override
    protected void setupServiceMockRepository() {
        repository = mock(EntityRepository.class);
        service = new SystemService(repository);
    }

    @Override
    protected SystemRequestDto generateRequestDto() {
        return new SystemRequestDto("SystemName", 3, false);
    }

    @Override
    protected System generateEntity() {
        return new System();
    }
}
