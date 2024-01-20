package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import org.springframework.stereotype.Component;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGatewayImpl;
import com.sethhaskellcondie.thegamepensiveapi.domain.EntityService;

@Component
public class ToyGatewayImpl extends EntityGatewayImpl<Toy, ToyRequestDto, ToyResponseDto> {
	public ToyGatewayImpl(EntityService<Toy, ToyRequestDto, ToyResponseDto> service) {
		super(service);
	}
}
