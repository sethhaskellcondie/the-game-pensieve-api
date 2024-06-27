package com.sethhaskellcondie.thegamepensiveapi.domain.entity.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityService;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterService;
import org.springframework.stereotype.Service;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityServiceAbstract;

@Service
public class SystemService extends EntityServiceAbstract<System, SystemRequestDto, SystemResponseDto> implements EntityService<System, SystemRequestDto, SystemResponseDto> {
    public SystemService(EntityRepository<System, SystemRequestDto, SystemResponseDto> repository, FilterService filterService) {
        super(repository, filterService);
    }
}
