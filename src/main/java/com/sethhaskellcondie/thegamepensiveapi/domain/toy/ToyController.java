package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import java.util.List;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInputValidation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;

@RestController
@RequestMapping("toys")
public class ToyController {
    private final ToyGateway gateway;

    public ToyController(ToyGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping("")
    public List<ToyResponseDto> getAllToys() {
        return gateway.getWithFilters("");
    }

    @GetMapping("/{id}")
    public ToyResponseDto getOneToy(@PathVariable int id) throws ExceptionResourceNotFound {
        return gateway.getById(id);
    }

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public ToyResponseDto createNewToy(@RequestBody ToyRequestDto toy) throws ExceptionFailedDbValidation {
        return gateway.createNew(toy);
    }

    @PutMapping("/{id}")
    public ToyResponseDto updateExistingToy(@PathVariable int id, @RequestBody ToyRequestDto toy) throws ExceptionInputValidation, ExceptionResourceNotFound, ExceptionFailedDbValidation {
        return gateway.updateExisting(id, toy);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExistingToy(@PathVariable int id) throws ExceptionResourceNotFound {
        gateway.deleteById(id);
    }
}
