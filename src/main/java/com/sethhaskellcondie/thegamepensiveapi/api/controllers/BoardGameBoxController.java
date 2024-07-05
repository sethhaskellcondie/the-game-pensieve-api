package com.sethhaskellcondie.thegamepensiveapi.api.controllers;

import com.sethhaskellcondie.thegamepensiveapi.api.FormattedResponseBody;
import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgamebox.BoardGameBoxGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgamebox.BoardGameBoxRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgamebox.BoardGameBoxResponseDto;
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
@RequestMapping("v1/boardGameBoxes")
public class BoardGameBoxController {
    private final BoardGameBoxGateway gateway;

    public BoardGameBoxController(BoardGameBoxGateway gateway) {
        this.gateway = gateway;
    }


    @ResponseBody
    @GetMapping("/{id}")
    public Map<String, BoardGameBoxResponseDto> getById(@PathVariable int id) throws ExceptionResourceNotFound {
        final BoardGameBoxResponseDto responseDto = gateway.getById(id);
        final FormattedResponseBody<BoardGameBoxResponseDto> body = new FormattedResponseBody<>(responseDto);
        return body.formatData();
    }

    @ResponseBody
    @PostMapping("/function/search")
    public Map<String, List<BoardGameBoxResponseDto>> getWithFilters(@RequestBody Map<String, List<FilterRequestDto>> requestBody) {
        final List<BoardGameBoxResponseDto> data = gateway.getWithFilters(requestBody.get("filters"));
        final FormattedResponseBody<List<BoardGameBoxResponseDto>> body = new FormattedResponseBody<>(data);
        return body.formatData();
    }

    @ResponseBody
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, BoardGameBoxResponseDto> createNew(@RequestBody Map<String, BoardGameBoxRequestDto> requestBody) throws ExceptionFailedDbValidation {
        final BoardGameBoxResponseDto responseDto = gateway.createNew(requestBody.get(Keychain.BOARD_GAME_BOX_KEY));
        final FormattedResponseBody<BoardGameBoxResponseDto> body = new FormattedResponseBody<>(responseDto);
        return body.formatData();
    }

    @ResponseBody
    @PutMapping("/{id}")
    public Map<String, BoardGameBoxResponseDto> updateExisting(@PathVariable int id, @RequestBody Map<String, BoardGameBoxRequestDto> requestBody) {
        final BoardGameBoxResponseDto responseDto = gateway.updateExisting(id, requestBody.get(Keychain.BOARD_GAME_BOX_KEY));
        final FormattedResponseBody<BoardGameBoxResponseDto> body = new FormattedResponseBody<>(responseDto);
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
