package com.sethhaskellcondie.thegamepensiveapi.domain.entity;

import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterService;

import java.util.List;

public abstract class EntityServiceAbstract<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> implements EntityService<T, RequestDto, ResponseDto> {

    private final EntityRepository<T, RequestDto, ResponseDto> repository;
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

    @Override
    public T createNew(RequestDto requestDto) {
        return repository.insert(requestDto);
    }

    @Override
    public T updateExisting(T t) {
        return repository.update(t);
    }

    @Override
    public void deleteById(int id) {
        repository.deleteById(id);
    }
}
