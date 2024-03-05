package com.sethhaskellcondie.thegamepensiveapi.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

//if a port is not provided for the @SpringBootTest then the application will be run in memory, use MockMvc
//see the SystemsWebTests.java for an example of MockMvc

//if you provide a port and configuration for the @SpringBootTest annotation this will start an embedded servlet container,
//use WebTestClient/TestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class HeartbeatControllerTests {
    @Autowired
    WebTestClient client;

    @Test
    void testHeartbeat_HappyPath_ReturnOk() {
        client.get().uri("/heartbeat").exchange().expectStatus().isOk();
    }
}
