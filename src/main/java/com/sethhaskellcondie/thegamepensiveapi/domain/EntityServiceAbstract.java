package com.sethhaskellcondie.thegamepensiveapi.domain;

import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;

import java.util.List;

public abstract class EntityServiceAbstract<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> implements EntityService<T, RequestDto, ResponseDto> {

    private final EntityRepository<T, RequestDto, ResponseDto> repository;

    public EntityServiceAbstract(EntityRepository<T, RequestDto, ResponseDto> repository) {
        this.repository = repository;
    }

    @Override
    public List<T> getWithFilters(List<Filter> filters) {
        return repository.getWithFilters(filters);
    }

    @Override
    public T getById(int id) throws ExceptionResourceNotFound {
        return repository.getById(id);
    }

    @Override
    public T createNew(RequestDto requestDto) throws ExceptionFailedDbValidation {
        return repository.insert(requestDto);
    }

    @Override
    public T updateExisting(T t) throws ExceptionFailedDbValidation {
        return repository.update(t);
    }

    @Override
    public void deleteById(int id) throws ExceptionResourceNotFound {
        repository.deleteById(id);
    }
}
