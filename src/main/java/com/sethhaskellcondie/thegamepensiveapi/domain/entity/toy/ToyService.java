package com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityService;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterService;
import org.springframework.stereotype.Service;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityServiceAbstract;

@Service
public class ToyService extends EntityServiceAbstract<Toy, ToyRequestDto, ToyResponseDto> implements EntityService<Toy, ToyRequestDto, ToyResponseDto> {
    public ToyService(EntityRepository<Toy, ToyRequestDto, ToyResponseDto> repository, FilterService filterService) {
        super(repository, filterService);
    }
}
