package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityService;
import org.springframework.stereotype.Service;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.EntityServiceAbstract;

@Service
public class ToyService extends EntityServiceAbstract<Toy, ToyRequestDto, ToyResponseDto> implements EntityService<Toy, ToyRequestDto, ToyResponseDto> {
    public ToyService(EntityRepository<Toy, ToyRequestDto, ToyResponseDto> repository) {
        super(repository);
    }
}
