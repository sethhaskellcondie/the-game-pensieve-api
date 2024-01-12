package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import java.util.List;

import org.springframework.stereotype.Service;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;

@Service
public class SystemServiceImpl implements SystemService {
	private final SystemRepository repository;

	public SystemServiceImpl(SystemRepository repository) {
		this.repository = repository;
	}

	public List<System> getWithFilters(String filters) {
		return repository.getWithFilters(filters);
	}

	public System getById(int id) {
		return repository.getById(id);
	}

	public System createNewSystem(System system) throws ExceptionFailedDbValidation {
		return repository.insert(system);
	}

	public System updateExistingSystem(System system) throws ExceptionFailedDbValidation {
		return repository.update(system);
	}

	public void deleteById(int id) throws ExceptionFailedDbValidation {
		repository.deleteById(id);
	}
}
