package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityGatewayAbstract;
import org.springframework.stereotype.Component;

@Component
public class VideoGameBoxGateway extends EntityGatewayAbstract<VideoGameBox, VideoGameBoxRequestDto, VideoGameBoxResponseDto>
        implements EntityGateway<VideoGameBox, VideoGameBoxRequestDto, VideoGameBoxResponseDto> {

    public VideoGameBoxGateway(VideoGameBoxService service) {
        super(service);
    }
}
