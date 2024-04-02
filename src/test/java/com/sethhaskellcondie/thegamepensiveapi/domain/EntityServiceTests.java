package com.sethhaskellcondie.thegamepensiveapi.domain;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.sethhaskellcondie.thegamepensiveapi.domain.EntityFactory.Generate.VALID;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public abstract class EntityServiceTests<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> {

    protected EntityService<T, RequestDto, ResponseDto> service;
    protected EntityRepository<T, RequestDto, ResponseDto> repository;
    protected EntityFactory<T, RequestDto, ResponseDto> factory;

    protected abstract void setupServiceMockRepository();
    protected abstract void setupFactory();

    @BeforeEach
    public void testPrep() {
        setupServiceMockRepository();
        setupFactory();
    }

    @Test
    public void getWithFilters_Passthrough_CallRepository() {
        String filters = "";

        service.getWithFilters(filters);

        verify(repository, times(1)).getWithFilters(filters);
    }

    @Test
    public void getById_Passthrough_CallRepository() throws ExceptionResourceNotFound {
        int id = 1;

        service.getById(id);

        verify(repository, times(1)).getById(1);
    }

    @Test
    public void createNew_Passthrough_CallRepository() throws ExceptionFailedDbValidation {
        RequestDto requestDto = factory.generateRequestDto(VALID);

        service.createNew(requestDto);

        verify(repository, times(1)).insert(requestDto);
    }

    @Test
    public void updateExisting_Passthrough_CallRepository() throws ExceptionFailedDbValidation {
        T entity = factory.generateEntity(VALID);

        service.updateExisting(entity);

        verify(repository, times(1)).update(entity);
    }

    @Test
    public void deleteById_Passthrough_CallRepository() throws ExceptionResourceNotFound {
        int id = 1;

        service.deleteById(id);

        verify(repository, times(1)).deleteById(id);
    }
}
