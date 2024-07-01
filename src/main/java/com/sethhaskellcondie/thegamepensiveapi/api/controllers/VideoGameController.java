package com.sethhaskellcondie.thegamepensiveapi.api.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sethhaskellcondie.thegamepensiveapi.api.FormattedResponseBody;
import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionResourceNotFound;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterRequestDto;

@RestController
@RequestMapping("v1/videoGames")
public class VideoGameController {
    private final VideoGameGateway gateway;

    public VideoGameController(VideoGameGateway gateway) {
        this.gateway = gateway;
    }

    @ResponseBody
    @GetMapping("/{id}")
    public Map<String, VideoGameResponseDto> getById(@PathVariable int id) throws ExceptionResourceNotFound {
        final VideoGameResponseDto responseDto = gateway.getById(id);
        final FormattedResponseBody<VideoGameResponseDto> body = new FormattedResponseBody<>(responseDto);
        return body.formatData();
    }

    @ResponseBody
    @PostMapping("/function/search")
    public Map<String, List<VideoGameResponseDto>> getWithFilters(@RequestBody Map<String, List<FilterRequestDto>> requestBody) {
        final List<VideoGameResponseDto> data = gateway.getWithFilters(requestBody.get("filters"));
        final FormattedResponseBody<List<VideoGameResponseDto>> body = new FormattedResponseBody<>(data);
        return body.formatData();
    }

    @ResponseBody
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, VideoGameResponseDto> createNew(@RequestBody Map<String, VideoGameRequestDto> requestBody) throws ExceptionFailedDbValidation {
        final VideoGameResponseDto responseDto = gateway.createNew(requestBody.get(Keychain.VIDEO_GAME_KEY));
        final FormattedResponseBody<VideoGameResponseDto> body = new FormattedResponseBody<>(responseDto);
        return body.formatData();
    }

    @ResponseBody
    @PutMapping("/{id}")
    public Map<String, VideoGameResponseDto> updateExisting(@PathVariable int id, @RequestBody Map<String, VideoGameRequestDto> requestBody) throws ExceptionResourceNotFound, ExceptionFailedDbValidation {
        final VideoGameResponseDto responseDto = gateway.updateExisting(id, requestBody.get(Keychain.VIDEO_GAME_KEY));
        final FormattedResponseBody<VideoGameResponseDto> body = new FormattedResponseBody<>(responseDto);
        return body.formatData();
    }

    @ResponseBody
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Map<String, String> deleteExisting(@PathVariable int id) throws ExceptionResourceNotFound {
        gateway.deleteById(id);
        FormattedResponseBody<String> body = new FormattedResponseBody<>("");
        return body.formatData();
    }
}
