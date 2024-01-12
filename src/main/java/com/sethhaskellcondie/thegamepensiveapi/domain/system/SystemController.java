package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInputValidation;

/**
 * A Controller is in charge of exposing the domain functionality through the different endpoints
 * Every entity will have the same base functionality but if you can't patch an entity then
 * the endpoint for patching will not be created even through the update function will be included
 * lower in the domain.
 *
 * Controllers only interact with Gateways, but also reference the shape of DTO's that are defined
 * in the entity. Gateways will always take an Entity.InputDto and will return either an 
 * Entity.ResponseDto or an error, the controller will format the response, then return
 *
 * Controllers are also responsible for formatting the response, this is mostly transforming
 * exceptions into errors to be returned.
 */
@RestController
@RequestMapping("systems")
public class SystemController {
	private final SystemGateway gateway;

	public SystemController(SystemGateway gateway)
	{
		this.gateway = gateway;
	}

	@GetMapping("")
	public List<System.SystemResponseDto> getAllSystems() {
		return gateway.getWithFilters("");
	}

	@GetMapping("/{id}")
	public System.SystemResponseDto getOneSystem(@PathVariable int id) {
		return gateway.getById(id);
	}

	@PostMapping("")
	public System.SystemResponseDto createNewSystem(@RequestBody System.SystemRequestDto system) throws ExceptionFailedDbValidation {
		return gateway.createNewSystem(system);
	}

	@PutMapping("/{id}")
	public System.SystemResponseDto updateExistingSystem(@PathVariable int id, @RequestBody System.SystemRequestDto system) throws ExceptionInputValidation, ExceptionFailedDbValidation {
		return gateway.updateExistingSystem(id, system);
	}

	@DeleteMapping("/{id}")
	public void deleteExistingSystem(@PathVariable int id) throws ExceptionInputValidation, ExceptionFailedDbValidation {
		gateway.deleteById(id);
	}
}
