package com.sethhaskellcondie.thegamepensiveapi.domain.entity;

import com.sethhaskellcondie.thegamepensiveapi.domain.filter.FilterRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionResourceNotFound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityFactory.Generate.ANOTHER_STARTS_WITH_VALID_PERSISTED;
import static com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityFactory.Generate.EMPTY;
import static com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityFactory.Generate.STARTS_WITH_VALID_PERSISTED;
import static com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityFactory.Generate.VALID_PERSISTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Deprecated
public abstract class EntityGatewayTests<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> {

    protected EntityGateway<T, RequestDto, ResponseDto> gateway;
    protected EntityService<T, RequestDto, ResponseDto> service;
    protected EntityFactory<T, RequestDto, ResponseDto> factory;

    protected abstract void setupGatewayMockService();
    protected abstract void setupFactory();
    protected abstract FilterRequestDto startsWithFilter();

    @BeforeEach
    public void testPrep() {
        setupGatewayMockService();
        setupFactory();
    }

    @Test
    public void getWithFilters_TwoFound_ReturnDtoList() {
        final T entity1 = factory.generateEntity(STARTS_WITH_VALID_PERSISTED);
        final T entity2 = factory.generateEntity(ANOTHER_STARTS_WITH_VALID_PERSISTED);
        final List<FilterRequestDto> filters = List.of(startsWithFilter());

        when(service.getWithFilters(filters)).thenReturn(List.of(entity1, entity2));
        final List<ResponseDto> expected = List.of(entity1.convertToResponseDto(), entity2.convertToResponseDto());

        final List<ResponseDto> actual = gateway.getWithFilters(filters);

        assertEquals(expected, actual);
    }

    @Test
    public void getWithFilters_NoneFound_ReturnEmptyList() {
        final List<ResponseDto> expected = List.of();
        when(service.getWithFilters(List.of(startsWithFilter()))).thenReturn(List.of());

        final List<ResponseDto> actual = gateway.getWithFilters(List.of(startsWithFilter()));

        assertEquals(expected, actual);
    }

    @Test
    public void getById_EntityFound_ReturnDto() throws ExceptionResourceNotFound {
        final T entity = factory.generateEntity(VALID_PERSISTED);
        final int id = entity.getId();
        final ResponseDto expected = entity.convertToResponseDto();
        when(service.getById(id)).thenReturn(entity);

        final ResponseDto actual = gateway.getById(id);

        assertEquals(expected, actual);
    }

    @Test
    public void createNew_Success_ReturnDto() throws ExceptionFailedDbValidation {
        final T entity = factory.generateEntity(VALID_PERSISTED);
        final RequestDto requestDto = factory.generateRequestDtoFromEntity(entity);
        final ResponseDto expected = entity.convertToResponseDto();
        when(service.createNew(requestDto)).thenReturn(entity);

        final ResponseDto actual = gateway.createNew(requestDto);

        assertEquals(expected, actual);
    }

    @Test
    public void updateExisting_EntityMissing_ThrowExceptionResourceNotFound() throws ExceptionResourceNotFound {
        final T entity = factory.generateEntity(VALID_PERSISTED);;
        final int id = entity.getId();
        final RequestDto requestDto = factory.generateRequestDtoFromEntity(entity);
        when(service.getById(id)).thenReturn(factory.generateEntity(EMPTY));

        assertThrows(ExceptionResourceNotFound.class, () -> gateway.updateExisting(id, requestDto));
    }

    @Test
    public void updateExisting_EntityFound_ReturnDto() throws ExceptionResourceNotFound, ExceptionInputValidation, ExceptionFailedDbValidation {
        final T entity = factory.generateEntity(VALID_PERSISTED);;
        final int id = entity.getId();
        final RequestDto requestDto = factory.generateRequestDtoFromEntity(entity);
        final ResponseDto expected = entity.convertToResponseDto();
        when(service.getById(id)).thenReturn(entity);
        when(service.updateExisting(entity)).thenReturn(entity);

        final ResponseDto actual = gateway.updateExisting(id, requestDto);

        assertEquals(expected, actual);
    }

    @Test
    public void deleteById_Passthrough_CallsService() throws ExceptionResourceNotFound {
        final T entity = factory.generateEntity(VALID_PERSISTED);
        final int id = entity.getId();

        gateway.deleteById(id);

        verify(service, times(1)).deleteById(1);
    }
}
