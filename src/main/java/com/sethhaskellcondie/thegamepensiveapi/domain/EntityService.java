package com.sethhaskellcondie.thegamepensiveapi.domain;

import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;

import java.util.List;

/**
 * A Service is the bridge between the entity behavior, gateways, and repositories.
 * <p>
 * Gateways will always connect to repositories through services, the service will hold a reference
 * to the entity repository along with any other related repositories needed for that entity.
 * For basic CRUD functions the service is a pass-through so everything is automated with generics.
 * <p>
 * Services hold any behaviors and business logic that happen between entities that are related, that
 * are not included in the entity object itself. Like creating a DTO from two different Entities.
 */
public interface EntityService<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> {
    List<T> getWithFilters(List<FilterRequestDto> filters);

    T getById(int id) throws ExceptionResourceNotFound;

    T createNew(RequestDto requestDto) throws ExceptionFailedDbValidation;

    T updateExisting(T t) throws ExceptionFailedDbValidation;

    void deleteById(int id) throws ExceptionResourceNotFound;
}
