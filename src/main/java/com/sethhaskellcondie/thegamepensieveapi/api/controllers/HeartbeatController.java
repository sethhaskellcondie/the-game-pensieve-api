package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HeartbeatController extends BaseController {

    private final Environment environment;

    public HeartbeatController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/v1/heartbeat")
    public ApiResponse<HeartbeatResponseDto> heartbeat(HttpServletRequest request) {
        final boolean secureMode = environment.acceptsProfiles(Profiles.of("secured"));
        return buildResponse(new HeartbeatResponseDto("thump thump", secureMode), request);
    }
}
