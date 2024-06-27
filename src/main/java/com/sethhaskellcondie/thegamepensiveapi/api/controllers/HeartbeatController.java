package com.sethhaskellcondie.thegamepensiveapi.api.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HeartbeatController {

    public HeartbeatController() { }

    @GetMapping("/v1/heartbeat")
    public String heartbeat() {
        return "thump thump";
    }

}
