package com.sethhaskellcondie.thegamepensiveapi.api.controllers;

import com.sethhaskellcondie.thegamepensiveapi.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test-container")
@AutoConfigureMockMvc
public class BoardGameTests {

    @Autowired
    private MockMvc mockMvc;
    private TestFactory factory;
    private final String baseUrl = "/v1/boardGames";
    private final String baseUrlSlash = "/v1/boardGames/";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    //TODO finish this
}
