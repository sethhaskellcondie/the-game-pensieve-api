package com.sethhaskellcondie.thegamepensieveapi.domain.entity;

import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterService;

import java.util.List;

public abstract class EntityServiceAbstract<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> implements EntityService<T, RequestDto, ResponseDto> {

    protected final EntityRepository<T, RequestDto, ResponseDto> repository;
    private final FilterService filterService;

    public EntityServiceAbstract(EntityRepository<T, RequestDto, ResponseDto> repository, FilterService filterService) {
        this.repository = repository;
        this.filterService = filterService;
    }

    @Override
    public List<T> getWithFilters(List<FilterRequestDto> dtoFilters) {
        List<Filter> filters = filterService.convertFilterRequestDtosToFilters(dtoFilters);
        return repository.getWithFilters(filters);
    }

    @Override
    public T getById(int id) {
        return repository.getById(id);
    }

    /**
     *  createNew will need to be implemented in each service it will look something like this.
     *  This cannot be implemented here because 'new t()' cannot be called on a generic.
     *  public T createNew(RequestDto requestDto) {
     *      T t = new t().updatedFromRequestDto(requestDto);
     *      return repository.insert(t);
     *  }
     */

    @Override
    public T updateExisting(int id, RequestDto requestDto) {
        T t = repository.getById(id);
        if (!t.isPersisted()) {
            throw new ExceptionResourceNotFound(t.getKey(), id);
        }
        t.updateFromRequestDto(requestDto);
        return repository.update(t);
    }

    @Override
    public void deleteById(int id) {
        repository.deleteById(id);
    }
}
