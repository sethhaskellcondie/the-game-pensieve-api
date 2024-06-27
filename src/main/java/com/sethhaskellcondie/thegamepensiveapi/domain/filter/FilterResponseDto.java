package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import java.util.List;
import java.util.Map;

public record FilterResponseDto(String type, Map<String, String> fields, Map<String, List<String>> filters) {
}
