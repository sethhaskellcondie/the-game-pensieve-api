package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;

@Repository
public interface SystemRepository {
	/**
	 * These methods should be on any repository where the object extends Entity
	 * I could make an EntityRepository interface and force interfaces like this to inherit from it
	 * But then the return types would have to be an Entity, and they would need to be cast each time
	 * Not worth it, right now...
	 */
	System insert(System system) throws ExceptionFailedDbValidation;
	List<System> getWithFilters(String filters);
	System getById(int id);
	System update(System system) throws ExceptionFailedDbValidation;
	void deleteById(int id) throws ExceptionFailedDbValidation;
}
