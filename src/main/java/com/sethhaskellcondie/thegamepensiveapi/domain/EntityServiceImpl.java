package com.sethhaskellcondie.thegamepensiveapi.domain;

import java.util.List;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;

public class EntityServiceImpl<T extends Entity> implements EntityService<T> {

	private final EntityRepository<T> repository;

	public EntityServiceImpl(EntityRepository<T> repository) {
		this.repository = repository;
	}
	public List<T> getWithFilters(String filters) {
		return repository.getWithFilters(filters);
	}

	public T getById(int id) throws ExceptionResourceNotFound {
		return repository.getById(id);
	}

	public T createNew(T t) throws ExceptionFailedDbValidation {
		return repository.insert(t);
	}

	public T updateExisting(T t) throws ExceptionFailedDbValidation {
		return repository.update(t);
	}

	public void deleteById(int id) throws ExceptionResourceNotFound {
		repository.deleteById(id);
	}
}
