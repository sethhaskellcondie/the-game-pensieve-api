package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.FormattedResponseBody;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
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
@RequestMapping("v1/custom_fields")
public class CustomFieldController {
    private final CustomFieldGateway gateway;

    public CustomFieldController(CustomFieldGateway gateway) {
        this.gateway = gateway;
    }

    @ResponseBody
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, CustomField> createNewCustomField(@RequestBody Map<String, CustomFieldRequestDto> requestBody) throws ExceptionFailedDbValidation {
        final CustomFieldRequestDto newCustomField = requestBody.get("custom_field");
        final CustomField savedCustomField = gateway.createNew(newCustomField);
        final FormattedResponseBody<CustomField> body = new FormattedResponseBody<>(savedCustomField);
        return body.formatData();
    }

    @ResponseBody
    @GetMapping("")
    public Map<String, List<CustomField>> getAllCustomFields() {
        final List<CustomField> allCustomFields = gateway.getAllCustomFields();
        final FormattedResponseBody<List<CustomField>> body = new FormattedResponseBody<>(allCustomFields);
        return body.formatData();
    }

    @ResponseBody
    @GetMapping("/entity/{key}")
    public Map<String, List<CustomField>> getAllCustomFieldsByEntityKey(@PathVariable String key) throws ExceptionResourceNotFound {
        final List<CustomField> customFields = gateway.getAllByEntityKey(key);
        final FormattedResponseBody<List<CustomField>> body = new FormattedResponseBody<>(customFields);
        return body.formatData();
    }

    @ResponseBody
    @PatchMapping("/{id}")
    public Map<String, CustomField> patchName(@PathVariable int id, @RequestBody Map<String, String> requestBody) throws ExceptionResourceNotFound {
        final String newName = requestBody.get("name");
        final CustomField updatedCustomField = gateway.updateName(id, newName);
        final FormattedResponseBody<CustomField> body = new FormattedResponseBody<>(updatedCustomField);
        return body.formatData();
    }

    @ResponseBody
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Map<String, String> deleteExistingCustomField(@PathVariable int id) throws ExceptionResourceNotFound {
        gateway.deleteById(id);
        FormattedResponseBody<String> body = new FormattedResponseBody<>("");
        return body.formatData();
    }
}
