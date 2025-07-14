package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.FormattedResponseBody;
import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterRequestDto;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("v1/videoGameBoxes")
public class VideoGameBoxController {
    private final VideoGameBoxGateway gateway;

    public VideoGameBoxController(VideoGameBoxGateway gateway) {
        this.gateway = gateway;
    }

    @ResponseBody
    @GetMapping("/{id}")
    public Map<String, VideoGameBoxResponseDto> getById(@PathVariable int id) throws ExceptionResourceNotFound {
        final VideoGameBoxResponseDto responseDto = gateway.getById(id);
        final FormattedResponseBody<VideoGameBoxResponseDto> body = new FormattedResponseBody<>(responseDto);
        return body.formatData();
    }

    @ResponseBody
    @PostMapping("/function/search")
    public Map<String, List<VideoGameBoxResponseDto>> getWithFilters(@RequestBody Map<String, List<FilterRequestDto>> requestBody) {
        final List<VideoGameBoxResponseDto> data = gateway.getWithFilters(requestBody.get("filters"));
        final FormattedResponseBody<List<VideoGameBoxResponseDto>> body = new FormattedResponseBody<>(data);
        return body.formatData();
    }

    @ResponseBody
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, VideoGameBoxResponseDto> createNew(@RequestBody Map<String, VideoGameBoxRequestDto> requestBody) throws ExceptionFailedDbValidation {
        final VideoGameBoxResponseDto responseDto = gateway.createNew(requestBody.get(Keychain.VIDEO_GAME_BOX_KEY));
        final FormattedResponseBody<VideoGameBoxResponseDto> body = new FormattedResponseBody<>(responseDto);
        return body.formatData();
    }

    @ResponseBody
    @PutMapping("/{id}")
    public Map<String, VideoGameBoxResponseDto> updateExisting(@PathVariable int id, @RequestBody Map<String, VideoGameBoxRequestDto> requestBody) {
        final VideoGameBoxResponseDto responseDto = gateway.updateExisting(id, requestBody.get(Keychain.VIDEO_GAME_BOX_KEY));
        final FormattedResponseBody<VideoGameBoxResponseDto> body = new FormattedResponseBody<>(responseDto);
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
