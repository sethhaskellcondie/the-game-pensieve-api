package com.sethhaskellcondie.thegamepensieveapi.domain.entity;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldOptionRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValueRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGame;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameService;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBox;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxService;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Every query path in the EntityRepositoryAbstract funnels through the afterLoad() hook, repositories
 * that override it (like the VideoGameRepository and the VideoGameBoxRepository) hydrate related ids on
 * every load. These tests pin that behavior on the paths that were previously easy to miss, if each
 * query method had to be decorated individually: getByIds, getDeletedById, getByIdIncludeDeleted,
 * and getByIdsIncludeDeleted.
 */
@JdbcTest
@ActiveProfiles("repository-tests")
public class EntityRepositoryAfterLoadTests {

    @Autowired
    protected JdbcTemplate jdbcTemplate;
    protected SystemRepository systemRepository;
    protected TestVideoGameRepository videoGameRepository;
    protected TestVideoGameBoxRepository videoGameBoxRepository;
    protected VideoGameService videoGameService;
    protected VideoGameBoxService videoGameBoxService;

    // Create test wrapper classes for repositories with protected constructors
    static class TestVideoGameRepository extends VideoGameRepository {
        TestVideoGameRepository(JdbcTemplate jdbcTemplate, CustomFieldRepository customFieldRepository, CustomFieldValueRepository customFieldValueRepository) {
            super(jdbcTemplate, customFieldRepository, customFieldValueRepository);
        }
    }

    static class TestVideoGameBoxRepository extends VideoGameBoxRepository {
        TestVideoGameBoxRepository(JdbcTemplate jdbcTemplate, CustomFieldRepository customFieldRepository, CustomFieldValueRepository customFieldValueRepository) {
            super(jdbcTemplate, customFieldRepository, customFieldValueRepository);
        }
    }

    @BeforeEach
    public void setUp() {
        final CustomFieldOptionRepository customFieldOptionRepository = new CustomFieldOptionRepository(jdbcTemplate);
        final CustomFieldRepository customFieldRepository = new CustomFieldRepository(jdbcTemplate, customFieldOptionRepository);
        final CustomFieldValueRepository customFieldValueRepository = new CustomFieldValueRepository(jdbcTemplate, customFieldRepository, customFieldOptionRepository);
        systemRepository = new SystemRepository(jdbcTemplate, customFieldRepository, customFieldValueRepository);
        videoGameRepository = new TestVideoGameRepository(jdbcTemplate, customFieldRepository, customFieldValueRepository);
        videoGameBoxRepository = new TestVideoGameBoxRepository(jdbcTemplate, customFieldRepository, customFieldValueRepository);
        final FilterService filterService = new FilterService(customFieldRepository);
        videoGameService = new VideoGameService(videoGameRepository, filterService, systemRepository, videoGameBoxRepository);
        videoGameBoxService = new VideoGameBoxService(videoGameBoxRepository, filterService, systemRepository, videoGameService);
    }

    @Test
    void getByIds_VideoGamesExist_RelatedBoxIdsHydrated() {
        final VideoGameBox box = createVideoGameBox("AfterLoad GetByIds Box", List.of("AfterLoad GetByIds Game 1", "AfterLoad GetByIds Game 2"));

        final List<VideoGame> games = videoGameRepository.getByIds(box.getVideoGameIds());

        assertEquals(2, games.size());
        for (VideoGame game : games) {
            assertEquals(List.of(box.getId()), game.getVideoGameBoxIds(),
                    "getByIds() should hydrate the related video game box ids on every game.");
        }
    }

    @Test
    void getDeletedById_VideoGameSoftDeleted_RelatedBoxIdsHydrated() {
        final VideoGameBox box = createVideoGameBox("AfterLoad Deleted Box", List.of("AfterLoad Deleted Game"));
        final int gameId = box.getVideoGameIds().get(0);
        videoGameRepository.deleteById(gameId);

        final VideoGame deletedGame = videoGameRepository.getDeletedById(gameId);

        assertNotNull(deletedGame.getDeletedAt());
        assertEquals(List.of(box.getId()), deletedGame.getVideoGameBoxIds(),
                "getDeletedById() should hydrate the related video game box ids just like getById().");
    }

    @Test
    void getByIdIncludeDeleted_VideoGameSoftDeleted_RelatedBoxIdsHydrated() {
        final VideoGameBox box = createVideoGameBox("AfterLoad IncludeDeleted Box", List.of("AfterLoad IncludeDeleted Game"));
        final int gameId = box.getVideoGameIds().get(0);
        videoGameRepository.deleteById(gameId);

        final VideoGame deletedGame = videoGameRepository.getByIdIncludeDeleted(gameId);

        assertEquals(List.of(box.getId()), deletedGame.getVideoGameBoxIds(),
                "getByIdIncludeDeleted() should hydrate the related video game box ids just like getById().");
    }

    @Test
    void getByIdsIncludeDeleted_MixOfActiveAndDeletedVideoGames_RelatedBoxIdsHydrated() {
        final VideoGameBox box = createVideoGameBox("AfterLoad Mixed Box", List.of("AfterLoad Mixed Game 1", "AfterLoad Mixed Game 2"));
        final int deletedGameId = box.getVideoGameIds().get(0);
        videoGameRepository.deleteById(deletedGameId);

        final List<VideoGame> games = videoGameRepository.getByIdsIncludeDeleted(box.getVideoGameIds());

        assertEquals(2, games.size());
        for (VideoGame game : games) {
            assertEquals(List.of(box.getId()), game.getVideoGameBoxIds(),
                    "getByIdsIncludeDeleted() should hydrate the related video game box ids on every game.");
        }
    }

    @Test
    void getByIdIncludeDeleted_VideoGameBoxExists_RelatedGameIdsHydrated() {
        final VideoGameBox box = createVideoGameBox("AfterLoad Box Hydration", List.of("AfterLoad Box Hydration Game"));

        final VideoGameBox loadedBox = videoGameBoxRepository.getByIdIncludeDeleted(box.getId());

        assertFalse(loadedBox.getVideoGameIds().isEmpty(),
                "getByIdIncludeDeleted() should hydrate the related video game ids just like getById().");
        assertEquals(box.getVideoGameIds(), loadedBox.getVideoGameIds());
    }

    private VideoGameBox createVideoGameBox(String boxTitle, List<String> gameTitles) {
        final System system = systemRepository.insert(new System(null, boxTitle + " System", 1, false, null, null, null, new ArrayList<>()));
        final List<VideoGameRequestDto> newVideoGames = new ArrayList<>();
        for (String gameTitle : gameTitles) {
            newVideoGames.add(new VideoGameRequestDto(gameTitle, system.getId(), new ArrayList<>()));
        }
        final VideoGameBoxRequestDto requestDto = new VideoGameBoxRequestDto(boxTitle, system.getId(), new ArrayList<>(), newVideoGames, true, new ArrayList<>());
        return videoGameBoxService.createNew(requestDto);
    }
}
