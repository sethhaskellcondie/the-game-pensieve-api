package com.sethhaskellcondie.thegamepensieveapi.domain.entity;

import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterRequestDto;

import java.util.List;

/**
 * A Service holds all the business logic for the different entities in the system.
 * <p>
 * Gateways will always connect to services, the service will hold a reference
 * to the entity repository along with any other related repositories/services needed for that entity.
 * For basic CRUD functions the service is a pass-through so everything is automated with generics.
 */
public interface EntityService<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> {
    List<T> getWithFilters(List<FilterRequestDto> filters);

    T getById(int id);

    T createNew(RequestDto requestDto);

    T updateExisting(int id, RequestDto requestDto);

    void deleteById(int id);
}
