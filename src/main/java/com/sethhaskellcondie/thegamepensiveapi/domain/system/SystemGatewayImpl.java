package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import java.util.List;

import org.springframework.stereotype.Component;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInputValidation;

@Component
public class SystemGatewayImpl implements SystemGateway {
	private final SystemService service;

	public SystemGatewayImpl(SystemService service) {
		this.service = service;
	}

	@Override
	public List<System.SystemResponseDto> getWithFilters(String filters) {
		List<System> systems = service.getWithFilters("");
		return systems.stream().map(System::convertToDto).toList();
	}

	@Override
	public System.SystemResponseDto getById(int id) {
		return service.getById(id).convertToDto();
	}

	@Override
	public System.SystemResponseDto createNewSystem(System.SystemRequestDto system) throws ExceptionFailedDbValidation {
		System inputSystem = new System();
		inputSystem.update(system);
		return service.createNewSystem(inputSystem).convertToDto();
	}

	@Override
	public System.SystemResponseDto updateExistingSystem(int id, System.SystemRequestDto system) throws ExceptionFailedDbValidation, ExceptionInputValidation {
		System validSystem = service.getById(id);
		if (!validSystem.isPersistent()) {
			throw new ExceptionInputValidation("Invalid System Id");
		}
		validSystem.update(system);
		return service.updateExistingSystem(validSystem).convertToDto();
	}

	@Override
	public void deleteById(int id) throws ExceptionFailedDbValidation, ExceptionInputValidation {
		System validSystem = service.getById(id);
		if (!validSystem.isPersistent()) {
			throw new ExceptionInputValidation("Invalid System Id");
		}
		service.deleteById(id);
	}
}
