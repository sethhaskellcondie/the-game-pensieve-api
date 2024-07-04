package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityService;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityServiceAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGame;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameService;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionMalformedEntity;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class VideoGameBoxService extends EntityServiceAbstract<VideoGameBox, VideoGameBoxRequestDto, VideoGameBoxResponseDto>
        implements EntityService<VideoGameBox, VideoGameBoxRequestDto, VideoGameBoxResponseDto> {

    private final SystemRepository systemRepository;
    private final VideoGameRepository videoGameRepository;
    private final VideoGameService videoGameService;

    public VideoGameBoxService(EntityRepository<VideoGameBox, VideoGameBoxRequestDto, VideoGameBoxResponseDto> repository, FilterService filterService,
                               SystemRepository systemRepository, VideoGameRepository videoGameRepository, VideoGameService videoGameService) {
        super(repository, filterService);
        this.systemRepository = systemRepository;
        this.videoGameRepository = videoGameRepository;
        this.videoGameService = videoGameService;
    }

    @Override
    public List<VideoGameBox> getWithFilters(List<FilterRequestDto> dtoFilters) {
        final List<Exception> exceptions = new ArrayList<>();
        final List<VideoGameBox> unverifiedBoxes = super.getWithFilters(dtoFilters);
        final List<VideoGameBox> verifiedBoxes = new ArrayList<>();
        for (VideoGameBox videoGameBox : unverifiedBoxes) {
            try {
                verifiedBoxes.add(validateSystemAndVideoGamesList(videoGameBox));
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        if (!exceptions.isEmpty()) {
            throw new ExceptionMalformedEntity(exceptions);
        }
        return verifiedBoxes;
    }

    @Override
    public VideoGameBox getById(int id) {
        final VideoGameBox videoGameBox = super.getById(id);
        return validateSystemAndVideoGamesList(videoGameBox);
    }

    @Override
    public VideoGameBox createNew(VideoGameBoxRequestDto requestDto) {
        final VideoGameBox videoGameBox = new VideoGameBox().updateFromRequestDto(requestDto);
        final VideoGameBox validatedVideoGameBox = validateSystemAndVideoGamesList(videoGameBox);
        final VideoGameBox savedVideoGameBox = repository.insert(validatedVideoGameBox);
        savedVideoGameBox.setSystemName(validatedVideoGameBox.getSystemName());
        savedVideoGameBox.setVideoGames(validatedVideoGameBox.getVideoGames());
        return savedVideoGameBox;
    }

    @Override
    public VideoGameBox updateExisting(VideoGameBox videoGameBox) {
        VideoGameBox validatedVideoGameBox = videoGameBox;
        if (!validatedVideoGameBox.isSystemIdValid()) {
            validatedVideoGameBox = validateSystemId(validatedVideoGameBox);
        }
        if (!validatedVideoGameBox.isVideoGamesValid()) {
            validatedVideoGameBox = validateVideoGames(validatedVideoGameBox);
        }
        final VideoGameBox savedVideoGameBox = repository.update(validatedVideoGameBox);
        savedVideoGameBox.setSystemName(validatedVideoGameBox.getSystemName());
        savedVideoGameBox.setVideoGames(validatedVideoGameBox.getVideoGames());
        return savedVideoGameBox;
    }

    public VideoGameBox validateSystemAndVideoGamesList(VideoGameBox videoGameBox) {
        final VideoGameBox systemVerified = validateSystemId(videoGameBox);
        return validateVideoGames(systemVerified);
    }

    public VideoGameBox validateSystemId(VideoGameBox videoGameBox) {
        try {
            System system = systemRepository.getByIdIncludeDeleted(videoGameBox.getSystemId());
            videoGameBox.setSystemName(system.getName());
        } catch (Exception e) {
            throw new ExceptionMalformedEntity(List.of(new Exception("Error - Problem getting video game box from the database, video game box with title: '"
                    + videoGameBox.getTitle() + "' had systemId: " + videoGameBox.getSystemId() + " but couldn't get a valid system from the database with that id. Message: "
                    + e.getMessage())));
        }
        return videoGameBox;
    }

    public VideoGameBox validateVideoGames(VideoGameBox videoGameBox) {
        ExceptionMalformedEntity exceptionMalformedEntity = new ExceptionMalformedEntity();
        if (videoGameBox.getVideoGameIds().isEmpty()) {
            exceptionMalformedEntity.addException("Attempted to validate the video games for video game box with title: '" + videoGameBox.getTitle()
                    + "', but there were not video game ids found on that object");
            throw exceptionMalformedEntity;
        }
        List<VideoGame> videoGames = new ArrayList<>();
        for (Integer videoGameId : videoGameBox.getVideoGameIds()) {
            try {
                VideoGame videoGame = videoGameRepository.getByIdIncludeDeleted(videoGameId);
                videoGames.add(videoGameService.validateSystemId(videoGame));
            } catch (Exception e) {
                exceptionMalformedEntity.addException("Attempted to validate the video games for video game box with title: '" + videoGameBox.getTitle()
                        + "', but there was an error getting video game data: " + e.getMessage());
            }
        }
        if (!exceptionMalformedEntity.isEmpty()) {
            throw exceptionMalformedEntity;
        }
        videoGameBox.setVideoGames(videoGames);
        return videoGameBox;
    }
}
