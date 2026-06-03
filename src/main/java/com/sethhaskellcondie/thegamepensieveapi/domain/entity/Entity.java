package com.sethhaskellcondie.thegamepensieveapi.domain.entity;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;

import java.util.List;

/**
 * Interface extracted from the Entity abstract class. Concrete entities that use composition
 * instead of extending Entity implement this interface directly.
 * The Entity abstract class also implements this interface so all inheritance-based entities
 * satisfy the same generic bounds.
 */
public interface Entity<RequestDto, ResponseDto> {
    Integer getId();

    String getKey();

    List<CustomFieldValue> getCustomFieldValues();

    void setCustomFieldValues(List<CustomFieldValue> customFieldValues);

    boolean isPersisted();

    boolean isDeleted();

    Entity<RequestDto, ResponseDto> updateFromRequestDto(RequestDto requestDto);

    ResponseDto convertToResponseDto();

    RequestDto convertToRequestDto();
}
