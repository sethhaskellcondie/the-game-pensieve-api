package com.sethhaskellcondie.thegamepensiveapi.api;

import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.toy.ToyGateway;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController tells spring that this class is a controller and may contain methods used to handle incoming requests
@RestController
public class HeartbeatController {

    private final SystemGateway systemGateway;
    private final ToyGateway toyGateway;
    private final JdbcTemplate jdbcTemplate;

    public HeartbeatController(SystemGateway systemGateway, ToyGateway toyGateway, JdbcTemplate jdbcTemplate) {
        this.systemGateway = systemGateway;
        this.toyGateway = toyGateway;
        this.jdbcTemplate = jdbcTemplate;
    }

    // @GetMapping is a composed annotation it acts as a shortcut for @RequestMapping(method = RequestMethod.GET)
    // This takes a route as a parameter and when a request is routed here this method is called.
    // example request is to localhost:8080/heartbeat
    @GetMapping("/heartbeat")
    public String heartbeat() {
        return "thump thump";
    }

    //This endpoint will only see the systems in my collection
    @Deprecated
    @PostMapping("/seedMyCollection")
    public String seedMyCollectionData() throws ExceptionFailedDbValidation {
        systemGateway.createNew("NES", 3, false);
        systemGateway.createNew("SNES", 4, false);
        systemGateway.createNew("Genesis", 4, false);
        systemGateway.createNew("Sega CD", 4, false);
        systemGateway.createNew("Game Boy", 4, true);
        systemGateway.createNew("Game Gear", 4, true);
        systemGateway.createNew("Playstation", 5, false);
        systemGateway.createNew("Nintendo 64", 5, false);
        systemGateway.createNew("Game Boy Color", 5, true);
        systemGateway.createNew("Dreamcast", 6, false);
        systemGateway.createNew("Gamecube", 6, false);
        systemGateway.createNew("Playstation 2", 6, false);
        systemGateway.createNew("Game Boy Advance", 6, true);
        systemGateway.createNew("Wii", 7, false);
        systemGateway.createNew("Playstation 3", 7, false);
        systemGateway.createNew("Nintendo DS", 7, true);
        systemGateway.createNew("Wii U", 8, false);
        systemGateway.createNew("Nintendo 3DS", 8, true);
        systemGateway.createNew("Switch", 9, false);
        systemGateway.createNew("Arcade", 100, false);
        systemGateway.createNew("PC", 101, false);

        toyGateway.createNew("Super Mario", "Amiibo");
        toyGateway.createNew("Super Mario", "Amiibo");
        toyGateway.createNew("Mega Man", "Amiibo");
        toyGateway.createNew("Link", "Amiibo");
        toyGateway.createNew("Donkey Kong", "Amiibo");
        toyGateway.createNew("Mickey Mouse", "Disney Infinity");
        toyGateway.createNew("Donald Duck", "Disney Infinity");
        toyGateway.createNew("Goofy", "Disney Infinity");
        toyGateway.createNew("Captain Jack", "Disney Infinity");
        toyGateway.createNew("Iron Man", "Disney Infinity");

        return "Seeding Successful";
    }
}
