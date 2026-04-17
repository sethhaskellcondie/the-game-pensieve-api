package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("v1/videoGames")
public class VideoGameController extends BaseController {
    private final VideoGameGateway gateway;

    public VideoGameController(VideoGameGateway gateway) {
        this.gateway = gateway;
    }

    @ResponseBody
    @GetMapping("/{id}")
    public ApiResponse<VideoGameResponseDto> getById(@PathVariable int id, HttpServletRequest request) throws ExceptionResourceNotFound {
        final VideoGameResponseDto responseDto = gateway.getById(id);
        return buildResponse(responseDto, request);
    }

    @ResponseBody
    @PostMapping("/function/search")
    public ApiResponse<List<VideoGameResponseDto>> getWithFilters(@RequestBody Map<String, List<FilterRequestDto>> requestBody, HttpServletRequest request) {
        final List<VideoGameResponseDto> data = gateway.getWithFilters(requestBody.get("filters"));
        return buildResponse(data, request);
    }

    @ResponseBody
    @PutMapping("/{id}")
    public ApiResponse<VideoGameResponseDto> updateExisting(@PathVariable int id, @RequestBody Map<String, VideoGameRequestDto> requestBody, HttpServletRequest request) {
        final VideoGameResponseDto responseDto = gateway.updateExisting(id, requestBody.get(Keychain.VIDEO_GAME_KEY));
        return buildResponse(responseDto, request);
    }
}
