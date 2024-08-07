package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityGatewayAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionResourceNotFound;
import org.springframework.stereotype.Component;

@Component
public class VideoGameBoxGateway extends EntityGatewayAbstract<VideoGameBox, VideoGameBoxRequestDto, VideoGameBoxResponseDto>
        implements EntityGateway<VideoGameBox, VideoGameBoxRequestDto, VideoGameBoxResponseDto> {

    public VideoGameBoxGateway(VideoGameBoxService service) {
        super(service);
    }

    @Override
    public VideoGameBoxResponseDto updateExisting(int id, VideoGameBoxRequestDto requestDto) {
        VideoGameBox videoGameBox = service.getById(id);
        if (!videoGameBox.isPersisted()) {
            throw new ExceptionResourceNotFound(videoGameBox.getKey(), id);
        }
        VideoGameBoxService videoGameBoxService = (VideoGameBoxService) service;
        return videoGameBoxService.updateExisting(videoGameBox, requestDto).convertToResponseDto();
    }
}
