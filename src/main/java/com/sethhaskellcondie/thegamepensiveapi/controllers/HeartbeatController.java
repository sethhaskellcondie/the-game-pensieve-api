package com.sethhaskellcondie.thegamepensiveapi.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController tells spring that this class is a controller and may contain methods used to handle incoming requests
@RestController
public class HeartbeatController
{
	// @GetMapping is a composed annotation it acts as a shortcut for @RequestMapping(method = RequestMethod.GET)
	// This takes a route as a parameter and when a request is routed here this method is called.
	// example request is to localhost:8080/heartbeat
	@GetMapping("/heartbeat")
	public String heartbeat() {
		//return a 200?
		//is 200 the default?
		return "thump thump";
	}
}
