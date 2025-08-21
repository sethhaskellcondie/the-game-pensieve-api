package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.FormattedResponseBody;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import com.sethhaskellcondie.thegamepensieveapi.domain.metadata.Metadata;
import com.sethhaskellcondie.thegamepensieveapi.domain.metadata.MetadataGateway;
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
public class MetadataController {
    private final MetadataGateway gateway;

    public MetadataController(MetadataGateway gateway) {
        this.gateway = gateway;
    }

    @ResponseBody
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Metadata> createNewMetadata(@RequestBody Map<String, Metadata> requestBody) throws ExceptionFailedDbValidation {
        final Metadata newMetadata = requestBody.get("metadata");
        final Metadata savedMetadata = gateway.createNew(newMetadata);
        final FormattedResponseBody<Metadata> body = new FormattedResponseBody<>(savedMetadata);
        return body.formatData();
    }

    @ResponseBody
    @GetMapping("")
    public Map<String, List<Metadata>> getAllMetadata() {
        final List<Metadata> allMetadata = gateway.getAllMetadata();
        final FormattedResponseBody<List<Metadata>> body = new FormattedResponseBody<>(allMetadata);
        return body.formatData();
    }

    @ResponseBody
    @GetMapping("/{key}")
    public Map<String, Metadata> getByKey(@PathVariable String key) throws ExceptionResourceNotFound {
        final Metadata metadata = gateway.getByKey(key);
        final FormattedResponseBody<Metadata> body = new FormattedResponseBody<>(metadata);
        return body.formatData();
    }

    @ResponseBody
    @PatchMapping("/{key}")
    public Map<String, Metadata> patchValue(@PathVariable String key, @RequestBody Map<String, String> requestBody) throws ExceptionResourceNotFound {
        final String newValue = requestBody.get("value");
        final Metadata metadataToUpdate = new Metadata(null, key, newValue, null, null, null);
        final Metadata updatedMetadata = gateway.updateValue(metadataToUpdate);
        final FormattedResponseBody<Metadata> body = new FormattedResponseBody<>(updatedMetadata);
        return body.formatData();
    }

    @ResponseBody
    @DeleteMapping("/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Map<String, String> deleteByKey(@PathVariable String key) throws ExceptionResourceNotFound {
        gateway.deleteByKey(key);
        FormattedResponseBody<String> body = new FormattedResponseBody<>("");
        return body.formatData();
    }
}