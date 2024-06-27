package com.sethhaskellcondie.thegamepensiveapi.domain.entity;

import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterRequestDto;

import java.util.List;

/**
 * The gateway is the entrypoint into the domain, external consumers will use the controllers while
 * internal consumers must use a gateway there is no direct access to the Entities and the business logic
 * in the domain. All Entities have the same basic CRUD functionality. the default implementation is found
 * in these abstract Entity classes (EntityGateway, EntityService, and EntityRepository) overwritten as needed.
 * <p>
 * A gateway has two responsibilities: interfacing with services, and converting the response from the service
 * into a responseDto to keep the Entity access encapsulated.
 * <p>
 * Each entity is designed so that the CRUD functions only need to be implemented in the Entity and the Repository,
 * then exposed through the controller, the rest works automagically.
 */
public interface EntityGateway<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> {

    List<ResponseDto> getWithFilters(List<FilterRequestDto> filters);

    ResponseDto getById(int id);

    ResponseDto createNew(RequestDto requestDto);

    ResponseDto updateExisting(int id, RequestDto requestDto);

    void deleteById(int id);
}
