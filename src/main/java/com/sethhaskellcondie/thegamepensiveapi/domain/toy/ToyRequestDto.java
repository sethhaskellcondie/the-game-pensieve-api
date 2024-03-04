package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

record ToyRequestDto(String name, String set) {
    ToyRequestDto {
        if (name.isBlank()) {
            throw new IllegalArgumentException("ERROR! - Illegal Argument: Name is required for a Toy");
        }
    }
}
