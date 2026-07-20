package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import com.sethhaskellcondie.thegamepensieveapi.domain.counts.CollectionCountsDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.counts.CollectionCountsGateway;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owner-scoped collection counts — a cheap way for API consumers (notably the MCP sidecar's
 * {@code get_collection_summary} tool) to learn the size and shape of a collection without transferring
 * every row of every entity.
 */
@RestController
public class CountsController extends BaseController {

    private final CollectionCountsGateway gateway;

    public CountsController(CollectionCountsGateway gateway) {
        this.gateway = gateway;
    }

    @ResponseBody
    @GetMapping("v1/function/counts")
    public ApiResponse<CollectionCountsDto> getCounts(HttpServletRequest request) {
        return buildResponse(gateway.getCounts(), request);
    }
}
