package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterRequestDto;
import jakarta.servlet.http.HttpServletRequest;
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
public class VideoGameBoxController extends BaseController {
    private final VideoGameBoxGateway gateway;

    public VideoGameBoxController(VideoGameBoxGateway gateway) {
        this.gateway = gateway;
    }

    @ResponseBody
    @GetMapping("/{id}")
    public ApiResponse<VideoGameBoxResponseDto> getById(@PathVariable int id, HttpServletRequest request) throws ExceptionResourceNotFound {
        final VideoGameBoxResponseDto responseDto = gateway.getById(id);
        return buildResponse(responseDto, request);
    }

    @ResponseBody
    @PostMapping("/function/search")
    public ApiResponse<List<VideoGameBoxResponseDto>> getWithFilters(@RequestBody Map<String, List<FilterRequestDto>> requestBody, HttpServletRequest request) {
        final List<VideoGameBoxResponseDto> data = gateway.getWithFilters(requestBody.get("filters"));
        return buildResponse(data, request);
    }

    @ResponseBody
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VideoGameBoxResponseDto> createNew(@RequestBody Map<String, VideoGameBoxRequestDto> requestBody, HttpServletRequest request) throws ExceptionFailedDbValidation {
        final VideoGameBoxResponseDto responseDto = gateway.createNew(requestBody.get(Keychain.VIDEO_GAME_BOX_KEY));
        return buildResponse(responseDto, request);
    }

    @ResponseBody
    @PutMapping("/{id}")
    public ApiResponse<VideoGameBoxResponseDto> updateExisting(@PathVariable int id, @RequestBody Map<String, VideoGameBoxRequestDto> requestBody, HttpServletRequest request) {
        final VideoGameBoxResponseDto responseDto = gateway.updateExisting(id, requestBody.get(Keychain.VIDEO_GAME_BOX_KEY));
        return buildResponse(responseDto, request);
    }

    @ResponseBody
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<String> deleteExisting(@PathVariable int id, HttpServletRequest request) throws ExceptionResourceNotFound {
        gateway.deleteById(id);
        return buildResponse("", request);
    }
}
