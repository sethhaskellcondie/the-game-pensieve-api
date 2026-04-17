package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("v1/filters")
public class FilterController extends BaseController {
    private final FilterGateway gateway;

    public FilterController(FilterGateway gateway) {
        this.gateway = gateway;
    }

    @ResponseBody
    @GetMapping("/{key}")
    public ApiResponse<FilterResponseDto> getFiltersByKey(@PathVariable String key, HttpServletRequest request) {
        final FilterResponseDto responseDto = gateway.getFiltersByKey(key);
        return buildResponse(responseDto, request);
    }
}
