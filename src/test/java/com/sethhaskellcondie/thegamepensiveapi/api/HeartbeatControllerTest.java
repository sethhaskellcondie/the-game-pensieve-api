package com.sethhaskellcondie.thegamepensiveapi.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureMockMvc
public class HeartbeatControllerTest {

	@Autowired
	WebTestClient client;

	@Test
	void testHeartbeat_HappyPath_ReturnOk () {
		client.get().uri("/heartbeat").exchange().expectStatus().isEqualTo(HttpStatus.OK);
	}
}
