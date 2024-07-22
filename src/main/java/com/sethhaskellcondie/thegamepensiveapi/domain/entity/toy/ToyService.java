package com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityService;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterService;
import org.springframework.stereotype.Service;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityServiceAbstract;

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
