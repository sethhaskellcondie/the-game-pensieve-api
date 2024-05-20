package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import java.util.List;
import java.util.Map;

import com.sethhaskellcondie.thegamepensiveapi.api.FormattedResponseBody;
import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
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

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;

@RestController
@RequestMapping("toys")
public class ToyController {
    private final ToyGateway gateway;
    //TODO update this to have a v1 in the request mapping

    public ToyController(ToyGateway gateway) {
        this.gateway = gateway;
    }

    @ResponseBody
    @GetMapping("/{id}")
    public Map<String, ToyResponseDto> getOneToy(@PathVariable int id) throws ExceptionResourceNotFound {
        final ToyResponseDto responseDto = gateway.getById(id);
        final FormattedResponseBody<ToyResponseDto> body = new FormattedResponseBody<>(responseDto);
        return body.formatData();
    }

    @ResponseBody
    @PostMapping("/search")
    public Map<String, List<ToyResponseDto>> getAllToys(@RequestBody Map<String, List<Filter>> requestBody) {
        final List<ToyResponseDto> data = gateway.getWithFilters(requestBody.get("filters"));
        final FormattedResponseBody<List<ToyResponseDto>> body = new FormattedResponseBody<>(data);
        return body.formatData();
    }

    @ResponseBody
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, ToyResponseDto> createNewToy(@RequestBody Map<String, ToyRequestDto> requestBody) throws ExceptionFailedDbValidation {
        final ToyResponseDto responseDto = gateway.createNew(requestBody.get(Keychain.TOY_KEY));
        final FormattedResponseBody<ToyResponseDto> body = new FormattedResponseBody<>(responseDto);
        return body.formatData();
    }

    @ResponseBody
    @PutMapping("/{id}")
    public Map<String, ToyResponseDto> updateExistingToy(@PathVariable int id, @RequestBody Map<String, ToyRequestDto> requestBody) throws ExceptionResourceNotFound, ExceptionFailedDbValidation {
        final ToyResponseDto responseDto = gateway.updateExisting(id, requestBody.get(Keychain.TOY_KEY));
        final FormattedResponseBody<ToyResponseDto> body = new FormattedResponseBody<>(responseDto);
        return body.formatData();
    }

    @ResponseBody
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Map<String, String> deleteExistingToy(@PathVariable int id) throws ExceptionResourceNotFound {
        gateway.deleteById(id);
        FormattedResponseBody<String> body = new FormattedResponseBody<>("");
        return body.formatData();
    }
}
