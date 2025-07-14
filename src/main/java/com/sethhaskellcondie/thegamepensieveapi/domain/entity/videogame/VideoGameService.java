package com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame;

import java.util.ArrayList;
import java.util.List;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.SlimVideoGameBox;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBox;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxRepository;
import org.springframework.stereotype.Service;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityService;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityServiceAbstract;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionMalformedEntity;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterService;

@Service
public class VideoGameService extends EntityServiceAbstract<VideoGame, VideoGameRequestDto, VideoGameResponseDto>
        implements EntityService<VideoGame, VideoGameRequestDto, VideoGameResponseDto> {

    private final SystemRepository systemRepository;
    private final VideoGameBoxRepository videoGameBoxRepository;

    public VideoGameService(VideoGameRepository repository, FilterService filterService, SystemRepository systemRepository, VideoGameBoxRepository videoGameBoxRepository) {
        super(repository, filterService);
        this.systemRepository = systemRepository;
        this.videoGameBoxRepository = videoGameBoxRepository;
    }

    @Override
    public List<VideoGame> getWithFilters(List<FilterRequestDto> dtoFilters) {
        final List<Exception> exceptions = new ArrayList<>();
        final List<VideoGame> videoGamesWithoutSystemsVerified = super.getWithFilters(dtoFilters);
        final List<VideoGame> verifiedVideoGames = new ArrayList<>();
        for (VideoGame videoGame : videoGamesWithoutSystemsVerified) {
            try {
                validateRelatedObjects(videoGame);
                verifiedVideoGames.add(videoGame);
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        if (!exceptions.isEmpty()) {
            throw new ExceptionMalformedEntity(exceptions);
        }
        return verifiedVideoGames;
    }

    @Override
    public VideoGame getById(int id) {
        final VideoGame videoGame = super.getById(id);
        return validateRelatedObjects(videoGame);
    }

    @Override
    public VideoGame createNew(VideoGameRequestDto requestDto) {
        final VideoGame videoGame = new VideoGame().updateFromRequestDto(requestDto);
        final VideoGame validatedVideoGame = validateSystem(videoGame);
        final VideoGame savedVideoGame = repository.insert(validatedVideoGame);
        savedVideoGame.setSystem(validatedVideoGame.getSystem());
        return savedVideoGame;
    }

    @Override
    public VideoGame updateExisting(int id, VideoGameRequestDto requestDto) {
        VideoGame videoGame = repository.getById(id).updateFromRequestDto(requestDto);
        VideoGame validatedVideoGame = videoGame;
        if (!videoGame.isSystemValid()) {
            validatedVideoGame = validateRelatedObjects(videoGame);
        }
        final VideoGame updatedVideoGame = repository.update(validatedVideoGame);
        updatedVideoGame.setSystem(validatedVideoGame.getSystem());
        updatedVideoGame.setVideoGameBoxes(validatedVideoGame.getVideoGameBoxes());
        return updatedVideoGame;
    }

    public VideoGame validateSystem(VideoGame videoGame) {
        try {
            System system = systemRepository.getByIdIncludeDeleted(videoGame.getSystemId());
            videoGame.setSystem(system);
        } catch (Exception e) {
            throw new ExceptionMalformedEntity("Error - Problem getting video games from the database, video game with title: '"
                    + videoGame.getTitle() + "' had systemId: " + videoGame.getSystemId() + " but couldn't get a valid system from the database with that id. Message: "
                    + e.getMessage());
        }
        return videoGame;
    }

    public VideoGame validateRelatedObjects(VideoGame videoGame) {
        ExceptionMalformedEntity exceptionMalformedEntity = new ExceptionMalformedEntity();
        try {
            System system = systemRepository.getByIdIncludeDeleted(videoGame.getSystemId());
            videoGame.setSystem(system);
        } catch (Exception e) {
            exceptionMalformedEntity.addException(new Exception("Problem getting video games from the database, video game with title: '"
                    + videoGame.getTitle() + "' had systemId: " + videoGame.getSystemId() + " but couldn't get a valid system from the database with that id. Message: "
                    + e.getMessage()));
        }
        if (videoGame.getVideoGameBoxIds().isEmpty()) {
            exceptionMalformedEntity.addException("Problem getting video game with title " + videoGame.getTitle()
                    + " from the database, no related video game box objects found in the database.");
            throw exceptionMalformedEntity;
        }
        List<SlimVideoGameBox> videoGameBoxes = new ArrayList<>();
        for (Integer videoGameBoxId : videoGame.getVideoGameBoxIds()) {
            try {
                VideoGameBox videoGameBox = videoGameBoxRepository.getById(videoGameBoxId);
                videoGameBox.setSystem(systemRepository.getById(videoGameBox.getSystemId()));
                videoGameBoxes.add(videoGameBox.convertToSlimVideoGameBox());
            } catch (Exception e) {
                exceptionMalformedEntity.addException("Problem getting video game with title " + videoGame.getTitle()
                    + " from the database, related video game box with id " + videoGameBoxId
                    + " failed when trying to retrieve a video game box with that id from the database.");
            }
        }

        if (exceptionMalformedEntity.isEmpty()) {
            videoGame.setVideoGameBoxes(videoGameBoxes);
            return videoGame;
        }
        throw exceptionMalformedEntity;
    }

    public int getIdByTitleAndSystem(String title, int systemId) {
        VideoGameRepository videoGameRepository = (VideoGameRepository) repository;
        return videoGameRepository.getIdByTitleAndSystem(title, systemId);
    }
}
