package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import org.springframework.stereotype.Component;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityGateway;

@Component
public interface SystemGateway extends EntityGateway<System, SystemRequestDto, SystemResponseDto> {
}
