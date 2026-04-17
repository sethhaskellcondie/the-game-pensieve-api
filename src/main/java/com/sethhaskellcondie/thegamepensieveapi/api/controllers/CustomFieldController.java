package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
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
@RequestMapping("v1/custom_fields")
public class CustomFieldController extends BaseController {
    private final CustomFieldGateway gateway;

    public CustomFieldController(CustomFieldGateway gateway) {
        this.gateway = gateway;
    }

    @ResponseBody
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CustomField> createNewCustomField(@RequestBody Map<String, CustomFieldRequestDto> requestBody, HttpServletRequest request) throws ExceptionFailedDbValidation {
        final CustomFieldRequestDto newCustomField = requestBody.get("custom_field");
        final CustomField savedCustomField = gateway.createNew(newCustomField);
        return buildResponse(savedCustomField, request);
    }

    @ResponseBody
    @GetMapping("")
    public ApiResponse<List<CustomField>> getAllCustomFields(HttpServletRequest request) {
        final List<CustomField> allCustomFields = gateway.getAllCustomFields();
        return buildResponse(allCustomFields, request);
    }

    @ResponseBody
    @GetMapping("/entity/{key}")
    public ApiResponse<List<CustomField>> getAllCustomFieldsByEntityKey(@PathVariable String key, HttpServletRequest request) throws ExceptionResourceNotFound {
        final List<CustomField> customFields = gateway.getAllByEntityKey(key);
        return buildResponse(customFields, request);
    }

    @ResponseBody
    @PatchMapping("/{id}")
    public ApiResponse<CustomField> patchName(@PathVariable int id, @RequestBody Map<String, String> requestBody, HttpServletRequest request) throws ExceptionResourceNotFound {
        final String newName = requestBody.get("name");
        final CustomField updatedCustomField = gateway.updateName(id, newName);
        return buildResponse(updatedCustomField, request);
    }

    @ResponseBody
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<String> deleteExistingCustomField(@PathVariable int id, HttpServletRequest request) throws ExceptionResourceNotFound {
        gateway.deleteById(id);
        return buildResponse("", request);
    }
}
