package com.sethhaskellcondie.thegamepensiveapi.domain.entity;

import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionResourceNotFound;

import java.util.List;

public abstract class EntityGatewayAbstract<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> implements EntityGateway<T, RequestDto, ResponseDto> {
    protected final EntityService<T, RequestDto, ResponseDto> service;

    public EntityGatewayAbstract(EntityService<T, RequestDto, ResponseDto> service) {
        this.service = service;
    }

    @Override
    public List<ResponseDto> getWithFilters(List<FilterRequestDto> filters) {
        List<T> t = service.getWithFilters(filters);
        return t.stream().map(Entity::convertToResponseDto).toList();
    }

    @Override
    public ResponseDto getById(int id) {
        return service.getById(id).convertToResponseDto();
    }

    @Override
    public ResponseDto createNew(RequestDto requestDto) {
        return service.createNew(requestDto).convertToResponseDto();
    }

    @Override
    public ResponseDto updateExisting(int id, RequestDto requestDto) {
        T t = service.getById(id);
        if (!t.isPersisted()) {
            throw new ExceptionResourceNotFound(t.getKey(), id);
        }
        t.updateFromRequestDto(requestDto);
        return service.updateExisting(t).convertToResponseDto();
    }

    @Override
    public void deleteById(int id) {
        service.deleteById(id);
    }
}
