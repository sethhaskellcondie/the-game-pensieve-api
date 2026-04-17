package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox.BoardGameBoxGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox.BoardGameBoxRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox.BoardGameBoxResponseDto;
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
@RequestMapping("v1/boardGameBoxes")
public class BoardGameBoxController extends BaseController {
    private final BoardGameBoxGateway gateway;

    public BoardGameBoxController(BoardGameBoxGateway gateway) {
        this.gateway = gateway;
    }

    @ResponseBody
    @GetMapping("/{id}")
    public ApiResponse<BoardGameBoxResponseDto> getById(@PathVariable int id, HttpServletRequest request) throws ExceptionResourceNotFound {
        final BoardGameBoxResponseDto responseDto = gateway.getById(id);
        return buildResponse(responseDto, request);
    }

    @ResponseBody
    @PostMapping("/function/search")
    public ApiResponse<List<BoardGameBoxResponseDto>> getWithFilters(@RequestBody Map<String, List<FilterRequestDto>> requestBody, HttpServletRequest request) {
        final List<BoardGameBoxResponseDto> data = gateway.getWithFilters(requestBody.get("filters"));
        return buildResponse(data, request);
    }

    @ResponseBody
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BoardGameBoxResponseDto> createNew(@RequestBody Map<String, BoardGameBoxRequestDto> requestBody, HttpServletRequest request) throws ExceptionFailedDbValidation {
        final BoardGameBoxResponseDto responseDto = gateway.createNew(requestBody.get(Keychain.BOARD_GAME_BOX_KEY));
        return buildResponse(responseDto, request);
    }

    @ResponseBody
    @PutMapping("/{id}")
    public ApiResponse<BoardGameBoxResponseDto> updateExisting(@PathVariable int id, @RequestBody Map<String, BoardGameBoxRequestDto> requestBody, HttpServletRequest request) {
        final BoardGameBoxResponseDto responseDto = gateway.updateExisting(id, requestBody.get(Keychain.BOARD_GAME_BOX_KEY));
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
