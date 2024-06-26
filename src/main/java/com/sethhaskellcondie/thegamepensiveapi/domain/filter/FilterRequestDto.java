package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

//this record is public and in its own class so that it can be used throughout the system and easily used in tests
public record FilterRequestDto(String key, String field, String operator, String operand) { }
