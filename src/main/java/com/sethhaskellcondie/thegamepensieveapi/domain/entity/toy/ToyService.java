package com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityService;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterService;
import org.springframework.stereotype.Service;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityServiceAbstract;

@Service
public class ToyService extends EntityServiceAbstract<Toy, ToyRequestDto, ToyResponseDto> implements EntityService<Toy, ToyRequestDto, ToyResponseDto> {

    public ToyService(ToyRepository repository, FilterService filterService) {
        super(repository, filterService);
    }

    @Override
    public Toy createNew(ToyRequestDto toyRequestDto) {
        if (duplicationCheck(toyRequestDto.name(), toyRequestDto.set()) > 0) {
            throw new ExceptionFailedDbValidation("Toy with name: '" + toyRequestDto.name() + "' and set: '" + toyRequestDto.set()
                    + "' was already found in the database. To update it, make an update request.");
        }
        Toy toy = new Toy().updateFromRequestDto(toyRequestDto);
        return repository.insert(toy);
    }

    public int duplicationCheck(String name, String set) {
        return getIdByNameAndSet(name, set);
    }

    public int getIdByNameAndSet(String name, String set) {
        ToyRepository toyRepository = (ToyRepository) repository;
        return toyRepository.getIdByNameAndSet(name, set);
    }
}
