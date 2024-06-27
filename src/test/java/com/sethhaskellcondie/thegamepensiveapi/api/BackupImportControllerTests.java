package com.sethhaskellcondie.thegamepensiveapi.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test-container")
@AutoConfigureMockMvc
public class BackupImportControllerTests {
    @Autowired
    private MockMvc mockMvc;

    //TODO finish fleshing out these tests
    @Test
    void testImportCustomFields_InvalidFields_ReturnErrors() {
    }
}
