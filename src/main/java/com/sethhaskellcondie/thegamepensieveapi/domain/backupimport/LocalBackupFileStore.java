package com.sethhaskellcondie.thegamepensieveapi.domain.backupimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInternalError;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * A backup.json file in the application's working directory, used by the default (unsecured, single-user) build
 * as a convenience round trip: back up to the file, then restore from it without moving a document through the
 * browser.
 *
 * <p><strong>This bean does not exist under the {@code secured} profile, and must not.</strong> The file is a
 * single, process-global path with no notion of who wrote it, while a secured deployment is multi-tenant and
 * every backup is RLS-scoped to one caller. Sharing one file across tenants means tenant B's backup overwrites
 * tenant A's, and — far worse — a restore reads whatever happens to be on disk and imports another tenant's
 * entire collection into the caller. The capability gate cannot catch this: both callers legitimately hold
 * BACKUP and IMPORT, so every individual request is authorized. The leak is the shared file, not the
 * permissions, so the fix is that a hosted build has no file at all.
 *
 * <p>Secured builds move backups through the HTTP layer instead, where each document belongs to one request:
 * {@code POST /v1/function/backup} returns the export in its response body and {@code POST /v1/function/import}
 * takes one in a request body. The web client already downloads from that response.
 */
@Service
@Profile("!secured")
public class LocalBackupFileStore {

    private static final String BACKUP_DATA_PATH = "backup.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void write(BackupDataDto backupData) {
        final File file = new File(BACKUP_DATA_PATH);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, backupData);
        } catch (IOException e) {
            throw new ExceptionInternalError("Failed to write backup data to file: " + BACKUP_DATA_PATH, e);
        }
    }

    public BackupDataDto read() {
        try {
            final byte[] fileData = Files.readAllBytes(Paths.get(BACKUP_DATA_PATH));
            return objectMapper.readValue(fileData, BackupDataDto.class);
        } catch (IOException e) {
            throw new ExceptionInternalError("Failed to read backup data from file: " + BACKUP_DATA_PATH, e);
        }
    }
}
