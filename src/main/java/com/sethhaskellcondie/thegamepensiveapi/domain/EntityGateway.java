package com.sethhaskellcondie.thegamepensiveapi.domain;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;

import java.util.List;

/**
 * A Gateway is how other parts of the system access the domain every Entity will have base functionality
 * inside the domain and if some of that base functionality should be unavailable it will not be exposed
 * with and endpoint on the controller.
 * <p>
 * A gateway primarily creates Entities and interfaces with services, but a gateway will never return an
 * Entity it will always return a DTO for that Entity, that DTO will contain relationships for other
 * Entities that can be retrieved through a gateway (internally) or an endpoint (externally)
 * <p>
 * If I had some permissions checks I would put them here.
 */
public interface EntityGateway<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> {

    List<ResponseDto> getWithFilters(String filters);

    ResponseDto getById(int id) throws ExceptionResourceNotFound;

    ResponseDto createNew(RequestDto requestDto) throws ExceptionFailedDbValidation;

    ResponseDto updateExisting(int id, RequestDto requestDto) throws ExceptionFailedDbValidation, ExceptionResourceNotFound;

    void deleteById(int id) throws ExceptionResourceNotFound;
}
