package com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityService;
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
        Toy toy = new Toy().updateFromRequestDto(toyRequestDto);
        return repository.insert(toy);
    }

    public int getIdByNameAndSet(String name, String set) {
        ToyRepository toyRepository = (ToyRepository) repository;
        return toyRepository.getIdByNameAndSet(name, set);
    }
}
