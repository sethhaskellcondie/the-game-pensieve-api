package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the permit-all behavior of the default (unsecured) build: the public showcase keeps working with no credentials.
 * <p>
 * Counterpart: {@link AuthSecuredProfileTests} exercises the authenticated {@code secured} build.
 */
@SpringBootTest
@ActiveProfiles("test-container")
@AutoConfigureMockMvc
public class AuthDefaultProfileTests {

    @Autowired
    private MockMvc mockMvc;
    private TestFactory factory;

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    @Test
    void getHeartbeat_NoAuth_ReturnsOk() throws Exception {
        mockMvc.perform(get("/v1/heartbeat"))
                .andExpect(status().isOk());
    }

    @Test
    void getProtectedRoute_NoAuth_DefaultProfile_ReturnsOk() throws Exception {
        // The "list" endpoint is a protected route once secured; in the default profile it stays open.
        mockMvc.perform(
                post("/v1/systems/function/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filters\": []}")
        ).andExpectAll(
                status().isOk(),
                jsonPath("$.errors").isEmpty()
        );
    }

    @Test
    void postEntity_NoAuth_DefaultProfile_Succeeds() throws Exception {
        // Writing with no credentials still works today (postSystemReturnResult asserts 201 Created internally).
        factory.postSystemReturnResult();
    }

    @Test
    void getMe_NoAuth_DefaultProfile_ResolvesToShowcaseGuest() throws Exception {
        // With no authentication the request resolves to the seeded public showcase owner, which is always GUEST.
        mockMvc.perform(get("/v1/auth/me"))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.data.id").isNumber(),
                        jsonPath("$.data.role").value("GUEST"),
                        jsonPath("$.errors").isEmpty()
                );
    }
}
