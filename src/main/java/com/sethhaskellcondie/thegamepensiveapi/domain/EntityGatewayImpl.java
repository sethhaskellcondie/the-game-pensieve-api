package com.sethhaskellcondie.thegamepensiveapi.domain;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;

import java.util.List;

public abstract class EntityGatewayImpl<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> implements EntityGateway<T, RequestDto, ResponseDto> {
    protected final EntityService<T, RequestDto, ResponseDto> service;

    public EntityGatewayImpl(EntityService<T, RequestDto, ResponseDto> service) {
        this.service = service;
    }

    @Override
    public List<ResponseDto> getWithFilters(String filters) {
        List<T> t = service.getWithFilters("");
        return t.stream().map(Entity::convertToResponseDto).toList();
    }

    @Override
    public ResponseDto getById(int id) throws ExceptionResourceNotFound {
        return service.getById(id).convertToResponseDto();
    }

    @Override
    public abstract ResponseDto createNew(RequestDto requestDto) throws ExceptionFailedDbValidation;

    @Override
    public ResponseDto updateExisting(int id, RequestDto requestDto) throws ExceptionFailedDbValidation, ExceptionResourceNotFound {
        T t = service.getById(id);
        if (!t.isPersistent()) {
            throw new ExceptionResourceNotFound("System", id);
        }
        t.updateFromRequest(requestDto);
        return service.updateExisting(t).convertToResponseDto();
    }

    @Override
    public void deleteById(int id) throws ExceptionResourceNotFound {
        T t = service.getById(id);
        if (!t.isPersistent()) {
            throw new ExceptionResourceNotFound("System", id);
        }
        service.deleteById(id);
    }
}
