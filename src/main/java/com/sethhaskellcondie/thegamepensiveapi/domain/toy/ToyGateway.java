package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import org.springframework.stereotype.Component;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGateway;

@Component
public interface ToyGateway extends EntityGateway<Toy, ToyRequestDto, ToyResponseDto> {
}
