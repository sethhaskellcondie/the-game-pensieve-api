package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGameGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGameRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGameResponseDto;
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
@RequestMapping("v1/boardGames")
public class BoardGameController extends BaseController {
    private final BoardGameGateway gateway;

    public BoardGameController(BoardGameGateway gateway) {
        this.gateway = gateway;
    }

    @ResponseBody
    @GetMapping("/{id}")
    public ApiResponse<BoardGameResponseDto> getById(@PathVariable int id, HttpServletRequest request) throws ExceptionResourceNotFound {
        final BoardGameResponseDto responseDto = gateway.getById(id);
        return buildResponse(responseDto, request);
    }

    @ResponseBody
    @PostMapping("/function/search")
    public ApiResponse<List<BoardGameResponseDto>> getWithFilters(@RequestBody Map<String, List<FilterRequestDto>> requestBody, HttpServletRequest request) {
        final List<BoardGameResponseDto> data = gateway.getWithFilters(requestBody.get("filters"));
        return buildResponse(data, request);
    }

    @ResponseBody
    @PutMapping("/{id}")
    public ApiResponse<BoardGameResponseDto> updateExisting(@PathVariable int id, @RequestBody Map<String, BoardGameRequestDto> requestBody, HttpServletRequest request) {
        final BoardGameResponseDto responseDto = gateway.updateExisting(id, requestBody.get(Keychain.BOARD_GAME_KEY));
        return buildResponse(responseDto, request);
    }
}
