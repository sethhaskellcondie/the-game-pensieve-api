package com.sethhaskellcondie.thegamepensiveapi.domain;

import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * A Repository is responsible for instantiating new objects and encapsulating all database communications,
 * all the SQL and JDBC code needed to communicate with the database is here.
 * <p>
 * The database itself will generate ID's for objects as they are inserted into the different tables.
 * The only way for an object to have an ID is to hydrate that object from the database with a repository.
 * <p>
 * Repositories are in charge of running any final validation on an object before it is written
 * or updated in a database table, this is usually encapsulated in a private dbValidation method.
 */
@Repository
public interface EntityRepository<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> {
    T insert(RequestDto requestDto) throws ExceptionFailedDbValidation;

    T insert(T t) throws ExceptionFailedDbValidation;

    List<T> getWithFilters(List<Filter> filters);

    T getById(int id) throws ExceptionResourceNotFound;

    T update(T t) throws ExceptionFailedDbValidation;

    void deleteById(int id) throws ExceptionResourceNotFound;

    //TODO T getDeletedById(int Id) this is the only way to get deleted objects [don't forget to update the tests]
}
