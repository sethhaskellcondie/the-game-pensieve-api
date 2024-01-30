package com.sethhaskellcondie.thegamepensiveapi.domain;

import java.util.List;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;

/**
 * A Service is the bridge between the entity behavior, gateways, and repositories.
 * <p>
 * Gateways should always connect to repositories through services, the service will hold a reference
 * to the entity repository along with any other related repositories needed for that entity.
 * <p>
 * Services all hold any behaviors that happen between entities that are related, that
 * cannot be included in the entity object itself. Like creating a DTO from two different Entities.
 */
public interface EntityService<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> {
	List<T> getWithFilters(String filters);
	T getById(int id) throws ExceptionResourceNotFound;
	T createNew(T t) throws ExceptionFailedDbValidation;
	T updateExisting(T t) throws ExceptionFailedDbValidation;
	void deleteById(int id) throws ExceptionResourceNotFound;
}
