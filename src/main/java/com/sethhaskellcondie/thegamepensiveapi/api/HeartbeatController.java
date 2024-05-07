package com.sethhaskellcondie.thegamepensiveapi.api;

import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemGateway;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController tells spring that this class is a controller and may contain methods used to handle incoming requests
@RestController
public class HeartbeatController {

    private SystemGateway gateway;

    public HeartbeatController(SystemGateway gateway) {
        this.gateway = gateway;
    }

    // @GetMapping is a composed annotation it acts as a shortcut for @RequestMapping(method = RequestMethod.GET)
    // This takes a route as a parameter and when a request is routed here this method is called.
    // example request is to localhost:8080/heartbeat
    @GetMapping("/heartbeat")
    public String heartbeat() {
        return "thump thump";
    }

    //This endpoint will only see the systems in my collection
    @PostMapping("/seedMyCollection")
    public String seedMyCollectionData() throws ExceptionFailedDbValidation {
        gateway.createNew("NES", 3, false);
        gateway.createNew("SNES", 4, false);
        gateway.createNew("Genesis", 4, false);
        gateway.createNew("Sega CD", 4, false);
        gateway.createNew("Game Boy", 4, true);
        gateway.createNew("Game Gear", 4, true);
        gateway.createNew("Playstation", 5, false);
        gateway.createNew("Nintendo 64", 5, false);
        gateway.createNew("Game Boy Color", 5, true);
        gateway.createNew("Dreamcast", 6, false);
        gateway.createNew("Gamecube", 6, false);
        gateway.createNew("Playstation 2", 6, false);
        gateway.createNew("Game Boy Advance", 6, true);
        gateway.createNew("Wii", 7, false);
        gateway.createNew("Playstation 3", 7, false);
        gateway.createNew("Nintendo DS", 7, true);
        gateway.createNew("Wii U", 8, false);
        gateway.createNew("Nintendo 3DS", 8, true);
        gateway.createNew("Switch", 9, false);
        gateway.createNew("Arcade", 100, false);
        gateway.createNew("PC", 101, false);
        return "Seeding Successful";
    }
}
