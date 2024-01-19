package com.sethhaskellcondie.thegamepensiveapi.domain;

import java.util.List;

import org.springframework.stereotype.Component;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;

@Component
public class EntityGatewayImpl<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> implements EntityGateway<T, RequestDto, ResponseDto> {
	private final EntityService<T, RequestDto, ResponseDto> service;

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
	public ResponseDto createNew(RequestDto requestDto) throws ExceptionFailedDbValidation {
		T t = service.hydrateFromRequestDto(requestDto);
		return service.createNew(t).convertToResponseDto();
	}

	@Override
	public ResponseDto updateExisting(int id, RequestDto requestDto) throws ExceptionFailedDbValidation, ExceptionInputValidation, ExceptionResourceNotFound {
		T t = service.getById(id);
		if (!t.isPersistent()) {
			throw new ExceptionInputValidation("Invalid System Id");
		}
		t.updateFromRequest(requestDto);
		return service.updateExisting(t).convertToResponseDto();
	}

	@Override
	public void deleteById(int id) throws ExceptionFailedDbValidation, ExceptionInputValidation, ExceptionResourceNotFound {
		T t = service.getById(id);
		if (!t.isPersistent()) {
			throw new ExceptionInputValidation("Invalid System Id");
		}
		service.deleteById(id);
	}
}
