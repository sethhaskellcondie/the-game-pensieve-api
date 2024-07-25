package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame;

import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionInputValidation;
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

    @Override
    public VideoGameResponseDto createNew(VideoGameRequestDto requestDto) {
        throw new RuntimeException("Not Implemented Error: Create new video game objects through the VideoGameBoxGateway instead of the VideoGameGateway.");
    }

}
