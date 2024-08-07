package com.sethhaskellcondie.thegamepensiveapi.domain.entity.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityService;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterService;
import org.springframework.stereotype.Service;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityServiceAbstract;

@Service
public class SystemService extends EntityServiceAbstract<System, SystemRequestDto, SystemResponseDto> implements EntityService<System, SystemRequestDto, SystemResponseDto> {
    public SystemService(SystemRepository repository, FilterService filterService) {
        super(repository, filterService);
    }

    @Override
    public System createNew(SystemRequestDto systemRequestDto) {
        System system = new System().updateFromRequestDto(systemRequestDto);
        return repository.insert(system);
    }

    public int getIdByName(String name) {
        SystemRepository systemRepository = (SystemRepository) repository;
        return systemRepository.getIdByName(name);
    }
}
