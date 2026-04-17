package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import com.sethhaskellcondie.thegamepensieveapi.domain.metadata.Metadata;
import com.sethhaskellcondie.thegamepensieveapi.domain.metadata.MetadataGateway;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("v1/metadata")
public class MetadataController extends BaseController {
    private final MetadataGateway gateway;
    private final ObjectMapper objectMapper;

    public MetadataController(MetadataGateway gateway) {
        this.gateway = gateway;
        this.objectMapper = new ObjectMapper();
    }

    @ResponseBody
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Metadata> createNewMetadata(@RequestBody Map<String, Metadata> requestBody, HttpServletRequest request) throws ExceptionFailedDbValidation, ExceptionInputValidation {
        final Metadata newMetadata = requestBody.get("metadata");
        validateJsonValue(newMetadata.value());
        final Metadata savedMetadata = gateway.createNew(newMetadata);
        return buildResponse(savedMetadata, request);
    }

    @ResponseBody
    @GetMapping("")
    public ApiResponse<List<Metadata>> getAllMetadata(HttpServletRequest request) {
        final List<Metadata> allMetadata = gateway.getAllMetadata();
        return buildResponse(allMetadata, request);
    }

    @ResponseBody
    @GetMapping("/{key}")
    public ApiResponse<Metadata> getByKey(@PathVariable String key, HttpServletRequest request) throws ExceptionResourceNotFound {
        final Metadata metadata = gateway.getByKey(key);
        return buildResponse(metadata, request);
    }

    @ResponseBody
    @PatchMapping("/{key}")
    public ApiResponse<Metadata> patchValue(@PathVariable String key, @RequestBody Map<String, String> requestBody, HttpServletRequest request)
            throws ExceptionResourceNotFound, ExceptionInputValidation {
        final String newValue = requestBody.get("value");
        validateJsonValue(newValue);
        final Metadata metadataToUpdate = new Metadata(null, key, newValue, null, null, null);
        final Metadata updatedMetadata = gateway.updateValue(metadataToUpdate);
        return buildResponse(updatedMetadata, request);
    }

    @ResponseBody
    @DeleteMapping("/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<String> deleteByKey(@PathVariable String key, HttpServletRequest request) throws ExceptionResourceNotFound {
        gateway.deleteByKey(key);
        return buildResponse("", request);
    }

    private void validateJsonValue(String value) throws ExceptionInputValidation {
        if (value == null || value.trim().isEmpty()) {
            throw new ExceptionInputValidation("Value cannot be null or empty");
        }

        try {
            objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            throw new ExceptionInputValidation("Value must be valid JSON: " + e.getMessage());
        }
    }
}
