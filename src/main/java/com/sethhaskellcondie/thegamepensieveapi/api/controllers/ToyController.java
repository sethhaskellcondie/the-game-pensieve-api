package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyResponseDto;
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
@RequestMapping("v1/toys")
public class ToyController extends BaseController {
    private final ToyGateway gateway;

    public ToyController(ToyGateway gateway) {
        this.gateway = gateway;
    }

    @ResponseBody
    @GetMapping("/{id}")
    public ApiResponse<ToyResponseDto> getById(@PathVariable int id, HttpServletRequest request) throws ExceptionResourceNotFound {
        final ToyResponseDto responseDto = gateway.getById(id);
        return buildResponse(responseDto, request);
    }

    @ResponseBody
    @PostMapping("/function/search")
    public ApiResponse<List<ToyResponseDto>> getWithFilters(@RequestBody Map<String, List<FilterRequestDto>> requestBody, HttpServletRequest request) {
        final List<ToyResponseDto> data = gateway.getWithFilters(requestBody.get("filters"));
        return buildResponse(data, request);
    }

    @ResponseBody
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ToyResponseDto> createNew(@RequestBody Map<String, ToyRequestDto> requestBody, HttpServletRequest request) throws ExceptionFailedDbValidation {
        final ToyResponseDto responseDto = gateway.createNew(requestBody.get(Keychain.TOY_KEY));
        return buildResponse(responseDto, request);
    }

    @ResponseBody
    @PutMapping("/{id}")
    public ApiResponse<ToyResponseDto> updateExisting(@PathVariable int id, @RequestBody Map<String, ToyRequestDto> requestBody, HttpServletRequest request)
            throws ExceptionResourceNotFound, ExceptionFailedDbValidation {
        final ToyResponseDto responseDto = gateway.updateExisting(id, requestBody.get(Keychain.TOY_KEY));
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
