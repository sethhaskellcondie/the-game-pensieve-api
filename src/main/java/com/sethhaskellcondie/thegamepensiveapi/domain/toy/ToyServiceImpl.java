package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import org.springframework.stereotype.Service;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.EntityServiceImpl;

@Service
public class ToyServiceImpl extends EntityServiceImpl<Toy, ToyRequestDto, ToyResponseDto> implements ToyService {
    public ToyServiceImpl(EntityRepository<Toy, ToyRequestDto, ToyResponseDto> repository) {
        super(repository);
    }
}
