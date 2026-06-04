package com.sethhaskellcondie.thegamepensieveapi.domain.entity.system;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityService;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterService;
import org.springframework.stereotype.Service;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityServiceAbstract;

@Service
public class SystemService extends EntityServiceAbstract<System, SystemRequestDto, SystemResponseDto> implements EntityService<System, SystemRequestDto, SystemResponseDto> {
    public SystemService(SystemRepository repository, FilterService filterService) {
        super(repository, filterService);
    }

    @Override
    public System createNew(SystemRequestDto systemRequestDto) {
        if (duplicationCheck(systemRequestDto.name()) > 0) {
            throw new ExceptionFailedDbValidation("System with name: '" + systemRequestDto.name() + "' was already found in the database. To update it, make an update request.");
        }
        System system = new System().updateFromRequestDto(systemRequestDto);
        return repository.insert(system);
    }

    public int duplicationCheck(String name) {
        return getIdByName(name);
    }

    public int getIdByName(String name) {
        SystemRepository systemRepository = (SystemRepository) repository;
        return systemRepository.getIdByName(name);
    }
}
