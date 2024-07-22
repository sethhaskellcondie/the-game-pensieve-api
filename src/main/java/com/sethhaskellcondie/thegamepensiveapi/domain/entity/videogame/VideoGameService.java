package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame;

import java.util.ArrayList;
import java.util.List;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.ToyRepository;
import org.springframework.stereotype.Service;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityService;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityServiceAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionMalformedEntity;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterService;

@Service
public class VideoGameService extends EntityServiceAbstract<VideoGame, VideoGameRequestDto, VideoGameResponseDto>
        implements EntityService<VideoGame, VideoGameRequestDto, VideoGameResponseDto> {

    private final SystemRepository systemRepository;

    public VideoGameService(VideoGameRepository repository, FilterService filterService, SystemRepository systemRepository) {
        super(repository, filterService);
        this.systemRepository = systemRepository;
    }

    @Override
    public List<VideoGame> getWithFilters(List<FilterRequestDto> dtoFilters) {
        final List<Exception> exceptions = new ArrayList<>();
        final List<VideoGame> videoGamesWithoutSystemsVerified = super.getWithFilters(dtoFilters);
        final List<VideoGame> verifiedVideoGames = new ArrayList<>();
        for (VideoGame videoGame : videoGamesWithoutSystemsVerified) {
            try {
                validateSystemId(videoGame);
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
        return validateSystemId(videoGame);
    }

    @Override
    public VideoGame createNew(VideoGameRequestDto requestDto) {
        final VideoGame videoGame = new VideoGame().updateFromRequestDto(requestDto);
        final VideoGame validatedVideoGame = validateSystemId(videoGame);
        final VideoGame savedVideoGame = repository.insert(validatedVideoGame);
        savedVideoGame.setSystemName(validatedVideoGame.getSystemName());
        return savedVideoGame;
    }

    @Override
    public VideoGame updateExisting(VideoGame videoGame) {
        VideoGame validatedVideoGame = videoGame;
        if (!videoGame.isSystemIdValid()) {
            validatedVideoGame = validateSystemId(videoGame);
        }
        final VideoGame updatedVideoGame = repository.update(validatedVideoGame);
        updatedVideoGame.setSystemName(validatedVideoGame.getSystemName());
        return updatedVideoGame;
    }

    public VideoGame validateSystemId(VideoGame videoGame) {
        try {
            System system = systemRepository.getByIdIncludeDeleted(videoGame.getSystemId());
            videoGame.setSystemName(system.getName());
        } catch (Exception e) {
            throw new ExceptionMalformedEntity(List.of(new Exception("Error - Problem getting video games from the database, video game with title: '"
                    + videoGame.getTitle() + "' had systemId: " + videoGame.getSystemId() + " but couldn't get a valid system from the database with that id. Message: "
                    + e.getMessage())));
        }
        return videoGame;
    }

    public int getIdByTitleAndSystem(String title, int systemId) {
        VideoGameRepository videoGameRepository = (VideoGameRepository) repository;
        return videoGameRepository.getIdByTitleAndSystem(title, systemId);
    }
}
