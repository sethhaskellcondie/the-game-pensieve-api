package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import org.springframework.stereotype.Component;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGatewayImpl;
import com.sethhaskellcondie.thegamepensiveapi.domain.EntityService;

@Component
public class SystemGatewayImpl extends EntityGatewayImpl<System, SystemRequestDto, SystemResponseDto> {
	public SystemGatewayImpl(EntityService<System, SystemRequestDto, SystemResponseDto> service) {
		super(service);
	}
}
