package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import org.springframework.stereotype.Service;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.EntityServiceAbstract;

@Service
public class ToyServiceAbstract extends EntityServiceAbstract<Toy, ToyRequestDto, ToyResponseDto> implements ToyService {
    public ToyServiceAbstract(EntityRepository<Toy, ToyRequestDto, ToyResponseDto> repository) {
        super(repository);
    }
}
