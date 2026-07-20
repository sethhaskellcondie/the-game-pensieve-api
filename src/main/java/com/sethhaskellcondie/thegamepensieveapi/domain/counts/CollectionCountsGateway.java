package com.sethhaskellcondie.thegamepensieveapi.domain.counts;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owner-scoped item counts for every entity type on the {@link Keychain}. Runs inside the per-request tenant
 * transaction, so Row-Level Security scopes each count to the acting owner (and {@code X-Showcase} views work
 * unchanged). Counts match what an empty {@code function/search} returns: soft-deleted rows are excluded. No
 * capability check, mirroring the unfiltered search the same data is already reachable through.
 */
@Component
public class CollectionCountsGateway {

    private final JdbcTemplate jdbcTemplate;

    public CollectionCountsGateway(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CollectionCountsDto getCounts() {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        int total = 0;
        for (String key : Keychain.getAllKeys()) {
            // The table name is a Keychain constant (the same alias the filter SQL builds from), never user input.
            final String table = Keychain.getTableAliasByKey(key);
            final Integer count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + table + " WHERE deleted_at IS NULL", Integer.class);
            counts.put(key, count);
            total += count;
        }
        return new CollectionCountsDto(counts, total);
    }
}
