package com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityGatewayAbstract;
import org.springframework.stereotype.Component;

@Component
public class VideoGameBoxGateway extends EntityGatewayAbstract<VideoGameBox, VideoGameBoxRequestDto, VideoGameBoxResponseDto>
        implements EntityGateway<VideoGameBox, VideoGameBoxRequestDto, VideoGameBoxResponseDto> {

    public VideoGameBoxGateway(VideoGameBoxService service) {
        super(service);
    }
}
