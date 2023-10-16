package com.sethhaskellcondie.thegamepensiveapi.controllers;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController tells spring that this class is a controller and may contain methods used to handle incoming requests
@RestController
public class HeartbeatController
{

	private final JdbcTemplate jdbcTemplate;

	public HeartbeatController(JdbcTemplate jdbcTemplate)
	{
		this.jdbcTemplate = jdbcTemplate;
	}

	// @GetMapping is a composed annotation it acts as a shortcut for @RequestMapping(method = RequestMethod.GET)
	// This takes a route as a parameter and when a request is routed here this method is called.
	// example request is to localhost:8080/heartbeat
	@GetMapping("/heartbeat")
	public String heartbeat() {
		//return a 200?
		//is 200 the default?
		return "thump thump";
	}

	//TODO create an endpoint for a more complete (default) seeding of data

	//This endpoint will only see the systems in my collection
	@PostMapping("/seedMyCollection")
	public String seedMyCollectionData() {
		//Seeding systems data
		seedSystem("NES", 3, false);
		seedSystem("SNES", 4, false);
		seedSystem("Genesis", 4, false);
		seedSystem("Sega CD", 4, false);
		seedSystem("Game Boy", 4, true);
		seedSystem("Game Gear", 4, true);
		seedSystem("Playstation", 5, false);
		seedSystem("Nintendo 64", 5, false);
		seedSystem("Game Boy Color", 5, true);
		seedSystem("Dreamcast", 6, false);
		seedSystem("Gamecube", 6, false);
		seedSystem("Playstation 2", 6, false);
		seedSystem("Game Boy Advance", 6, true);
		seedSystem("Wii", 7, false);
		seedSystem("Playstation 3", 7, false);
		seedSystem("Nintendo DS", 7, true);
		seedSystem("Wii U", 8, false);
		seedSystem("Nintendo 3DS", 8, true);
		seedSystem("Switch", 9, false);
		seedSystem("Arcade", 100, false);
		seedSystem("PC", 101, false);
		return "Seeding Successful";
	}
	
	private void seedSystem(String name, int generation, boolean handheld) {
		String sql = """
   			INSERT INTO systems(name, generation, handheld) VALUES (?, ?, ?);
			""";
		jdbcTemplate.update(sql, name, generation, handheld);
	}
}
