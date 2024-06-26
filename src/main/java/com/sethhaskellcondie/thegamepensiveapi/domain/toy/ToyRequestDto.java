package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;

import java.util.List;

public record ToyRequestDto(
        String name,
        String set,
        List<CustomFieldValue> customFieldValues) {

    //Used for backing up data
    public static ToyRequestDto convertResponseToRequest(ToyResponseDto toy) {
        return new ToyRequestDto(toy.name(), toy.set(), toy.customFieldValues());
    }
}
