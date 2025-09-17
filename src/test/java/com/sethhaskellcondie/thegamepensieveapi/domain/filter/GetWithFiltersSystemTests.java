package com.sethhaskellcondie.thegamepensieveapi.domain.filter;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGame;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBox;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxService;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameService;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is part of an extensive set of tests on the GetWithFilters() call
 * it is called the same way for each entity in the EntityRepository.
 * <p>
 * The test suites have been broken up to reduce the complexity of the data set
 * that is being queried.
 * <p>
 * By the end every Filter in this system will have been tests against the
 * systems table. Then each entity will get that it works with the getWithFilters()
 * call. This is considered 'good enough' we don't need to test every filter on
 * every entity.
 */
@JdbcTest
@ActiveProfiles("filter-tests3") //Also used in GetWithFiltersCustomFieldTextTests
public class GetWithFiltersSystemTests {

    @Autowired
    protected JdbcTemplate jdbcTemplate;
    protected SystemRepository systemRepository;
    protected TestVideoGameRepository videoGameRepository;
    protected TestVideoGameBoxRepository videoGameBoxRepository;
    protected VideoGameService videoGameService;
    protected VideoGameBoxService videoGameBoxService;
    protected FilterService filterService;
    protected CustomFieldRepository customFieldRepository;

    // Create test wrapper classes for repositories with protected constructors
    static class TestVideoGameRepository extends VideoGameRepository {
        TestVideoGameRepository(JdbcTemplate jdbcTemplate) {
            super(jdbcTemplate);
        }
    }

    static class TestVideoGameBoxRepository extends VideoGameBoxRepository {
        TestVideoGameBoxRepository(JdbcTemplate jdbcTemplate) {
            super(jdbcTemplate);
        }
    }

    @BeforeEach
    public void setUp() {
        systemRepository = new SystemRepository(jdbcTemplate);
        videoGameRepository = new TestVideoGameRepository(jdbcTemplate);
        videoGameBoxRepository = new TestVideoGameBoxRepository(jdbcTemplate);
        customFieldRepository = new CustomFieldRepository(jdbcTemplate);
        filterService = new FilterService(customFieldRepository);
        videoGameService = new VideoGameService(videoGameRepository, filterService, systemRepository, videoGameBoxRepository);
        videoGameBoxService = new VideoGameBoxService(videoGameBoxRepository, filterService, systemRepository, videoGameService);
    }

    @Test
    void testSystemFilters() {
        // Insert systems first
        System nes = systemRepository.insert(new System(null, "NES", 3, false, null, null, null, new ArrayList<>()));
        System snes = systemRepository.insert(new System(null, "SNES", 4, false, null, null, null, new ArrayList<>()));
        System genesis = systemRepository.insert(new System(null, "Genesis", 4, false, null, null, null, new ArrayList<>()));

        // Create video game boxes using VideoGameBoxService (which creates video games internally)
        createVideoGameBox("Super Mario Bros Collection", nes.getId(), true,
                List.of("Super Mario Bros", "Super Mario Bros 2"));
        createVideoGameBox("Super Mario World", snes.getId(), true,
                List.of("Super Mario World"));
        createVideoGameBox("Legend of Zelda Collection", nes.getId(), false,
                List.of("The Legend of Zelda", "Zelda II"));
        createVideoGameBox("Sonic Genesis Collection", genesis.getId(), true,
                List.of("Sonic the Hedgehog", "Sonic 2"));

        // Test video game filtering (games created by video game boxes)
        testVideoGameNoFilters(7);
        testVideoGameSystemEquals(nes.getId(), 4);  // Super Mario Bros, Super Mario Bros 2, The Legend of Zelda, Zelda II
        testVideoGameSystemEquals(snes.getId(), 1); // Super Mario World
        testVideoGameSystemEquals(genesis.getId(), 2); // Sonic the Hedgehog, Sonic 2
        testVideoGameSystemNotEquals(nes.getId(), 3); // 7 - 4 = 3
        testVideoGameSystemNotEquals(snes.getId(), 6); // 7 - 1 = 6
        testVideoGameSystemNotEquals(genesis.getId(), 5); // 7 - 2 = 5

        // Test video game box filtering
        testVideoGameBoxNoFilters(4);
        testVideoGameBoxSystemEquals(nes.getId(), 2);
        testVideoGameBoxSystemEquals(snes.getId(), 1);
        testVideoGameBoxSystemEquals(genesis.getId(), 1);
        testVideoGameBoxSystemNotEquals(nes.getId(), 2);
        testVideoGameBoxSystemNotEquals(snes.getId(), 3);
        testVideoGameBoxSystemNotEquals(genesis.getId(), 3);
    }

    // Video Game filtering tests
    void testVideoGameNoFilters(int expectedResults) {
        final List<Filter> filters = List.of();

        final List<VideoGame> results = videoGameRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of video game results returned when testing with no filters.");
    }

    void testVideoGameSystemEquals(int systemId, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.VIDEO_GAME_KEY, Filter.FIELD_TYPE_SYSTEM, "system_id", Filter.OPERATOR_EQUALS, Integer.toString(systemId), false)
        );

        final List<VideoGame> results = videoGameRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of video game results returned when testing system filter 'equals.'");
    }

    void testVideoGameSystemNotEquals(int systemId, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.VIDEO_GAME_KEY, Filter.FIELD_TYPE_SYSTEM, "system_id", Filter.OPERATOR_NOT_EQUALS, Integer.toString(systemId), false)
        );

        final List<VideoGame> results = videoGameRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of video game results returned when testing system filter 'not_equals.'");
    }

    // Video Game Box filtering tests
    void testVideoGameBoxNoFilters(int expectedResults) {
        final List<Filter> filters = List.of();

        final List<VideoGameBox> results = videoGameBoxRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of video game box results returned when testing with no filters.");
    }

    void testVideoGameBoxSystemEquals(int systemId, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.VIDEO_GAME_BOX_KEY, Filter.FIELD_TYPE_SYSTEM, "system_id", Filter.OPERATOR_EQUALS, Integer.toString(systemId), false)
        );

        final List<VideoGameBox> results = videoGameBoxRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of video game box results returned when testing system filter 'equals.'");
    }

    void testVideoGameBoxSystemNotEquals(int systemId, int expectedResults) {
        final List<Filter> filters = List.of(
                new Filter(Keychain.VIDEO_GAME_BOX_KEY, Filter.FIELD_TYPE_SYSTEM, "system_id", Filter.OPERATOR_NOT_EQUALS, Integer.toString(systemId), false)
        );

        final List<VideoGameBox> results = videoGameBoxRepository.getWithFilters(filters);

        assertEquals(expectedResults, results.size(), "Wrong number of video game box results returned when testing system filter 'not_equals.'");
    }

    private void createVideoGameBox(String boxTitle, int systemId, boolean isPhysical, List<String> gameNames) {
        List<VideoGameRequestDto> newVideoGames = new ArrayList<>();
        for (String gameName : gameNames) {
            newVideoGames.add(new VideoGameRequestDto(gameName, systemId, new ArrayList<>()));
        }

        VideoGameBoxRequestDto requestDto = new VideoGameBoxRequestDto(
                boxTitle,
                systemId,
                new ArrayList<>(),
                newVideoGames,
                isPhysical,
                new ArrayList<>()
        );

        videoGameBoxService.createNew(requestDto);
    }
}