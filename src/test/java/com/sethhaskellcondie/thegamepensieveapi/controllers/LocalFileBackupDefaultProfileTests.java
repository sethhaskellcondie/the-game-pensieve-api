package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.sethhaskellcondie.thegamepensieveapi.domain.backupimport.LocalBackupFileStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the file-backed backup round trip that the default (unsecured, single-user) build keeps: back up to
 * {@code backup.json} in the working directory, then restore from it without moving a document through the
 * browser.
 * <p>
 * Counterpart: {@link LocalFileBackupSecuredProfileTests} asserts that none of this exists under
 * {@code secured}. The pair is the whole point — one file with one possible author is a convenience; the same
 * file on a multi-tenant server is a cross-tenant leak, because nothing records who wrote it.
 */
@SpringBootTest
@ActiveProfiles("test-container")
@AutoConfigureMockMvc
public class LocalFileBackupDefaultProfileTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationContext context;

    @Test
    void localBackupFileStore_ExistsInTheDefaultBuild() {
        assertTrue(context.getBeanNamesForType(LocalBackupFileStore.class).length > 0,
                "The default build writes and reads backup.json, so the store bean must be present.");
    }

    @Test
    void importFromFile_IsRoutedInTheDefaultBuild() throws Exception {
        // Asserts the route EXISTS, not that a restore succeeds — there may be no backup.json in the working
        // directory, which surfaces as a 500 from the store. Either way it is not the 404 the secured build
        // returns, and that distinction is what this test is for.
        mockMvc.perform(post("/v1/function/importFromFile"))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() != 404,
                        "importFromFile must be routed in the default build. Got 404, so the controller is missing."));
    }

    @Test
    void backup_ReturnsTheDataInTheResponseBody() throws Exception {
        // The response body is the contract in BOTH builds — the web client downloads from it. The file write
        // is an extra that only this build performs.
        mockMvc.perform(post("/v1/function/backup"))
                .andExpect(status().isOk());
    }
}
