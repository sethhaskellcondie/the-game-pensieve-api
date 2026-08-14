package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.sethhaskellcondie.thegamepensieveapi.SecuredProfileTest;
import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import com.sethhaskellcondie.thegamepensieveapi.domain.backupimport.LocalBackupFileStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The security half of the file-backed backup split: under {@code secured}, nothing writes or reads the shared
 * {@code backup.json}, and the route that would import it does not exist.
 * <p>
 * This is a cross-tenant leak guard, and it is worth asserting rather than assuming. {@code backup.json} is one
 * process-global path with no record of who wrote it: on a multi-tenant server, tenant A's backup is overwritten
 * by tenant B's, and a restore imports whichever collection happens to be on disk into the caller's account. The
 * capability gate cannot catch that — both callers legitimately hold BACKUP and IMPORT, so every individual
 * request is authorized. The leak is the shared file, which is why the fix is that a hosted build has no file at
 * all rather than a tighter permission.
 * <p>
 * Counterpart: {@link LocalFileBackupDefaultProfileTests} pins the round trip the single-user build keeps.
 */
@SpringBootTest
@ActiveProfiles({"test-container", "secured"})
@AutoConfigureMockMvc
public class LocalFileBackupSecuredProfileTests extends SecuredProfileTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationContext context;
    private TestFactory factory;
    private static final String PASSWORD = "Sup3rSecret!";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    @Test
    void localBackupFileStore_DoesNotExistUnderSecured() {
        assertEquals(0, context.getBeanNamesForType(LocalBackupFileStore.class).length,
                "No bean may exist that can write or read the shared backup.json in a multi-tenant build.");
    }

    /**
     * Authenticated on purpose. An anonymous request would be rejected at the security chain with a 401 before
     * routing, which proves nothing about whether the handler exists — a real token is what makes the 404
     * meaningful.
     */
    @Test
    void importFromFile_IsNotRoutedUnderSecured() throws Exception {
        final String accessToken = factory.tokenFor(factory.randomEmail(), PASSWORD);

        mockMvc.perform(post("/v1/function/importFromFile")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    /**
     * The backup endpoint itself survives — only its file side effect is gone. Losing it would take the web
     * client's download with it, so the split has to keep this working.
     */
    @Test
    void backup_StillWorksUnderSecured() throws Exception {
        final String accessToken = factory.tokenFor(factory.randomEmail(), PASSWORD);

        mockMvc.perform(post("/v1/function/backup")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }
}
