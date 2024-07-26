package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityService;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityServiceAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.SlimVideoGame;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGame;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameService;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionMalformedEntity;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public VideoGameBox createNew(VideoGameBoxRequestDto requestDto) {
        ExceptionMalformedEntity exceptionMalformedEntity = new ExceptionMalformedEntity();
        final VideoGameBox videoGameBox = new VideoGameBox().updateFromRequestDto(requestDto);
        List<SlimVideoGame> relatedVideoGames = new ArrayList<>();
        for (Integer videoGameId : requestDto.existingVideoGameIds()) {
            try {
                relatedVideoGames.add(videoGameRepository.getById(videoGameId).convertToSlimVideoGame());
            } catch (Exception e) {
                exceptionMalformedEntity.addException(e);
            }
        }
        for (VideoGameRequestDto videoGameRequestDto : requestDto.newVideoGames()) {
            try {
                relatedVideoGames.add(videoGameService.createNew(videoGameRequestDto).convertToSlimVideoGame());
            } catch (Exception e) {
                exceptionMalformedEntity.addException(e);
            }
        }
        if (!exceptionMalformedEntity.isEmpty()) {
            throw exceptionMalformedEntity;
        }
        videoGameBox.setVideoGames(relatedVideoGames);
        final VideoGameBox validatedVideoGameBox = validateSystem(videoGameBox);
        final VideoGameBox savedVideoGameBox = repository.insert(validatedVideoGameBox);
        savedVideoGameBox.setSystem(validatedVideoGameBox.getSystem());
        savedVideoGameBox.setVideoGames(relatedVideoGames);
        return savedVideoGameBox;
    }

    @Override
    public VideoGameBox updateExisting(VideoGameBox videoGameBox) {
        VideoGameBox validatedVideoGameBox = videoGameBox;
        if (!validatedVideoGameBox.isSystemValid()) {
            validatedVideoGameBox = validateSystem(validatedVideoGameBox);
        }
        if (!validatedVideoGameBox.isVideoGamesValid()) {
            validatedVideoGameBox = validateVideoGames(validatedVideoGameBox);
        }
        final VideoGameBox savedVideoGameBox = repository.update(validatedVideoGameBox);
        savedVideoGameBox.setSystem(validatedVideoGameBox.getSystem());
        savedVideoGameBox.setVideoGames(validatedVideoGameBox.getVideoGames());
        return savedVideoGameBox;
    }

    @Override
    @Transactional
    public void deleteById(int id) {
        VideoGameBox videoGameBox = getById(id);
        List<VideoGame> markedForDeletion = new ArrayList<>();
        for (SlimVideoGame slimVideoGame : videoGameBox.getVideoGames()) {
            VideoGame videoGame = videoGameService.getById(slimVideoGame.id());
            //if this video game is the only game associated to this video game box then delete it as well as the box.
            if (videoGame.getVideoGameBoxes().size() == 1) {
                markedForDeletion.add(videoGame);
            }
        }
        for (VideoGame videoGame : markedForDeletion) {
            videoGameService.deleteById(videoGame.getId());
        }
        repository.deleteById(id);
    }

    public VideoGameBox validateSystemAndVideoGamesList(VideoGameBox videoGameBox) {
        final VideoGameBox systemVerified = validateSystem(videoGameBox);
        return validateVideoGames(systemVerified);
    }

    public VideoGameBox validateSystem(VideoGameBox videoGameBox) {
        try {
            System system = systemRepository.getByIdIncludeDeleted(videoGameBox.getSystemId());
            videoGameBox.setSystem(system);
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
        List<SlimVideoGame> videoGames = new ArrayList<>();
        for (Integer videoGameId : videoGameBox.getVideoGameIds()) {
            try {
                videoGames.add(videoGameService.getById(videoGameId).convertToSlimVideoGame());
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
