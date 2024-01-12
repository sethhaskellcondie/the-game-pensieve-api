package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import java.util.List;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInputValidation;

/**
 * A Gateway is how other parts of the system access the domain every Entity will have base functionality
 * inside the domain and if some of that base functionality should be unavailable it will not be exposed
 * with and endpoint on the controller.
 *
 * A gateway primarily creates Entities and interfaces with services, but a gateway will never return an
 * Entity it will always return a DTO for that Entity, that DTO will contain relationships for other
 * Entities that can be retrieved through a gateway (internally) or an endpoint (externally)
 *
 * If I had some permissions checks I would put them here.
 */
public interface SystemGateway {

	List<System.SystemResponseDto> getWithFilters(String filters);
	System.SystemResponseDto getById(int id);
	System.SystemResponseDto createNewSystem(System.SystemRequestDto system) throws ExceptionFailedDbValidation;
	System.SystemResponseDto updateExistingSystem(int id, System.SystemRequestDto system) throws ExceptionFailedDbValidation, ExceptionInputValidation;
	void deleteById(int id) throws ExceptionFailedDbValidation, ExceptionInputValidation;
}
