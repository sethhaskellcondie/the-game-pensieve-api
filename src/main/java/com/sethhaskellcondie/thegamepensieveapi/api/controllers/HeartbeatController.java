package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HeartbeatController extends BaseController {

    public HeartbeatController() { }

    @GetMapping("/v1/heartbeat")
    public ApiResponse<String> heartbeat(HttpServletRequest request) {
        return buildResponse("thump thump", request);
    }
}
