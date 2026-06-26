package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.AuthResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.AuthService;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.LoginRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.RefreshRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.RegisterRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.RegisterResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints. Unlike the catalog controllers these take a flat request body (there is no
 * entity-key wrapper) and are permitted without a token under the secured profile so callers can obtain one.
 */
@RestController
@RequestMapping("v1/auth")
public class AuthController extends BaseController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @ResponseBody
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterResponseDto> register(@RequestBody RegisterRequestDto requestDto, HttpServletRequest request) throws ExceptionFailedDbValidation {
        final RegisterResponseDto responseDto = authService.register(requestDto);
        return buildResponse(responseDto, request);
    }

    @ResponseBody
    @PostMapping("/login")
    public ApiResponse<AuthResponseDto> login(@RequestBody LoginRequestDto requestDto, HttpServletRequest request) {
        final AuthResponseDto responseDto = authService.login(requestDto);
        return buildResponse(responseDto, request);
    }

    @ResponseBody
    @PostMapping("/refresh")
    public ApiResponse<AuthResponseDto> refresh(@RequestBody RefreshRequestDto requestDto, HttpServletRequest request) {
        final AuthResponseDto responseDto = authService.refresh(requestDto);
        return buildResponse(responseDto, request);
    }
}
