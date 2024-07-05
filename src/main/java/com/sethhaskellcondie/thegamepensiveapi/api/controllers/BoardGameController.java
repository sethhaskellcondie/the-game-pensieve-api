package com.sethhaskellcondie.thegamepensiveapi.api.controllers;

import com.sethhaskellcondie.thegamepensiveapi.api.FormattedResponseBody;
import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgame.BoardGameGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgame.BoardGameRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgame.BoardGameResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionResourceNotFound;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterRequestDto;
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
@RequestMapping("v1/boardGames")
public class BoardGameController {
    private final BoardGameGateway gateway;

    public BoardGameController(BoardGameGateway gateway) {
        this.gateway = gateway;
    }

    @ResponseBody
    @GetMapping("/{id}")
    public Map<String, BoardGameResponseDto> getById(@PathVariable int id) throws ExceptionResourceNotFound {
        final BoardGameResponseDto responseDto = gateway.getById(id);
        final FormattedResponseBody<BoardGameResponseDto> body = new FormattedResponseBody<>(responseDto);
        return body.formatData();
    }

    @ResponseBody
    @PostMapping("/function/search")
    public Map<String, List<BoardGameResponseDto>> getWithFilters(@RequestBody Map<String, List<FilterRequestDto>> requestBody) {
        final List<BoardGameResponseDto> data = gateway.getWithFilters(requestBody.get("filters"));
        final FormattedResponseBody<List<BoardGameResponseDto>> body = new FormattedResponseBody<>(data);
        return body.formatData();
    }

    @ResponseBody
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, BoardGameResponseDto> createNew(@RequestBody Map<String, BoardGameRequestDto> requestBody) throws ExceptionFailedDbValidation {
        final BoardGameResponseDto responseDto = gateway.createNew(requestBody.get(Keychain.VIDEO_GAME_KEY));
        final FormattedResponseBody<BoardGameResponseDto> body = new FormattedResponseBody<>(responseDto);
        return body.formatData();
    }

    @ResponseBody
    @PutMapping("/{id}")
    public Map<String, BoardGameResponseDto> updateExisting(@PathVariable int id, @RequestBody Map<String, BoardGameRequestDto> requestBody) {
        final BoardGameResponseDto responseDto = gateway.updateExisting(id, requestBody.get(Keychain.BOARD_GAME_KEY));
        final FormattedResponseBody<BoardGameResponseDto> body = new FormattedResponseBody<>(responseDto);
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
