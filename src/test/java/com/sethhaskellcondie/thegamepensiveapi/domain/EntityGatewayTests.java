package com.sethhaskellcondie.thegamepensiveapi.domain;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public abstract class EntityGatewayTests<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> {

    protected EntityGateway<T, RequestDto, ResponseDto> gateway;
    protected EntityService<T, RequestDto, ResponseDto> service;

    protected abstract void setupGatewayMockService();
    protected abstract T generateEntity();
    protected abstract RequestDto generateRequestFromEntity(T entity);
    protected abstract T generateAnotherEntity();
    protected abstract T generateEmptyEntity();

    @BeforeEach
    public void testPrep() {
        setupGatewayMockService();
    }

    @Test
    public void getWithFilters_TwoFound_ReturnDtoList() {
        T entity1 = generateEntity();
        T entity2 = generateAnotherEntity();
        when(service.getWithFilters("")).thenReturn(List.of(entity1, entity2));
        List<ResponseDto> expected = List.of(entity1.convertToResponseDto(), entity2.convertToResponseDto());

        List<ResponseDto> actual = gateway.getWithFilters("");

        assertEquals(expected, actual);
    }

    @Test
    public void getWithFilters_NoneFound_ReturnEmptyList() {
        List<ResponseDto> expected = List.of();
        when(service.getWithFilters("")).thenReturn(List.of());

        List<ResponseDto> actual = gateway.getWithFilters("");

        assertEquals(expected, actual);
    }

    @Test
    public void getById_EntityFound_ReturnDto() throws ExceptionResourceNotFound {
        T entity = generateEntity();
        int id = entity.getId();
        ResponseDto expected = entity.convertToResponseDto();
        when(service.getById(id)).thenReturn(entity);

        ResponseDto actual = gateway.getById(id);

        assertEquals(expected, actual);
    }

    @Test
    public void createNew_Success_ReturnDto() throws ExceptionFailedDbValidation {
        T entity = generateEntity();
        RequestDto requestDto = generateRequestFromEntity(entity);
        ResponseDto expected = entity.convertToResponseDto();
        when(service.createNew(requestDto)).thenReturn(entity);

        ResponseDto actual = gateway.createNew(requestDto);

        assertEquals(expected, actual);
    }

    @Test
    public void updateExisting_EntityMissing_ThrowExceptionResourceNotFound() throws ExceptionResourceNotFound {
        T entity = generateEntity();
        int id = entity.getId();
        RequestDto requestDto = generateRequestFromEntity(entity);
        when(service.getById(id)).thenReturn(generateEmptyEntity());

        assertThrows(ExceptionResourceNotFound.class, () -> gateway.updateExisting(id, requestDto));
    }

    @Test
    public void updateExisting_EntityFound_ReturnDto() throws ExceptionResourceNotFound, ExceptionInputValidation, ExceptionFailedDbValidation {
        T entity = generateEntity();
        int id = entity.getId();
        RequestDto requestDto = generateRequestFromEntity(entity);
        ResponseDto expected = entity.convertToResponseDto();
        when(service.getById(id)).thenReturn(entity);
        when(service.updateExisting(entity)).thenReturn(entity);

        ResponseDto actual = gateway.updateExisting(id, requestDto);

        assertEquals(expected, actual);
    }

    @Test
    public void deleteById_Passthrough_CallsService() throws ExceptionResourceNotFound {
        T entity = generateEntity();
        int id = entity.getId();

        gateway.deleteById(id);

        verify(service, times(1)).deleteById(1);
    }
}
