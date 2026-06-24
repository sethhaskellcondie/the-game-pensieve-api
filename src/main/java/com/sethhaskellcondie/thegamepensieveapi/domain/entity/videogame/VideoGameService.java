package com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.SlimVideoGameBox;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBox;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxRepository;
import org.springframework.stereotype.Service;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityService;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityServiceAbstract;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
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
        final List<VideoGame> videoGames = super.getWithFilters(dtoFilters);
        if (videoGames.isEmpty()) {
            return videoGames;
        }

        //Batch load every related box and system once (instead of per-game) to avoid N+1 queries, then hydrate each
        //game from the in-memory maps. This is the list equivalent of validateRelatedObjects() used by getById().
        final List<Integer> boxIds = videoGames.stream().flatMap(game -> game.getVideoGameBoxIds().stream()).distinct().toList();
        final Map<Integer, VideoGameBox> boxesById = new HashMap<>();
        for (VideoGameBox box : videoGameBoxRepository.getByIds(boxIds)) {
            boxesById.put(box.getId(), box);
        }
        final List<Integer> systemIds = new ArrayList<>();
        videoGames.forEach(game -> systemIds.add(game.getSystemId()));
        boxesById.values().forEach(box -> systemIds.add(box.getSystemId()));
        final Map<Integer, System> systemsById = getSystemsByIds(systemIds);

        final Map<Integer, SlimVideoGameBox> slimBoxesById = new HashMap<>();
        for (VideoGameBox box : boxesById.values()) {
            final System boxSystem = systemsById.get(box.getSystemId());
            if (null != boxSystem) {
                box.setSystem(boxSystem);
            }
            slimBoxesById.put(box.getId(), box.convertToSlimVideoGameBox());
        }

        final List<Exception> exceptions = new ArrayList<>();
        final List<VideoGame> verifiedVideoGames = new ArrayList<>();
        for (VideoGame videoGame : videoGames) {
            try {
                hydrateRelatedObjects(videoGame, systemsById, slimBoxesById);
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

    //The batch-loaded equivalent of validateRelatedObjects(): hydrate a single game from the prefetched maps,
    //preserving the same malformed-entity checks (missing system, no related boxes, missing related box).
    private void hydrateRelatedObjects(VideoGame videoGame, Map<Integer, System> systemsById, Map<Integer, SlimVideoGameBox> slimBoxesById) {
        final ExceptionMalformedEntity exceptionMalformedEntity = new ExceptionMalformedEntity();
        final System system = systemsById.get(videoGame.getSystemId());
        if (null != system) {
            videoGame.setSystem(system);
        } else {
            exceptionMalformedEntity.addException(new Exception("Problem getting video games from the database, video game with title: '"
                    + videoGame.getTitle() + "' had systemId: " + videoGame.getSystemId() + " but couldn't get a valid system from the database with that id."));
        }
        if (videoGame.getVideoGameBoxIds().isEmpty()) {
            exceptionMalformedEntity.addException("Problem getting video game with title " + videoGame.getTitle()
                    + " from the database, no related video game box objects found in the database.");
            throw exceptionMalformedEntity;
        }
        final List<SlimVideoGameBox> videoGameBoxes = new ArrayList<>();
        for (Integer videoGameBoxId : videoGame.getVideoGameBoxIds()) {
            final SlimVideoGameBox slimVideoGameBox = slimBoxesById.get(videoGameBoxId);
            if (null != slimVideoGameBox) {
                videoGameBoxes.add(slimVideoGameBox);
            } else {
                exceptionMalformedEntity.addException("Problem getting video game with title " + videoGame.getTitle()
                    + " from the database, related video game box with id " + videoGameBoxId
                    + " failed when trying to retrieve a video game box with that id from the database.");
            }
        }
        if (!exceptionMalformedEntity.isEmpty()) {
            throw exceptionMalformedEntity;
        }
        videoGame.setVideoGameBoxes(videoGameBoxes);
    }

    private Map<Integer, System> getSystemsByIds(List<Integer> systemIds) {
        final Map<Integer, System> systemsById = new HashMap<>();
        for (System system : systemRepository.getByIdsIncludeDeleted(systemIds)) {
            systemsById.put(system.getId(), system);
        }
        return systemsById;
    }

    /**
     * Batch load the slim view of many video games at once (game + its system, no boxes) for use by the
     * VideoGameBoxService when hydrating the games inside a list of boxes. This avoids calling getById() per game,
     * which would re-hydrate every game's own boxes just to build a slim view that doesn't include them.
     */
    public Map<Integer, SlimVideoGame> getSlimVideoGamesByIds(List<Integer> videoGameIds) {
        final VideoGameRepository videoGameRepository = (VideoGameRepository) repository;
        final List<VideoGame> videoGames = videoGameRepository.getByIds(videoGameIds);
        final List<Integer> systemIds = videoGames.stream().map(VideoGame::getSystemId).toList();
        final Map<Integer, System> systemsById = getSystemsByIds(systemIds);
        final Map<Integer, SlimVideoGame> slimVideoGamesById = new HashMap<>();
        for (VideoGame videoGame : videoGames) {
            final System system = systemsById.get(videoGame.getSystemId());
            if (null != system) {
                videoGame.setSystem(system);
            }
            slimVideoGamesById.put(videoGame.getId(), videoGame.convertToSlimVideoGame());
        }
        return slimVideoGamesById;
    }

    @Override
    public VideoGame getById(int id) {
        final VideoGame videoGame = super.getById(id);
        return validateRelatedObjects(videoGame);
    }

    @Override
    public VideoGame createNew(VideoGameRequestDto requestDto) {
        if (duplicationCheck(requestDto.title(), requestDto.systemId()) > 0) {
            throw new ExceptionFailedDbValidation("VideoGame with title: '" + requestDto.title() + "' and systemId: " + requestDto.systemId()
                    + " was already found in the database. To update it, make an update request.");
        }
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
                    + videoGame.getTitle() + "' had systemId: " + videoGame.getSystemId() + " but couldn't get a valid system from the database with that id.", e);
        }
        return videoGame;
    }

    public int duplicationCheck(String title, int systemId) {
        return getIdByTitleAndSystemId(title, systemId);
    }

    public int getIdByTitleAndSystemId(String title, int systemId) {
        VideoGameRepository videoGameRepository = (VideoGameRepository) repository;
        return videoGameRepository.getIdByTitleAndSystem(title, systemId);
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
}
