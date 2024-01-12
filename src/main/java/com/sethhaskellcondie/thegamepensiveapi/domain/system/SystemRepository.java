package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import java.util.List;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
/**
 * A Repository is in charge of all database connections, all the SQL needed to communicate
 * with the database is encapsulated here.
 *
 * Repositories will always return hydrated objects, the database itself will generate ID's
 * for the objects, the only way to get an object with an ID is to retrieve it from the database
 * and have a repository hydrate it.
 *
 * Repositories are in charge of running any final validation on an object before it is written
 * or updated in a database table.
 */
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
