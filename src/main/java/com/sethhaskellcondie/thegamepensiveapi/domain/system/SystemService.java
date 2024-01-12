package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import java.util.List;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;

/**
 * A Service is the bridge between the entity behavior, gateways, and repositories.
 *
 * Gateways should always connect to repositories through services, the service will hold a reference
 * to the entity repository along with any other related repositories needed for that entity.
 *
 * Services all hold any behaviors that happen between entities that are related, that
 * cannot be included in the entity object itself. Like creating a DTO from two different Entities.
 */
public interface SystemService {
	/**
	 * These methods should be on any service where the object extends Entity
	 * I could make an EntityService interface and force interfaces like this to inherit from it
	 * But then the return types would have to be an Entity, and they would need to be cast each time
	 * Not worth it, right now...
	 */
	List<System> getWithFilters(String filters);
	System getById(int id);
	System createNewSystem(System system) throws ExceptionFailedDbValidation;
	System updateExistingSystem(System system) throws ExceptionFailedDbValidation;
	void deleteById(int id) throws ExceptionFailedDbValidation;
}
