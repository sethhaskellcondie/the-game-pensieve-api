package com.sethhaskellcondie.thegamepensiveapi.domain;

import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;

import java.util.List;

public abstract class EntityGatewayAbstract<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> implements EntityGateway<T, RequestDto, ResponseDto> {
    private final EntityService<T, RequestDto, ResponseDto> service;

    public EntityGatewayAbstract(EntityService<T, RequestDto, ResponseDto> service) {
        this.service = service;
    }

    @Override
    public List<ResponseDto> getWithFilters(List<Filter> filters) {
        List<T> t = service.getWithFilters(filters);
        return t.stream().map(Entity::convertToResponseDto).toList();
    }

    @Override
    public ResponseDto getById(int id) throws ExceptionResourceNotFound {
        return service.getById(id).convertToResponseDto();
    }

    @Override
    public ResponseDto createNew(RequestDto requestDto) throws ExceptionFailedDbValidation {
        return service.createNew(requestDto).convertToResponseDto();
    }

    @Override
    public ResponseDto updateExisting(int id, RequestDto requestDto) throws ExceptionFailedDbValidation, ExceptionResourceNotFound {
        T t = service.getById(id);
        if (!t.isPersisted()) {
            throw new ExceptionResourceNotFound(this.getClass().getSimpleName(), id);
        }
        t.updateFromRequestDto(requestDto);
        return service.updateExisting(t).convertToResponseDto();
    }

    @Override
    public void deleteById(int id) throws ExceptionResourceNotFound {
        service.deleteById(id);
    }
}
