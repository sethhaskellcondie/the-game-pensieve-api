package com.sethhaskellcondie.thegamepensieveapi.domain.backupimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensieveapi.domain.metadata.Metadata;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The /backup endpoint serializes BackupDataDto to a file with a default ObjectMapper. Metadata timestamps use
 * java.sql.Timestamp (like every other entity), which serializes to epoch-millis. This test guards against
 * regressing Metadata back to a java.time type, which a default ObjectMapper cannot serialize and which broke
 * the backup with an IOException.
 */
public class BackupDataSerializationTests {

    private static final long EPOCH_MILLIS = 1759175142585L;

    private BackupDataDto backupDataWithMetadata() {
        final Timestamp timestamp = new Timestamp(EPOCH_MILLIS);
        final Metadata metadata = new Metadata(4, "goals", "{}", timestamp, timestamp, null);
        return new BackupDataDto(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(metadata));
    }

    @Test
    void metadataSerializesAsEpochMillisLikeOtherEntities() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final String json = assertDoesNotThrow(() -> mapper.writeValueAsString(backupDataWithMetadata()),
                "A default ObjectMapper must serialize Metadata without error - serializing it threw before this fix.");
        final JsonNode createdAt = mapper.readTree(json).get("metadata").get(0).get("createdAt");
        assertTrue(createdAt.isNumber(), "Metadata createdAt must serialize as epoch-millis, consistent with the rest of the system.");
        assertEquals(EPOCH_MILLIS, createdAt.asLong());
    }

    @Test
    void backupDataWithMetadataRoundTrips() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final BackupDataDto original = backupDataWithMetadata();
        final String json = mapper.writeValueAsString(original);
        final BackupDataDto roundTripped = mapper.readValue(json, BackupDataDto.class);
        assertEquals(original.metadata(), roundTripped.metadata());
    }
}
