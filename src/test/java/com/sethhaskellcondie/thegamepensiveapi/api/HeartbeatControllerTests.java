package com.sethhaskellcondie.thegamepensiveapi.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

//if you give a port and configuration for the @SpringBootTest annotation this will start an embedded servlet container, use WebTestClient/TestRestTemplate
//@SpringBootTest( webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//if a port is not provided for the @SpringBootTest then the application will be run in memory, use MockMvc
@SpringBootTest
@AutoConfigureMockMvc
public class HeartbeatControllerTests
{

	@Autowired
	private MockMvc client;

//	@Test
//	void testHeartbeat_HappyPath_ReturnOk () {
//		//this uses a WebTestClient instead of MockMvc
//		client.get().uri("/heartbeat").exchange().expectStatus().isEqualTo(HttpStatus.OK); //expectStatus().isOk();
//	}

//	@Test
//	void testHeartbeat_HappyPath_ReturnOk () {
//		client
//	}
}
