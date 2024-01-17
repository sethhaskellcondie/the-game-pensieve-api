package com.sethhaskellcondie.thegamepensiveapi.domain;

import java.util.List;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;

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
 *
 * I haven't been able to create a suitable EntityRepositoryImpl that can be extended to give
 * the needed functionality for free to each new Entity, but I bet it can be done...somehow...
 * until then the comments and documentation for that can be found in the SystemRepositoryImpl
 */
public interface EntityRepository<T extends Entity> {
	T insert(T t) throws ExceptionFailedDbValidation;
	List<T> getWithFilters(String filters);
	T getById(int id) throws ExceptionResourceNotFound;
	T update(T t) throws ExceptionFailedDbValidation;
	void deleteById(int id) throws ExceptionResourceNotFound;
}
