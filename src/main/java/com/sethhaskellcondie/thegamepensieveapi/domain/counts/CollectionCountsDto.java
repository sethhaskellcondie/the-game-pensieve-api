package com.sethhaskellcondie.thegamepensieveapi.domain.counts;

import java.util.Map;

/**
 * Item counts per entity key (in {@link com.sethhaskellcondie.thegamepensieveapi.domain.Keychain} order) plus
 * their sum. The wire shape of {@code GET /v1/function/counts}.
 */
public record CollectionCountsDto(Map<String, Integer> counts, int total) {
}
