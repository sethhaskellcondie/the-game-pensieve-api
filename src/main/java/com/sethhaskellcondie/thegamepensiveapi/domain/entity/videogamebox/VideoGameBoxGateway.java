package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityGatewayAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityService;
import org.springframework.stereotype.Component;

@Component
public class VideoGameBoxGateway extends EntityGatewayAbstract<VideoGameBox, VideoGameBoxRequestDto, VideoGameBoxResponseDto>
        implements EntityGateway<VideoGameBox, VideoGameBoxRequestDto, VideoGameBoxResponseDto> {

    public VideoGameBoxGateway(EntityService<VideoGameBox, VideoGameBoxRequestDto, VideoGameBoxResponseDto> service) {
        super(service);
    }
}
