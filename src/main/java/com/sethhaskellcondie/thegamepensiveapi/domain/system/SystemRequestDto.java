package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;

import java.util.List;

/**
 * The request DTO will use the wrapper classes for Primitives to allow nulls to be passed
 * in as input then it will be validated when the object is created then we can pass back
 * all validation errors back at the same time.
 * <p>
 * All entities request and response data transfer objects should include List<CustomFieldValue> customFieldValues because all entities have CustomFieldValues
 */
public record SystemRequestDto(
        String name,
        Integer generation,
        Boolean handheld,
        List<CustomFieldValue> customFieldValues) {

    //used for backing up data
    public static SystemRequestDto convertRequestToResponse(SystemResponseDto system) {
        return new SystemRequestDto(system.name(), system.generation(), system.handheld(), system.customFieldValues());
    }
}
