package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HeartbeatController extends BaseController {

    private final Environment environment;
    private final String version;

    //pensieve.version is the Maven <version> of this project, substituted into application.properties by
    //Maven resource filtering at build time - see that file. A build that skips resource filtering (running
    //the class straight out of an IDE without a maven process-resources) leaves the placeholder unresolved,
    //so the default keeps the endpoint answering instead of failing to start on a missing property.
    public HeartbeatController(Environment environment, @Value("${pensieve.version:unknown}") String version) {
        this.environment = environment;
        this.version = version;
    }

    @GetMapping("/v1/heartbeat")
    public ApiResponse<HeartbeatResponseDto> heartbeat(HttpServletRequest request) {
        final boolean secureMode = environment.acceptsProfiles(Profiles.of("secured"));
        return buildResponse(new HeartbeatResponseDto("thump thump", secureMode, version), request);
    }
}
