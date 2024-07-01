package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame;

import org.springframework.stereotype.Component;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityGatewayAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityService;

@Component
public class VideoGameGateway extends EntityGatewayAbstract<VideoGame, VideoGameRequestDto, VideoGameResponseDto>
	implements EntityGateway<VideoGame, VideoGameRequestDto, VideoGameResponseDto> {
	public VideoGameGateway(EntityService<VideoGame, VideoGameRequestDto, VideoGameResponseDto> service) {
		super(service);
	}
}
