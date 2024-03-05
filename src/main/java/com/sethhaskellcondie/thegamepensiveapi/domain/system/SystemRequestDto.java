package com.sethhaskellcondie.thegamepensiveapi.domain.system;

record SystemRequestDto(String name, Integer generation, Boolean handheld) {
    public SystemRequestDto {
        if (name.isBlank()) {
            throw new IllegalArgumentException("ERROR! - Illegal Argument: Name is required for a System");
        }
        if (null == generation) {
            throw new IllegalArgumentException("ERROR! - Illegal Argument: Generation is required for a System");
        }
        if (null == handheld) {
            throw new IllegalArgumentException("ERROR! - Illegal Argument: Handheld is required for a System");
        }
    }
}
