package com.sethhaskellcondie.thegamepensieveapi.domain.counts;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGameRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox.BoardGameBoxRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInternalError;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owner-scoped item counts for every entity type on the {@link Keychain}. Each count is asked of that entity's own
 * repository, so all the SQL stays in the repository layer. Runs inside the per-request tenant transaction, so
 * Row-Level Security scopes each count to the acting owner (and {@code X-Showcase} views work unchanged). Counts
 * match what an empty {@code function/search} returns: soft-deleted rows are excluded. No capability check,
 * mirroring the unfiltered search the same data is already reachable through.
 */
@Component
public class CollectionCountsGateway {

    private final Map<String, EntityRepository<?, ?, ?>> repositoriesByKey;

    public CollectionCountsGateway(SystemRepository systemRepository, ToyRepository toyRepository,
                                   VideoGameRepository videoGameRepository, VideoGameBoxRepository videoGameBoxRepository,
                                   BoardGameRepository boardGameRepository, BoardGameBoxRepository boardGameBoxRepository) {
        //When a new entity is added to the Keychain its repository needs to be registered here as well.
        this.repositoriesByKey = Map.of(
                Keychain.SYSTEM_KEY, systemRepository,
                Keychain.TOY_KEY, toyRepository,
                Keychain.VIDEO_GAME_KEY, videoGameRepository,
                Keychain.VIDEO_GAME_BOX_KEY, videoGameBoxRepository,
                Keychain.BOARD_GAME_KEY, boardGameRepository,
                Keychain.BOARD_GAME_BOX_KEY, boardGameBoxRepository
        );
    }

    public CollectionCountsDto getCounts() {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        int total = 0;
        for (String key : Keychain.getAllKeys()) {
            final EntityRepository<?, ?, ?> repository = repositoriesByKey.get(key);
            if (repository == null) {
                throw new ExceptionInternalError("CollectionCountsGateway has no repository registered for the entity key: " + key);
            }
            final int count = repository.getCount();
            counts.put(key, count);
            total += count;
        }
        return new CollectionCountsDto(counts, total);
    }
}
